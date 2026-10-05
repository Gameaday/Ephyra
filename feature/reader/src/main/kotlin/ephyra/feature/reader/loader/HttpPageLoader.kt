package ephyra.feature.reader.loader

import ephyra.core.common.util.lang.launchIO
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.network.ImageUrlPolicy
import ephyra.core.common.util.network.LayeredFailure
import ephyra.core.common.util.network.PageLoadRecovery
import ephyra.core.common.util.network.PageLoadRecoveryAction
import ephyra.core.common.util.network.PageLoadRecoveryDecision
import ephyra.core.common.util.network.ReResolvePacer
import ephyra.core.common.util.network.TransientErrors
import ephyra.core.common.util.network.withContext
import ephyra.core.common.util.system.DeviceUtil
import ephyra.core.common.util.system.logcat
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.chapter.model.toSChapter
import ephyra.domain.chapter.service.ChapterCache
import ephyra.feature.reader.model.ReaderChapter
import ephyra.feature.reader.model.ReaderPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.PageListDiagnostics
import eu.kanade.tachiyomi.source.online.needsFreshPageList
import eu.kanade.tachiyomi.source.online.resolvePageImage
import eu.kanade.tachiyomi.source.online.resolvesOwnPageImages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import java.io.IOException
import java.util.concurrent.PriorityBlockingQueue
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.math.min

/**
 * Fresh page addresses for one chapter load, fetched at most once.
 *
 * **Why a refetch and not `getImageUrl`.** When the recovery ladder drops an indicted URL the page is
 * left with no address, and the next attempt asks the source for a replacement. For a source that
 * populates `Page.imageUrl` in `getPageList` — which is every 1.6 extension, because upstream removed
 * the per-page chain from the extension API — **there is no per-page call that returns one**. Making
 * one runs an inherited default that throws, from a method the source does not implement.
 *
 * This is the reported MangaDex failure, in full: its at-home tokens expire after five minutes, so a
 * long read drops them. Clearing the field was correct — a dead token should not be reused — but there
 * was no way back from it, and a chapter that had listed perfectly could not be finished.
 *
 * Memoised because pages load concurrently: one expired token should cost a page-list fetch for the
 * chapter, not one per page.
 */
private class FreshPageAddresses(
    private val source: HttpSource,
    private val chapter: Chapter,
) {
    private val mutex = Mutex()
    private var pages: List<Page>? = null

    suspend fun at(index: Int): String? {
        val list = mutex.withLock {
            pages ?: source.getPageList(chapter.toSChapter()).also { pages = it }
        }
        return list.getOrNull(index)?.imageUrl
    }
}

/**
 * Loader used to load chapters from an online source.
 */
internal class HttpPageLoader(
    private val chapter: ReaderChapter,
    private val source: HttpSource,
    private val chapterCache: ChapterCache,
    /**
     * Device performance tier used to scale preload window sizes and worker concurrency.
     * Defaults to [DeviceUtil.PerformanceTier.MEDIUM] so that the loader is safe to instantiate
     * in tests or other contexts where a [Context] is unavailable. Production callers (i.e.
     * [ephyra.app.ui.reader.loader.ChapterLoader]) always supply the real tier.
     */
    performanceTier: DeviceUtil.PerformanceTier = DeviceUtil.PerformanceTier.MEDIUM,
    /**
     * When `true` the loader is being used to preload a chapter that is not yet the active
     * reading chapter. In this mode only a single background worker is spawned, regardless of
     * the device performance tier, so the preload never competes with the active chapter's
     * downloads for network bandwidth. The worker count is raised to the full tier value the
     * moment the chapter becomes active (i.e. a new [HttpPageLoader] is created for it via the
     * active-chapter path in [ChapterLoader]).
     */
    isPreloadOnly: Boolean = false,
    private val preProcessor: ReaderPagePreProcessor? = null,
) : PageLoader() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * A queue used to manage requests one by one while allowing priorities.
     */
    private val queue = PriorityBlockingQueue<PriorityPage>()

    /**
     * Number of pages ahead of the current page to preload — scaled by device capability.
     */
    private val preloadSize = when (performanceTier) {
        DeviceUtil.PerformanceTier.LOW -> 2
        DeviceUtil.PerformanceTier.MEDIUM -> 4
        DeviceUtil.PerformanceTier.HIGH -> 6
    }

    /**
     * Number of pages behind the current page to preload — scaled by device capability.
     */
    private val preloadBackwardSize = when (performanceTier) {
        DeviceUtil.PerformanceTier.LOW -> 1
        DeviceUtil.PerformanceTier.MEDIUM -> 2
        DeviceUtil.PerformanceTier.HIGH -> 3
    }

    /**
     * Number of concurrent page-download workers.
     *
     * For preload-only loaders a single worker is always used so that background chapter
     * prefetch never steals bandwidth from the active chapter. The full worker count (scaled
     * by device tier) is only used once the chapter becomes the active reading chapter.
     */
    private val fullWorkerCount = when (performanceTier) {
        DeviceUtil.PerformanceTier.LOW -> 1
        DeviceUtil.PerformanceTier.MEDIUM -> 2
        DeviceUtil.PerformanceTier.HIGH -> 3
    }

    /**
     * Whether the page list loaded in [getPages] had any pages with an unresolved image URL
     * (i.e. [Page.imageUrl] was null or empty). This is one of the two inputs to the decision at
     * [recycle] time:
     *
     * - `true`  → the cache entry was incomplete when loaded (fresh network fetch, or a
     *             previous session that ended before all URLs were resolved). The [recycle]
     *             save is needed to persist newly-resolved image URLs so the next open can
     *             skip the [HttpSource.getImageUrl] calls.
     * - `false` → every page already had a resolved image URL when loaded from cache. Nothing
     *             changed during *loading* that the cache doesn't already reflect, so the
     *             [recycle] disk write is skipped to avoid redundant I/O.
     *
     * Describes the list **as it was loaded**, and only that: a URL dropped mid-session because it
     * was the thing that failed is invisible here, which is why [recycle] re-derives the condition
     * from the pages as they now stand rather than trusting this flag alone.
     *
     * Starts as `true` so that a conservative save is always attempted if [getPages] never
     * runs (e.g. the loader is recycled before it is used).
     */
    @Volatile
    private var cacheHadMissingImageUrls = true

    /**
     * Describes the page list this loader holds, for the rejection report.
     *
     * An instance field rather than a static because the reader prefetches neighbouring chapters: a
     * shared counter describes whichever chapter was fetched last, not the one that failed. Two lines
     * of one device report contradicted each other while both were true of different objects, because
     * they came from different chapters.
     */
    private var pageListOrigin: String = "<no page list loaded yet>"

    /**
     * Spaces re-resolutions across this chapter's pages.
     *
     * Per loader rather than per process, because one chapter failing together is the observed
     * shape; correlating across chapters would need state with a lifetime nobody owns. The work
     * count is already bounded by the worker pool, so what this prevents is those few workers
     * asking the source the same question at the same instant.
     */
    private val reResolvePacer = ReResolvePacer()

    /** Guards [promoteToActive] so the promotion is applied at most once. */
    @OptIn(ExperimentalAtomicApi::class)
    private val promoted = AtomicBoolean(!isPreloadOnly)

    init {
        val initialWorkers = if (isPreloadOnly) 1 else fullWorkerCount
        repeat(initialWorkers) { launchWorker() }
    }

    /**
     * Promotes this loader from preload-only (1 worker) to the full [fullWorkerCount] for the
     * active reading chapter. Idempotent — subsequent calls after the first are no-ops.
     *
     * The formula `fullWorkerCount - 1` is correct because preload-only loaders always start
     * with exactly 1 worker ([init] uses `initialWorkers = 1` when [isPreloadOnly] is true),
     * and [promoted] starts as `!isPreloadOnly`, so this branch is only reached when
     * [isPreloadOnly] was true and exactly 1 worker is already running.
     */
    @OptIn(ExperimentalAtomicApi::class)
    override fun promoteToActive() {
        if (isRecycled) return
        if (!promoted.compareAndSet(expectedValue = false, newValue = true)) return
        // Preload-only loaders always start with 1 worker; launch the remaining workers up to
        // the tier-scaled maximum.
        val remaining = fullWorkerCount - 1
        repeat(remaining) { launchWorker() }
    }

    private fun launchWorker() {
        scope.launchIO {
            flow {
                while (true) {
                    emit(runInterruptible { queue.take() })
                }
            }
                .filter { it.page.status == Page.State.Queue }
                .collect { internalLoadPage(it.page, it.priority) }
        }
    }

    override var isLocal: Boolean = false

    /**
     * Fetches the page list from the source and persists it.
     *
     * Extracted from the cache-miss arm of [getPages] because there are now two ways to reach it —
     * no cached entry, or a cached entry that failed the URL contract — and they must behave
     * identically. In particular both must persist, so a rejected list is *overwritten* rather than
     * left on disk to be rejected again on the next open.
     */
    private suspend fun fetchAndPersist(domainChapter: Chapter): List<Page> {
        val networkPages = source.getPageList(chapter.chapter.toSChapter())
        // What the source actually returned, counted rather than asserted. Every fixture in
        // `HttpPageLoaderUrlResolutionTest` builds `Page` with an image URL in `getPageList`, so
        // passing tests only proved the app agrees with a model — never that a real 1.6 source
        // populates the field the way the fixtures assume it does.
        //
        // Recorded on `PageListDiagnostics` rather than only logged, because the reported failure is
        // a contradiction between two facts about this call — `getPageList` is declared, yet the pages
        // carry no address — and the resolver cannot see this one. Logging it left the question
        // answerable only by someone reading logcat; putting it on the diagnostic means the *next
        // error message* carries the answer with it.
        val withAddress = networkPages.count { !it.imageUrl.isNullOrEmpty() }
        PageListDiagnostics.record(networkPages.size, withAddress)
        logcat(LogPriority.INFO) {
            "getPageList returned ${networkPages.size} page(s) for '${domainChapter.name}', " +
                "$withAddress with an image address, source=${source.javaClass.name}, " +
                "declaresGetPageList=${source.capabilities.declaringClassOf("getPageList")}"
        }
        // Persist immediately so a crash before recycle() doesn't lose the page list.
        scope.launchIO {
            try {
                chapterCache.putPageListToCache(domainChapter, networkPages)
            } catch (ex: Throwable) {
                if (ex is CancellationException) throw ex
                logcat(LogPriority.WARN, ex) { "Failed to persist page list to cache after network fetch" }
            }
        }
        // cacheHadMissingImageUrls stays true (network pages have no imageUrls yet)
        return networkPages
    }

    /**
     * Returns the page list for a chapter. It tries to return the page list from the local cache,
     * otherwise fallbacks to network.
     *
     * When the page list is fetched from the network, it is immediately persisted to
     * [ChapterCache] in the background. This means a process death or force-close before the
     * normal [recycle] call does not lose the page URLs — the next chapter open will be served
     * from cache rather than making another [source.getPageList] network call.
     */
    override suspend fun getPages(): List<ReaderPage> {
        check(!isRecycled)
        val domainChapter = chapter.chapter
        var isCacheHit = false

        val pages = try {
            val cachedPages = chapterCache.getPageListFromCache(domainChapter)
            // A cache hit is only a hit if the list it holds still satisfies the URL contract. The
            // reported MangaDex failure lived here: a list written by a bad pass was served verbatim
            // on every subsequent open, the source was never asked, and no fix downstream of the
            // read could take effect. See `cachedPagesAreUsable`.
            if (cachedPagesAreUsable(cachedPages, source.baseUrl)) {
                // All image URLs are already resolved: the recycle() save can be skipped.
                isCacheHit = true
                cacheHadMissingImageUrls = cachedPages.any { it.imageUrl.isNullOrEmpty() }
                cachedPages
            } else {
                logcat(LogPriority.WARN) {
                    "Discarding a cached page list for '${domainChapter.name}' that fails the URL " +
                        "contract; refetching from the source"
                }
                fetchAndPersist(domainChapter)
            }
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            fetchAndPersist(domainChapter)
        }
        // Which list this loader ended up holding. Reported by the loader rather than read from a
        // static on `PageListDiagnostics`, because the reader prefetches neighbouring chapters and a
        // static describes whichever list was fetched most recently — not the chapter whose page had
        // just failed. Two lines of one report then contradicted each other while both were true of
        // different objects.
        pageListOrigin = buildString {
            append("from ").append(if (isCacheHit) "cache" else "source fetch")
            append(", ").append(pages.size).append(" page(s) held, ")
            append(pages.count { !it.imageUrl.isNullOrEmpty() }).append(" with an address")
        }
        return pages.mapIndexed { index, page ->
            // Don't trust sources and use our own indexing
            ReaderPage(index, page.url, page.imageUrl)
        }
    }

    /**
     * Loads a page through the queue. Handles re-enqueueing pages if they were evicted from the cache.
     */
    // One per chapter load, shared by every page: pages load concurrently, and an expired at-home
    // token should cost a page-list fetch for the chapter rather than one per page.
    private val freshAddresses = FreshPageAddresses(source, chapter.chapter)

    override suspend fun loadPage(page: ReaderPage) = withIOContext {
        check(!isRecycled)
        val imageUrl = page.imageUrl

        // A page whose address the ladder has indicted gets a replacement from a fresh page list,
        // before anything else looks at it. This is the only place a 1.6 source keeps addresses:
        // `getImageUrl` would be a call the source does not implement.
        //
        // **The fetch is network I/O and must not be fatal to the reader.** It is made here, on
        // the viewer's own coroutine, so an exception that escapes — a connection reset by the
        // source's API being the reported one — killed that coroutine and with it the reader
        // activity, presenting as a dialog and a kick back to the series screen rather than a
        // failed page. A page that cannot get a replacement stays flagged and falls back to the
        // ladder's own resolution inside `internalLoadPage`, whose recovery ladder is the one
        // place that already knows how to fail a *page* rather than a *reader*.
        if (page.needsFreshAddress) {
            runCatching { freshAddresses.at(page.index) }
                .onSuccess { replacement ->
                    page.imageUrl = replacement
                    page.needsFreshAddress = false
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    logcat(LogPriority.WARN, e) {
                        "Could not obtain a replacement address for page ${page.number} of " +
                            "${chapter.chapter.name}; leaving it to the recovery ladder"
                    }
                }
        }

        // Check if the image has been deleted
        if (page.status == Page.State.Ready && imageUrl != null && !chapterCache.isImageInCache(imageUrl)) {
            prepareForReload(page)
        }

        // Automatically retry failed pages when subscribed to this page. The status is read into a
        // local first because `prepareForReload` overwrites it, and a failure that indicts the URL
        // takes the URL with it, so this retry asks the source rather than repeating the request
        // that just failed.
        val failedWith = (page.status as? Page.State.Error)?.error
        if (failedWith != null) {
            prepareForReload(page, dropImageUrl = TransientErrors.shouldReResolveUrl(failedWith))
        }

        val queuedPages = mutableListOf<PriorityPage>()
        if (page.status == Page.State.Queue) {
            queuedPages += PriorityPage(page, 1).also { queue.offer(it) }
        }
        queuedPages += preloadNextPages(page, preloadSize)
        queuedPages += preloadPrevPages(page, preloadBackwardSize)

        suspendCancellableCoroutine<Nothing> { continuation ->
            continuation.invokeOnCancellation {
                queuedPages.forEach {
                    if (it.page.status == Page.State.Queue) {
                        queue.remove(it)
                    }
                }
            }
        }
    }

    /**
     * Retries a page. This method is only called from user interaction on the viewer.
     *
     * The page's own error decides whether the resolved URL survives the retry, so the button the
     * user presses is a genuinely new request whenever the URL was what failed. That is the
     * difference between a Retry that can work and one that re-sends a request known to fail: an
     * unresolvable image host is not reachable by asking for the same host again, and the source
     * will hand back a different one.
     */
    override fun retryPage(page: ReaderPage) {
        check(!isRecycled)
        val failedWith = (page.status as? Page.State.Error)?.error
        prepareForReload(page, dropImageUrl = failedWith != null && TransientErrors.shouldReResolveUrl(failedWith))
        queue.offer(PriorityPage(page, 2))
    }

    override fun recycle() {
        super.recycle()
        // Cancel all in-flight download coroutines. Any page whose download is interrupted here
        // will have its disk-cache editor aborted (see ChapterCache.fetchAndCacheImage), so no
        // partial data is committed. The page status may be left in a transient state
        // (LoadPage/DownloadImage); we reset it below so external observers never see a ghost
        // "downloading" indicator for a cancelled operation.
        scope.cancel()
        queue.clear()

        // Reset pages stuck in transient states so that:
        //  • any viewer holding a reference to the old ReaderPage sees a retryable state, and
        //  • if this chapter is re-entered later, new ReaderPage objects start with Queue status
        //    (they are always created fresh in getPages(), but defensive reset costs nothing).
        val pages = chapter.pages
        if (pages != null) {
            var cancelledCount = 0
            for (page in pages) {
                val status = page.status
                if (status == Page.State.LoadPage || status == Page.State.DownloadImage) {
                    page.status = Page.State.Queue
                    cancelledCount++
                }
            }
            if (cancelledCount > 0) {
                logcat(LogPriority.DEBUG) {
                    "Recycled ${chapter.chapter.name}: cancelled $cancelledCount in-flight download(s)"
                }
            }

            // Release stream lambdas so the captured imageUrl strings and file references can be GC'd
            pages.forEach { it.stream = null }

            // Cache current page list progress for online chapters to allow a faster reopen.
            // [needsPageListSave] decides whether that write is redundant; it is not simply the
            // [cacheHadMissingImageUrls] flag, because that flag describes the list as it was
            // *loaded* and cannot see a URL dropped mid-session because it was the thing that
            // failed.
            if (needsPageListSave(cacheHadMissingImageUrls, pages.map { it.imageUrl })) {
                val pagesToSave = pages.map { Page(it.index, it.url, it.imageUrl) }
                persistenceScope.launch {
                    try {
                        chapterCache.putPageListToCache(
                            chapter.chapter,
                            pagesToSave,
                        )
                    } catch (e: Throwable) {
                        if (e is CancellationException) {
                            throw e
                        }
                        logcat(LogPriority.WARN, e) {
                            "Failed to persist page list on recycle for ${chapter.chapter.name}"
                        }
                    } finally {
                        persistenceScope.cancel()
                    }
                }
            } else {
                persistenceScope.cancel()
            }
        }
    }

    /**
     * Preloads the given [amount] of pages after the [currentPage] with a lower priority.
     *
     * @return a list of [PriorityPage] that were added to the [queue]
     */
    private fun preloadNextPages(currentPage: ReaderPage, amount: Int): List<PriorityPage> {
        val pageIndex = currentPage.index
        val pages = currentPage.chapter.pages ?: return emptyList()
        if (pageIndex == pages.lastIndex) return emptyList()

        return pages
            .subList(pageIndex + 1, min(pageIndex + 1 + amount, pages.size))
            .mapNotNull {
                if (it.status == Page.State.Queue) {
                    PriorityPage(it, 0).apply { queue.offer(this) }
                } else {
                    null
                }
            }
    }

    /**
     * Preloads the given [amount] of pages before the [currentPage] with a lower priority.
     * This avoids stutter when the user navigates backward through a chapter.
     *
     * @param currentPage the page the user is currently viewing.
     * @param amount the number of pages before [currentPage] to preload.
     * @return a list of [PriorityPage] that were added to the [queue]
     */
    private fun preloadPrevPages(currentPage: ReaderPage, amount: Int): List<PriorityPage> {
        val pageIndex = currentPage.index
        if (pageIndex == 0) return emptyList()
        val pages = currentPage.chapter.pages ?: return emptyList()

        return pages
            .subList(maxOf(0, pageIndex - amount), pageIndex)
            .mapNotNull {
                if (it.status == Page.State.Queue) {
                    PriorityPage(it, 0).apply { queue.offer(this) }
                } else {
                    null
                }
            }
    }

    /**
     * Proactively starts loading the first [amount] pages of this chapter at background priority.
     * Called after the page list has been fetched so that images begin downloading before the user
     * actually scrolls to this chapter, reducing wait time at chapter boundaries.
     */
    override fun preloadFirstPages(amount: Int) {
        if (isRecycled) return
        val pages = chapter.pages?.take(amount) ?: return
        pages.forEach { page ->
            if (page.status == Page.State.Queue) {
                queue.offer(PriorityPage(page, 0))
            }
        }
    }

    /**
     * Queues every page in this chapter at the lowest background priority so that the
     * smart-combine pre-scan can process the entire chapter without waiting for the user to
     * navigate to each page. Pages are enqueued at priority [BACKGROUND_PRELOAD_PRIORITY]
     * (below the nearby-page preload priority of 0), so they never compete for bandwidth with
     * the page the user is actively reading or about to read.
     *
     * Only pages in [Page.State.Queue] are enqueued. Pages already downloading, ready, or in
     * an error state are intentionally skipped: pages in progress or already cached need no
     * action, and errored pages are retried through the user-facing [retryPage] path rather
     * than being silently re-queued here.
     */
    override fun preloadAllPages() {
        if (isRecycled) return
        val pages = chapter.pages ?: return
        pages.forEach { page ->
            if (page.status == Page.State.Queue) {
                queue.offer(PriorityPage(page, BACKGROUND_PRELOAD_PRIORITY))
            }
        }
    }

    /**
     * Resets [page] so the next worker attempt re-fetches it.
     *
     * [dropImageUrl] decides whether the resolved URL survives the reset, and it is the one place
     * that decision is made for a user-triggered reload:
     *
     * - `false` (the default) keeps the URL. Correct for a cache eviction, where the bytes are
     *   missing but the URL was never at fault, and re-resolving would silently change which URL a
     *   page that was mid-render points at.
     * - `true` drops it, so the next attempt asks the source. Required when the page failed in a
     *   way that indicts the URL — a revoked signed URL, or a host that does not resolve. Keeping
     *   it makes the user's Retry a verbatim repeat of the request that just failed, which for an
     *   unresolvable image host is a permanent failure the user cannot get out of (`DEF-023`).
     *
     * Callers pass [TransientErrors.shouldReResolveUrl] of the page's error rather than deciding
     * for themselves, so the drop [internalLoadPage] makes when a load fails and the drop a
     * user-triggered reload makes cannot disagree about what a given failure means.
     */
    private fun prepareForReload(
        page: ReaderPage,
        dropImageUrl: Boolean = false,
    ) {
        page.clearLoadedImage()
        page.stream = null
        if (dropImageUrl) {
            // Flagged rather than cleared: the address is replaced from a fresh page list when the
            // page is next loaded, which is the only place a 1.6 source keeps them. Clearing it
            // instead leaves the page asking a method the source does not implement.
            page.needsFreshAddress = true
        }
        page.status = Page.State.Queue
    }

    /**
     * Loads the page, retrieving the image URL and downloading the image if necessary.
     * Failed loads are retried on a jittered backoff, up to
     * [PageLoadRecovery.DEFAULT_MAX_RETRIES] times, before the page is marked failed. Downloaded
     * images are stored in the chapter cache.
     *
     * **Why a retry does not always keep the URL.** Whether the next attempt re-requests the same
     * URL or asks the source for a new one is [PageLoadRecovery]'s decision, from
     * [TransientErrors.shouldReResolveUrl] — not a local attempt counter. A counter cannot tell the
     * two apart: it re-resolves after a `429` (where the same URL is correct and re-resolving costs
     * an extra source round-trip) and it re-resolves after a `403` or a name that did not resolve
     * only from the *second* attempt — so the first attempt of a user's own Retry re-requested the
     * URL that had just failed. For a signed URL or a dead image CDN host that attempt is a verbatim
     * repeat of a request known to fail, which is what made "even after retry" true: see `DEF-023`.
     * The same owner now serves the downloader, which previously re-requested the identical URL no
     * matter what the classifier said: see `DEF-028`.
     *
     * If a higher-priority page enters the queue while this page is still waiting to start or
     * between the URL-fetch and image-download phases, this method yields immediately: the page
     * is reset to [Page.State.Queue] and re-enqueued at its original [priority] so that a free
     * worker can pick up the urgent page without delay.
     *
     * **Concurrency safety**: re-enqueueing a page sets its status back to [Page.State.Queue],
     * but the worker collecting from the queue always filters with
     * `it.page.status == Page.State.Queue`. This ensures that if a second queue entry for this
     * page already exists (e.g. added by [loadPage]) and is dequeued by another worker before
     * the reset, that worker starts the load and this one's re-queued entry is simply skipped
     * by the filter when it is eventually dequeued — no double-download occurs.
     *
     * @param page the page whose source image has to be downloaded.
     * @param priority the queue priority at which this page was dequeued.
     */
    private suspend fun internalLoadPage(page: ReaderPage, priority: Int) {
        // One owner for the decision, so the downloader cannot drift from the reader on what to do
        // about a failure. It used to: the reader dropped a URL the classifier indicted and asked
        // the source again, while the downloader retried the identical string against the same
        // classifier — so a chapter could read and fail to download. See `PageLoadRecovery`.
        val recovery = PageLoadRecovery()
        while (true) {
            try {
                // Yield to a higher-priority page before starting the URL fetch.
                if (requeueAndYield(page, priority)) return

                // Clear the previous attempt's progress. `Page.progress` is written by
                // `ProgressListener` as bytes arrive and is not reset anywhere on this path, so a
                // download that died at 47% left the retry ladder showing a *frozen determinate*
                // spinner at 47% -- a status that is confidently wrong. Zero renders as the
                // indeterminate spinner, which is what is actually true: the next attempt has not
                // started transferring yet. `Downloader` already does this; the reader did not.
                page.progress = 0

                if (page.imageUrl.isNullOrEmpty()) {
                    page.status = Page.State.LoadPage
                    // Paced, but *only* when this is a re-resolution. The first resolution of a page
                    // is on the hot path — the user is waiting for that image, and the page they are
                    // waiting on competes with the preload window for the same few workers — so
                    // spacing those out taxes every chapter open to solve a problem that only exists
                    // after something has already failed. Measured on a six-page preload window,
                    // pacing unconditionally added 1.2s, and up to 400ms to the page being waited
                    // for. `isRetrySequence` is the guard, and it is the reason it is asked here
                    // rather than inferred.
                    if (recovery.isRetrySequence) {
                        reResolvePacer.paceReResolution().takeIf { it > 0 }?.let { delay(it) }
                    }
                    // The extension ABI — which field holds the address, whether this source customises
                    // the chain, how to ask it — lives in one place, `resolvePageImage`. It used to be
                    // spelled out here, and separately in `Downloader`, and both had to be corrected
                    // more than once. A Jellyfin or local-archive consumer will not learn these rules
                    // at all; it asks one question.
                    //
                    // What stays here is page-load *policy*, which depends on state this cannot see:
                    // pacing above, and the repeat-address check below.
                    val resolved = source.resolvePageImage(page, pageListOrigin).value
                    // A source that hands back the identical string we have already rejected is not
                    // going to produce a different one on the next call either, and every call it
                    // does make is a round-trip spent learning nothing. Reporting the defect now
                    // ends the ladder sooner and reports the *cause* rather than a resolver error
                    // about a name that can never exist.
                    //
                    // Re-judged here, redundantly, on purpose: this is the branch that decides whether
                    // a *repeat* address is worth another round-trip, so it must answer from the value
                    // it is about to store rather than inherit an answer computed for a different
                    // string.
                    if (recovery.isKnownUnusable(resolved)) {
                        ImageUrlPolicy.requireUsable(resolved)
                    }
                    page.imageUrl = resolved
                }
                // **`page.imageUrl` is opaque to the host once populated.** It is *not* resolved or
                // rewritten here, and nothing else in this loader may write into it. The source
                // that produced it owns its interpretation: MangaDex — the canonical 1.6
                // extension — stores a **relative path** (`/data/<hash>/<file>`) in `imageUrl`
                // and an at-home cache key in `url`, and its own overridden `imageRequest` joins
                // them (`GET(mdAtHomeServerUrl + page.imageUrl)`). A previous version of this
                // loader resolved `imageUrl` against `baseUrl` and wrote the result back before
                // fetching, so MangaDex's request became
                // `"<at-home-host>https://mangadex.org/data/..."` — two URLs spliced into a host
                // that can never resolve — and **every page of every chapter failed identically**.
                // Upstream Mihon never writes into a populated `Page.imageUrl`, and neither do we.
                //
                // Sources that do *not* override `imageRequest` lose nothing: the base
                // `HttpSource.imageRequest` resolves at the request boundary (see
                // `PageImageAddress`), where a source override inherits nothing by design. The
                // only consumers of the raw value below are the cache key and the persisted page
                // list, which must both be the same string the source produced.
                val imageUrl = requireNotNull(page.imageUrl) { "Image URL is null after being fetched from source" }

                recovery.onResolved(imageUrl)

                // Yield again after the URL fetch (which can be slow) and before the potentially
                // large image download, giving the urgent page a chance to start promptly.
                if (requeueAndYield(page, priority)) return

                if (!chapterCache.isImageInCache(imageUrl)) {
                    page.status = Page.State.DownloadImage
                    chapterCache.fetchAndCacheImage(imageUrl) { source.getImage(page) }
                }

                page.stream = {
                    // getImageFile returns null if the entry was evicted from the disk cache
                    // (e.g. LRU pressure during a rapid progress-bar seek).
                    //
                    // The reset MUST also re-offer the page to the queue. Setting
                    // `status = Queue` is not enough: `Queue` is a *status*, not an enqueue, and
                    // the only thing that puts a page in front of a worker is `queue.offer`. The
                    // pager's load trigger is `LaunchedEffect(page)`, keyed on page *identity*, so
                    // it cannot re-fire because a status changed. Without the offer below, nothing
                    // ever picks this page up again and it stays `Queue` forever — silently blank
                    // rather than errored, which is how this presented as "missed images".
                    //
                    // Offering at the same priority this load was dequeued at preserves ordering
                    // against the other in-flight pages rather than jumping the whole preload
                    // window. `imageUrl` is deliberately left intact: re-resolving it is a
                    // separate concern (`DEF-018`) and doing it here would silently change which
                    // URL a page that was mid-render points at.
                    chapterCache.getImageFile(imageUrl)?.inputStream() ?: run {
                        prepareForReload(page)
                        queue.offer(PriorityPage(page, priority))
                        throw IOException("Image evicted from cache, page re-queued for re-download: $imageUrl")
                    }
                }

                // Run pre-processor check on boundary pages. If the page matches a
                // blocked hash, it is marked hidden and the viewer is notified to
                // rebuild its adapter list *without* this page. Crucially, we must
                // NOT transition to Ready for blocked pages — doing so would cause
                // the page holder's statusFlow collector to call setImage() and
                // briefly render the blocked content before the adapter rebuild
                // removes the holder.
                val totalPages = chapter.pages?.size ?: 0
                if (preProcessor?.checkPageOnLoad(page, totalPages) == true) {
                    onPageFiltered?.invoke()
                    return
                }

                page.status = Page.State.Ready
                return
            } catch (e: Throwable) {
                if (e is CancellationException) throw e

                // One decision, one owner, shared with the downloader: retry this URL, ask the
                // source for a different one, or stop. `PageLoadRecovery` also decides whether the
                // failed URL is dropped from the page — including when the answer is "stop", because
                // a URL the classifier has indicted is dead either way, and leaving it on the page
                // would let it reach the list `recycle` persists, so the next open of this chapter
                // would begin by requesting an address already known to be bad.
                //
                // Dropping it here, at the point of failure, rather than at the start of the next
                // attempt, is what makes this survive a yield: [requeueAndYield] returns out of this
                // loop and a fresh call starts a fresh attempt sequence, so a decision held in a
                // local would be lost and the page would go back to the URL that just failed.
                //
                // The old `rejectedUrl = null` on every successful resolve is now
                // `recovery.onResolved(imageUrl)`, at the same point in the same order, so a source
                // that fixes itself is not refused forever on the strength of an older failure.
                val decision = recovery.onFailure(page.imageUrl, e)
                if (decision.dropUrl) {
                    // Replaced rather than cleared, and flagged as well: clearing leaves the page
                    // asking `getImageUrl` for a new address, which for a source that populates
                    // `Page.imageUrl` in `getPageList` is a call it does not implement — the reported
                    // failure. The replacement has to come from a page list, the only place those
                    // addresses exist.
                    page.imageUrl = freshAddresses.at(page.index)
                    page.needsFreshAddress = false
                }

                if (decision.action == PageLoadRecoveryAction.GIVE_UP) {
                    // The only place that knows what was tried. Without it a page that gave up after
                    // three hosts reports one host's error and the other two are unrecorded anywhere,
                    // which is what made the original report a puzzle to reason about rather than a
                    // fault to read.
                    //
                    // The line leads with the layer that *owns* the failure, not the one that noticed
                    // it. A transport failure on an address that could never have worked is an ADAPTER
                    // failure reported late, and `MalformedImageUrlException` is classified that way on
                    // purpose — the MangaDex report was `Unable to resolve host "…,https"`, which reads
                    // as a network fault and sent the investigation to the resolver when the string was
                    // the defect. Leading with the layer makes this line answer "renderer, source or
                    // adapter?" before it answers "what happened?".
                    logcat(LogPriority.WARN, decision.error) {
                        giveUpMessage(page.number, chapter.chapter.name, decision, recovery.summary())
                    }
                    page.status = Page.State.Error(decision.error)
                    return
                }
                delay(decision.delayMs)
            }
        }
    }

    /**
     * If there is a page in the queue with strictly higher [priority] than [currentPriority],
     * resets [page] to [Page.State.Queue] and re-enqueues it at [currentPriority] so the
     * calling worker is freed to service the more urgent request instead. Returns `true` when
     * the caller should `return` immediately (yield occurred), `false` otherwise.
     *
     * Uses [PriorityBlockingQueue.peek] which is non-blocking and O(1), so calling this at
     * natural suspension points adds no measurable overhead on the common path.
     */
    private fun requeueAndYield(page: ReaderPage, currentPriority: Int): Boolean {
        if (!shouldYield(currentPriority)) return false
        page.status = Page.State.Queue
        queue.offer(PriorityPage(page, currentPriority))
        return true
    }

    /**
     * Returns `true` if there is a page in the queue whose priority is strictly greater than
     * [currentPriority].
     */
    private fun shouldYield(currentPriority: Int): Boolean {
        val next = queue.peek() ?: return false
        return next.priority > currentPriority
    }

    companion object {
        /**
         * Whether [recycle] has to write the page list back to the chapter cache.
         *
         * **Why this is not just the [cacheHadMissingImageUrls] flag.** That flag answers "was the
         * list complete when it was loaded?", and a URL *dropped during this session* is invisible
         * to it. A page drops its URL precisely when the URL is what failed (`DEF-023`: a signed URL
         * the source has revoked, or an image host that does not resolve), and the one thing that
         * must not survive into the next open of this chapter is that dead URL — otherwise every
         * open begins by spending a request on a host already known to be dead. So the decision is
         * re-derived from the URLs the pages hold *now*.
         *
         * Takes the URLs rather than the pages so the rule is a pure function of two values and can
         * be asserted directly, rather than needing a live chapter to ask.
         */
        internal fun needsPageListSave(
            cacheHadMissingImageUrls: Boolean,
            imageUrls: List<String?>,
        ): Boolean = cacheHadMissingImageUrls || imageUrls.any { it.isNullOrEmpty() }

        /**
         * Whether a page list read back from the chapter cache can be trusted.
         *
         * **This is the fix for "it worked a week ago".** The chapter cache is a *provider* of page
         * data, and until now it was the one provider whose output was never checked. A page list
         * written by a bad pass — the reported case being a URL that is a three-part composite of a
         * host, an API URL and a timestamp rather than an address — was read back and used verbatim,
         * forever, across app updates. Because the poisoned `imageUrl` is non-empty, the loader's
         * "resolve it from the source" branch is skipped entirely, so `source.getImageUrl` is never
         * called: fixing the source cannot help, and neither can any fix that runs after the read.
         * The only way out was clearing the cache by hand.
         *
         * That is why the reported failure outlived every URL-policy change: none of them looked here.
         *
         * **Why the whole list is discarded rather than the one bad page.** The pages arrived from a
         * single `putPageListToCache`, so a list containing one unusable URL is evidence that the
         * write was wrong, not that one page happened to be. Keeping the rest would leave a list the
         * source never produced.
         *
         * A page whose `imageUrl` is null or empty is *not* a failure: that is the ordinary state of
         * a list fetched from the network and not yet resolved, and it is what
         * [needsPageListSave] exists to track.
         *
         * **Why the check resolves before judging, rather than judging the raw string.** A cached
         * page holding a *relative* URL is a supported, ordinary state — `img.attr("src")` instead
         * of `absUrl("src")` is everyday source code, and the loader's job is precisely to complete
         * it against `baseUrl`. Judging the raw string would reject that and send a perfectly
         * recoverable page back to the source on every open, which is the same failure as not
         * caching at all, only slower. So the question is not "is this already an address" but "can
         * this become one", and only the second is disqualifying.
         */
        internal fun cachedPagesAreUsable(
            pages: List<Page>,
            baseUrl: String?,
        ): Boolean = !pages.needsFreshPageList(baseUrl)

        /**
         * The terminal log line for a page that has exhausted its ladder.
         *
         * Leads with the layer that **owns** the fault rather than the one that noticed it last.
         * The reported failure read `Giving up on page 3 after 3 attempt(s): Unable to resolve host
         * "cmxd98sb0x3yprd.mangadex.network,https"`, which described the resolver — the last thing
         * touched — when the string was already impossible before it left the adapter. Leading with
         * the layer is what makes the next line self-diagnosing without a device log.
         */
        internal fun giveUpMessage(
            pageNumber: Int,
            chapterName: String,
            decision: PageLoadRecoveryDecision,
            summary: String,
        ): String {
            val failure = LayeredFailure.classify(
                operation = "image request",
                subject = "page $pageNumber of $chapterName",
                error = decision.error,
            )
            return "Giving up: ${failure.describe()} after ${decision.attempt} attempt(s): " +
                "$summary (${decision.reason})"
        }

        /**
         * Priority assigned to pages queued by [preloadAllPages]. Set below the nearby-page
         * preload priority (0) so that background full-chapter downloads never steal bandwidth
         * from pages the user is actively reading or about to reach.
         */
        private const val BACKGROUND_PRELOAD_PRIORITY = -1
    }
}

/**
 * Data class used to keep ordering of pages in order to maintain priority.
 */
@OptIn(ExperimentalAtomicApi::class)
private class PriorityPage(
    val page: ReaderPage,
    val priority: Int,
) : Comparable<PriorityPage> {
    companion object {
        private val idGenerator = AtomicInt(0)
    }

    private val identifier = idGenerator.incrementAndFetch()

    override fun compareTo(other: PriorityPage): Int {
        val p = other.priority.compareTo(priority)
        return if (p != 0) p else identifier.compareTo(other.identifier)
    }
}
