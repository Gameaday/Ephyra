package ephyra.core.download

import android.content.Context
import com.hippo.unifile.UniFile
import ephyra.core.archive.ZipWriter
import ephyra.core.common.di.IoDispatcher
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.storage.extension
import ephyra.core.common.util.lang.launchIO
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.network.PageLoadRecovery
import ephyra.core.common.util.network.PageLoadRecoveryAction
import ephyra.core.common.util.network.ReResolvePacer
import ephyra.core.common.util.network.ResolvedImageUrl
import ephyra.core.common.util.storage.DiskUtil
import ephyra.core.common.util.storage.DiskUtil.NOMEDIA_FILE
import ephyra.core.common.util.storage.saveTo
import ephyra.core.common.util.system.ImageUtil
import ephyra.core.common.util.system.encoder
import ephyra.core.common.util.system.logcat
import ephyra.core.download.Downloader.Companion.BOUNDARY_PAGES
import ephyra.core.metadata.comicinfo.COMIC_INFO_FILE
import ephyra.core.metadata.comicinfo.ComicInfo
import ephyra.data.cache.ChapterCache
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.chapter.model.toSChapter
import ephyra.domain.chapter.service.getChapterSort
import ephyra.domain.download.model.Download
import ephyra.domain.download.service.DownloadNotifier
import ephyra.domain.download.service.DownloadPreferences
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.getComicInfo
import ephyra.domain.reader.service.ReaderPreferences
import ephyra.domain.source.service.SourceManager
import ephyra.domain.track.interactor.GetTracks
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.needsFreshPageList
import eu.kanade.tachiyomi.source.online.resolvePageImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import nl.adaptivity.xmlutil.serialization.XML
import okhttp3.Response
import okio.Buffer
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.inject.Provider
import kotlin.math.abs
import kotlin.math.min

/**
 * This class is the one in charge of downloading chapters.
 *
 * Its queue contains the list of chapters to download.
 */
class Downloader(
    private val context: Context,
    private val provider: DownloadProvider,
    private val cache: DownloadCache,
    private val sourceManagerProvider: Provider<SourceManager>,
    private val chapterCache: ChapterCache,
    private val downloadPreferences: DownloadPreferences,
    private val readerPreferences: ReaderPreferences,
    private val libraryPreferences: LibraryPreferences,
    private val xml: XML,
    private val getCategories: GetCategories,
    private val getTracks: GetTracks,
    private val notifier: DownloadNotifier,
    private val store: DownloadStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val sourceManager get() = sourceManagerProvider.get()

    /**
     * Queue where active downloads are kept.
     */
    private val _queueState = MutableStateFlow<List<Download>>(emptyList())
    val queueState = _queueState.asStateFlow()

    /**
     * Notifier for the downloader state and progress.
     */

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var downloaderJob: Job? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunningFlow = _isRunning.asStateFlow()

    /**
     * Whether the downloader is running.
     */
    val isRunning: Boolean
        get() = _isRunning.value

    /**
     * Whether the downloader is paused
     */
    @Volatile
    var isPaused: Boolean = false

    init {
        scope.launch {
            val chapters = store.restore()
            addAllToQueue(chapters)
        }
    }

    /**
     * Starts the downloader. It doesn't do anything if it's already running or there isn't anything
     * to download.
     *
     * @return true if the downloader is started, false otherwise.
     */
    fun start(): Boolean {
        if (isRunning || queueState.value.isEmpty()) {
            return false
        }

        val pending = queueState.value.filter { it.status != Download.State.DOWNLOADED }
        pending.forEach { if (it.status != Download.State.QUEUE) it.status = Download.State.QUEUE }

        isPaused = false

        launchDownloaderJob()

        return pending.isNotEmpty()
    }

    /**
     * Stops the downloader.
     */
    fun stop(reason: String? = null) {
        cancelDownloaderJob()
        queueState.value
            .filter { it.status == Download.State.DOWNLOADING }
            .forEach { it.status = Download.State.ERROR }

        if (reason != null) {
            notifier.onWarning(reason)
            return
        }

        if (isPaused && queueState.value.isNotEmpty()) {
            notifier.onPaused()
        } else {
            notifier.onComplete()
        }

        isPaused = false

        DownloadJob.stop(context)
    }

    /**
     * Pauses the downloader
     */
    fun pause() {
        cancelDownloaderJob()
        queueState.value
            .filter { it.status == Download.State.DOWNLOADING }
            .forEach { it.status = Download.State.QUEUE }
        isPaused = true
    }

    /**
     * Removes everything from the queue.
     */
    fun clearQueue() {
        cancelDownloaderJob()

        internalClearQueue()
        notifier.dismissProgress()
    }

    /**
     * Prepares the subscriptions to start downloading.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun launchDownloaderJob() {
        if (isRunning) return

        _isRunning.value = true
        downloaderJob = scope.launch {
            try {
                val activeDownloadsFlow = combine(
                    queueState,
                    downloadPreferences.parallelSourceLimit().changes(),
                ) { a, b -> a to b }.transformLatest { (queue, parallelCount) ->
                    while (true) {
                        val activeDownloads = queue.asSequence()
                            // Ignore completed downloads, leave them in the queue
                            .filter { it.status.value <= Download.State.DOWNLOADING.value }
                            .groupBy { it.source }
                            .toList()
                            .take(parallelCount)
                            .map { (_, downloads) -> downloads.first() }
                        emit(activeDownloads)

                        if (activeDownloads.isEmpty()) break
                        // Suspend until a download enters the ERROR state
                        val activeDownloadsErroredFlow =
                            combine(activeDownloads.map(Download::statusFlow)) { states ->
                                states.contains(Download.State.ERROR)
                            }.filter { it }
                        activeDownloadsErroredFlow.first()
                    }
                }
                    .distinctUntilChanged()

                // Use supervisorScope to cancel child jobs when the downloader job is cancelled
                supervisorScope {
                    val downloadJobs = mutableMapOf<Download, Job>()

                    activeDownloadsFlow.collectLatest { activeDownloads ->
                        val downloadJobsToStop = downloadJobs.filter { it.key !in activeDownloads }
                        downloadJobsToStop.forEach { (download, job) ->
                            job.cancel()
                            downloadJobs.remove(download)
                        }

                        val downloadsToStart = activeDownloads.filter { it !in downloadJobs }
                        downloadsToStart.forEach { download ->
                            downloadJobs[download] = launchDownloadJob(download)
                        }
                    }
                }
            } finally {
                _isRunning.value = false
            }
        }
    }

    private fun CoroutineScope.launchDownloadJob(download: Download) = launchIO {
        try {
            downloadChapter(download)

            // Remove successful download from queue
            if (download.status == Download.State.DOWNLOADED) {
                removeFromQueue(download)
            }
            if (areAllDownloadsFinished()) {
                stop()
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            logcat(LogPriority.ERROR, e)
            notifier.onError(e.message)
            stop()
        }
    }

    /**
     * Destroys the downloader subscriptions.
     */
    private fun cancelDownloaderJob() {
        downloaderJob?.cancel()
        downloaderJob = null
        _isRunning.value = false
    }

    /**
     * Creates a download object for every chapter and adds them to the downloads queue.
     *
     * @param manga the manga of the chapters to download.
     * @param chapters the list of chapters to download.
     * @param autoStart whether to start the downloader after enqueing the chapters.
     */
    fun queueChapters(manga: Manga, chapters: List<Chapter>, autoStart: Boolean) {
        if (chapters.isEmpty()) return

        val source = sourceManager.get(manga.source) as? HttpSource ?: return
        val wasEmpty = queueState.value.isEmpty()
        val enqueuedChapterIds = queueState.value.mapTo(HashSet()) { it.chapter.id }
        val chaptersToQueue = chapters.asSequence()
            // Filter out those already downloaded.
            .filter { provider.findChapterDir(it.name, it.scanlator, it.url, manga.title, source) == null }
            // Preserve the manga's configured chapter ordering instead of assuming that raw
            // source order always represents newest-first display order.
            .sortedWith(getChapterSort(manga))
            // Filter out those already enqueued.
            .filter { chapter -> chapter.id !in enqueuedChapterIds }
            // Create a download for each one.
            .map { Download(source, manga, it) }
            .toList()

        if (chaptersToQueue.isNotEmpty()) {
            addAllToQueue(chaptersToQueue)

            // Start downloader if needed
            if (autoStart && wasEmpty) {
                val queuedDownloads = queueState.value.count { it.source !is UnmeteredSource }
                val maxDownloadsFromSource = queueState.value
                    .groupBy { it.source }
                    .filterKeys { it !is UnmeteredSource }
                    .maxOfOrNull { it.value.size }
                    ?: 0
                if (
                    queuedDownloads > DOWNLOADS_QUEUED_WARNING_THRESHOLD ||
                    maxDownloadsFromSource > CHAPTERS_PER_SOURCE_QUEUE_WARNING_THRESHOLD
                ) {
                    // Skip warning for now or use an interface if needed
                }
                DownloadJob.start(context, downloadPreferences.downloadOnlyOverWifi().getSync())
            }
        }
    }

    /**
     * Downloads a chapter.
     *
     * @param download the chapter to be downloaded.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun downloadChapter(download: Download) {
        val mangaDir = provider.getMangaDir(download.manga.title, download.source).getOrElse { e ->
            download.status = Download.State.ERROR
            notifier.onError(e.message, download.chapter.name, download.manga.title, download.manga.id)
            return
        }

        val availSpace = DiskUtil.getAvailableStorageSpace(mangaDir)
        if (availSpace != -1L && availSpace < MIN_DISK_SPACE) {
            pause()
            notifier.onError(
                context.stringResource(ephyra.app.core.common.R.string.download_insufficient_space),
            )
            return
        }

        val chapterDirname = if (
            libraryPreferences.jellyfinCompatibleNaming().get() &&
            downloadPreferences.saveChaptersAsCBZ().get()
        ) {
            provider.getJellyfinChapterDirName(
                download.manga.title,
                download.chapter.chapterNumber,
                download.chapter.name,
            )
        } else {
            provider.getChapterDirName(
                download.chapter.name,
                download.chapter.scanlator,
                download.chapter.url,
                libraryPreferences.disallowNonAsciiFilenames().get(),
            )
        }
        val tmpDir = mangaDir.createDirectory(chapterDirname + TMP_DIR_SUFFIX)
            ?: error("Failed to create temporary download directory for chapter ${download.chapter.name}")

        // One pacer per job, the same scope the reader uses per chapter loader. Sources mint signed
        // URLs in batches with a common expiry, so a download that runs past that boundary has every
        // page fail at once -- and without this, every page's retry asks the source the same
        // question at the same instant, at a source that has just signalled it is unhappy.
        val reResolvePacer = ReResolvePacer()

        try {
            // If the page list already exists, start from the file — unless it is a copy that can no longer
            // produce an address, in which case the source is asked again. Refetching is the only
            // repair for that case: it replaces the bad copy, where resolving it cannot, because
            // `Page.url` is not an address and the source does not implement the chain that would
            // read it. See `needsFreshPageList`.
            val pageList = download.pages?.takeUnless { it.needsFreshPageList(download.source.baseUrl) }
                ?: run {
                    // Otherwise, pull page list from network and add them to download object
                    val pages = download.source.getPageList(download.chapter.toSChapter())

                    if (pages.isEmpty()) {
                        throw Exception(context.stringResource(ephyra.app.core.common.R.string.page_list_empty_error))
                    }
                    // Don't trust index from source
                    val reIndexedPages = pages.mapIndexed { index, page ->
                        Page(index, page.url, page.imageUrl, page.uri)
                    }
                    download.pages = reIndexedPages
                    reIndexedPages
                }

            // Delete all temporary (unfinished) files
            tmpDir.listFiles()
                ?.filter { it.extension == "tmp" }
                ?.forEach { it.delete() }

            // One per download run, shared by every page: pages download concurrently, and a signed URL that
            // expired should cost one page-list fetch for the chapter, not one per page.
            val freshAddresses = FreshPageAddresses(download)

            download.status = Download.State.DOWNLOADING

            // Start downloading images, consider we can have downloaded images already
            pageList.asFlow().flatMapMerge(concurrency = downloadPreferences.parallelPageLimit().get()) { page ->
                flow {
                    // Fetch image URL if necessary
                    //
                    // The emptiness test is the same one the reader makes, and both now come from
                    // one place. It used to be spelled out here separately, which is how a download
                    // and a read of the same chapter could end up resolving different fields and
                    // cache the same bytes twice under two spellings.
                    if (page.imageUrl.isNullOrEmpty()) {
                        page.status = Page.State.LoadPage
                        try {
                            // One question, asked of one owner. Which field holds the address, whether
                            // this source customises the chain, and how to ask it are all inside
                            // `resolvePageImage`; a Jellyfin or local-archive consumer will not learn
                            // any of it.
                            page.imageUrl = download.source
                                .resolvePageImage(page, "download/first attempt").value
                        } catch (e: Throwable) {
                            page.status = Page.State.Error(e)
                        }
                    }

                    withIOContext {
                        getOrDownloadImage(page, download, tmpDir, reResolvePacer, freshAddresses)
                    }
                    emit(page)
                }
                    .flowOn(ioDispatcher)
            }
                .collect {
                    // Do when page is downloaded.
                    notifier.onProgressChange(download)
                }

            // Do after download completes

            if (!isDownloadSuccessful(download, tmpDir)) {
                download.status = Download.State.ERROR
                return
            }

            // Unified post-processing: blocklist filtering + stub-page merging in a single
            // ordered pass. Lists files once, applies position-aware credit page detection
            // with aspect-ratio pre-filter, then merges consecutive stub pages.
            postProcessPages(tmpDir)

            createComicInfoFile(
                tmpDir,
                download.manga,
                download.chapter,
                download.source,
            )

            // Only rename the directory if it's downloaded. The result is *checked*, because
            // everything below this line publishes the chapter: the index entry the reader routes on,
            // and the DOWNLOADED state the library shows. On a SAF-backed root (SD card, OTG) the
            // rename can fail — provider refuses the name, volume full during the media rescan, the
            // directory still open — and an unchecked failure indexed a chapter whose directory does
            // not exist, which the reader then opens as a hard "page list empty" error. A chapter
            // that could not be published is an error, not a success.
            val published = if (downloadPreferences.saveChaptersAsCBZ().get()) {
                // The CBZ path reports failure by throwing, and that is left as it is: the
                // surrounding catch already turns it into ERROR.
                archiveChapter(mangaDir, chapterDirname, tmpDir)
                true
            } else {
                val renamed = tmpDir.renameTo(chapterDirname)
                if (!renamed) {
                    logcat(LogPriority.ERROR) {
                        "Failed to publish $chapterDirname: rename returned false; " +
                            "leaving the pages in ${tmpDir.name}"
                    }
                }
                renamed
            }
            if (!published) {
                download.status = Download.State.ERROR
                return
            }

            // Copy CBZ to Jellyfin library folder if sync is enabled and folder is configured
            if (downloadPreferences.autoSyncToJellyfin().get() &&
                downloadPreferences.saveChaptersAsCBZ().get() &&
                libraryPreferences.jellyfinCompatibleNaming().get() &&
                downloadPreferences.jellyfinLibraryFolder().get().isNotBlank()
            ) {
                copyToJellyfinLibrary(mangaDir, chapterDirname, download.manga.title)
            }

            cache.addChapter(chapterDirname, mangaDir, download.manga)

            DiskUtil.createNoMediaFile(tmpDir, context)

            download.status = Download.State.DOWNLOADED
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            // If the page list threw, it will resume here
            logcat(LogPriority.ERROR, error)
            download.status = Download.State.ERROR
            notifier.onError(error.message, download.chapter.name, download.manga.title, download.manga.id)
        }
    }

    // Gets the image from the filesystem if it exists or downloads it otherwise.
    //
    // @param page the page to download.
    // @param download the download of the page.
    // @param tmpDir the temporary directory of the download.

    /**
     * Fresh page addresses for one download run, fetched at most once.
     *
     * **Why a refetch and not `getImageUrl`.** When the recovery ladder drops an indicted URL it leaves
     * the page with a blank `imageUrl`, and the next attempt asks the source for a replacement. For a
     * source that populates `Page.imageUrl` in `getPageList` — which is every 1.6 extension, since
     * upstream removed the per-page chain from the extension API — there is **no per-page call that
     * returns an address**. Making one runs an inherited default that throws, from a method the source
     * does not implement. The addresses only exist in a page list.
     *
     * This is the reported failure in full: a page whose address expired was dropped, and the attempt to
     * replace it called a method MangaDex does not have. Dropping the URL was correct — a dead signed URL
     * should not be reused — but there was no way back from it.
     *
     * Memoised per run because pages download concurrently: without this, one expired signed URL costs a
     * page-list fetch per page instead of one per chapter.
     */
    private class FreshPageAddresses(private val download: Download) {
        private val mutex = Mutex()
        private var pages: List<Page>? = null

        suspend fun at(index: Int): String? {
            val list = mutex.withLock {
                pages ?: download.source.getPageList(download.chapter.toSChapter()).also { pages = it }
            }
            return list.getOrNull(index)?.imageUrl
        }
    }

    private suspend fun getOrDownloadImage(
        page: Page,
        download: Download,
        tmpDir: UniFile,
        reResolvePacer: ReResolvePacer,
        freshAddresses: FreshPageAddresses,
    ) {
        // If the image URL is empty, do nothing
        if (page.imageUrl == null) {
            return
        }

        // One recovery per page, not per download: pages are downloaded concurrently through
        // `flatMapMerge`, and this is the per-page attempt sequence the reader's loader also keeps
        // one of. See `PageLoadRecovery`.
        val recovery = PageLoadRecovery()

        val digitCount = (download.pages?.size ?: 0).toString().length.coerceAtLeast(3)
        val filename = "%0${digitCount}d".format(Locale.ENGLISH, page.number)
        val tmpFile = tmpDir.findFile("$filename.tmp")

        // Delete temp file if it exists
        tmpFile?.delete()

        // Try to find the image file
        val imageFile = tmpDir.listFiles()?.firstOrNull {
            it.name!!.startsWith("$filename.") || it.name!!.startsWith("${filename}__001")
        }

        try {
            // If the image is already downloaded, do nothing. Otherwise download from network
            val file = when {
                imageFile != null -> imageFile
                // getImageFile returns null if the cache entry was evicted between isImageInCache
                // and the actual file access (TOCTOU race). Fall through to downloadImage in that case.
                chapterCache.isImageInCache(page.imageUrl!!) ->
                    chapterCache.getImageFile(page.imageUrl!!)
                        ?.let { copyImageFromCache(it, tmpDir, filename) }
                        ?: downloadImage(page, download, tmpDir, filename, recovery, reResolvePacer, freshAddresses)

                else -> downloadImage(page, download, tmpDir, filename, recovery, reResolvePacer, freshAddresses)
            }

            // When the page is ready, set page path, progress (just in case) and status
            splitTallImageIfNeeded(page, tmpDir)

            page.uri = file.uri
            page.progress = 100
            page.status = Page.State.Ready
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            // Mark this page as error and allow to download the remaining
            page.progress = 0
            page.status = Page.State.Error(e)
            notifier.onError(e.message, download.chapter.name, download.manga.title, download.manga.id)
        }
    }

    /**
     * Downloads the image from network to a file in tmpDir.
     *
     * @param page the page to download.
     * @param source the source of the page.
     * @param tmpDir the temporary directory of the download.
     * @param filename the filename of the image.
     */
    private suspend fun downloadImage(
        page: Page,
        download: Download,
        tmpDir: UniFile,
        filename: String,
        recovery: PageLoadRecovery,
        reResolvePacer: ReResolvePacer,
        freshAddresses: FreshPageAddresses,
    ): UniFile {
        val source = download.source
        page.status = Page.State.DownloadImage
        page.progress = 0
        return flow {
            val response = source.getImage(page)
            val file = tmpDir.createFile("$filename.tmp")
                ?: error("Failed to create temporary file for page $filename")
            try {
                response.body.source().saveTo(file.openOutputStream())
                val extension = getImageExtension(response, file)
                file.renameTo("$filename.$extension")
            } catch (e: Exception) {
                response.close()
                file.delete()
                throw e
            }
            emit(file)
        }
            // One decision, one owner, shared with the reader -- see `PageLoadRecovery`.
            //
            // This used to be a second copy of the rule and it had drifted in the way that
            // mattered: the classifier was shared, so the downloader *knew* a 403 or an unresolvable
            // host indicted the URL, and then retried the identical string anyway. The backoff had
            // drifted too, 2s/4s/8s here against the reader's 1s/2s/4s, from its own
            // `(2L shl attempt) * 1000`. The observable consequence was that a chapter with
            // short-lived signed image URLs read successfully and failed to download.
            //
            // `dropUrl` is applied here rather than by the caller, because the caller *is* this
            // lambda and the next attempt re-reads `page.imageUrl` through
            // `HttpSource.imageRequest`. The drop is deliberately ahead of the give-up check: a dead
            // URL must not survive even when nothing follows, and the re-resolution is behind it so
            // the final attempt never spends a source round-trip on a URL it is about to discard.
            .retryWhen { cause, _ ->
                if (cause is CancellationException) return@retryWhen false

                val decision = recovery.onFailure(page.imageUrl, cause)

                // Drop before anything else, including on give-up. A URL the classifier has indicted
                // is dead whether or not another attempt follows, and leaving it on the page would
                // let it be reused by a later run of this chapter.
                if (decision.dropUrl) {
                    // Replaced rather than merely cleared. Clearing leaves the page asking
                    // `getImageUrl` for a new address, which for a source that populates
                    // `Page.imageUrl` in `getPageList` is a call it does not implement — the reported
                    // failure. The replacement has to come from a page list, which is the only place
                    // those addresses exist.
                    page.imageUrl = freshAddresses.at(page.index)
                }

                if (decision.action == PageLoadRecoveryAction.GIVE_UP) {
                    // Logged, not notified. Returning false rethrows the cause, and the enclosing
                    // `getOrDownloadImage` catch already reports this page to the user -- notifying
                    // here as well would report every failed page twice. The user's message stays
                    // the underlying error; this line is for whoever has to work out why.
                    logcat(LogPriority.WARN, cause) {
                        "Gave up on page ${page.number} of ${download.chapter.name} after " +
                            "${decision.attempt} attempt(s): ${recovery.summary()} (${decision.reason})"
                    }
                    return@retryWhen false
                }

                if (decision.dropUrl) {
                    // Paced only on a re-resolution, for the same reason as the reader: a page's
                    // first URL is on the hot path, and `isRetrySequence` is what distinguishes the
                    // two rather than a guess at each call site.
                    if (recovery.isRetrySequence) {
                        reResolvePacer.paceReResolution().takeIf { it > 0 }?.let { delay(it) }
                    }
                    // A source may supply `Page.imageUrl` in `pageListParse` and never implement
                    // `imageUrlParse`, in which case asking again throws the base
                    // `UnsupportedOperationException`. Letting that escape would replace the `403`
                    // that actually stopped the page, and the user would be told about the
                    // machinery instead of the cause -- the opposite of what the recovery history is
                    // for. So the resolution failure is recorded as detail and the page stops here,
                    // still reporting the load failure it actually suffered.
                    //
                    // Returning false rather than retrying also avoids spending another attempt on a
                    // request that has no URL to make: `HttpSource.imageRequest` requires one.
                    //
                    // Reached only when a retry follows, so the final attempt never spends a source
                    // round-trip on a URL it is about to discard.
                    page.imageUrl = try {
                        source.resolvePageImage(page, "download/retry").value
                    } catch (resolutionError: Throwable) {
                        if (resolutionError is CancellationException) throw resolutionError
                        recovery.onFailure(null, resolutionError)
                        logcat(LogPriority.WARN, resolutionError) {
                            "Could not obtain a replacement URL for page ${page.number} of " +
                                "${download.chapter.name}: ${recovery.summary()}"
                        }
                        return@retryWhen false
                    }
                }
                delay(decision.delayMs)
                true
            }
            .first()
    }

    /**
     * Copies the image from cache to file in tmpDir.
     *
     * @param cacheFile the file from cache.
     * @param tmpDir the temporary directory of the download.
     * @param filename the filename of the image.
     */
    private fun copyImageFromCache(cacheFile: File, tmpDir: UniFile, filename: String): UniFile {
        val tmpFile = tmpDir.createFile("$filename.tmp")
            ?: throw IOException("Could not create temporary file in download directory")
        cacheFile.inputStream().use { input ->
            tmpFile.openOutputStream().use { output ->
                input.copyTo(output)
            }
        }
        val extension = cacheFile.inputStream().use { ImageUtil.findImageType(it) } ?: return tmpFile
        tmpFile.renameTo("$filename.${extension.extension}")
        // Do NOT delete the cache file here: deleting the raw file from the DiskLruCache directory
        // bypasses journal tracking (leaving a stale entry) and can break active reader streams if
        // the same chapter is open in the reader at the same time.  The DiskLruCache manages its
        // own LRU eviction automatically when new content is added.
        return tmpFile
    }

    /**
     * Returns the extension of the downloaded image from the network response, or if it's null,
     * analyze the file. If everything fails, assume it's a jpg.
     *
     * @param response the network response of the image.
     * @param file the file where the image is already downloaded.
     */
    private fun getImageExtension(response: Response, file: UniFile): String {
        val mime = response.body.contentType()?.run { if (type == "image") "image/$subtype" else null }
        return ImageUtil.getExtensionFromMimeType(mime) { file.openInputStream() }
    }

    /**
     * Returns the encoder function and file extension for the user's preferred image format.
     * Used by every code path that creates a derived (split / merged / rotated) image so
     * that all such images within a chapter use the same format.
     */
    private suspend fun derivedImageEncoder(): Pair<(android.graphics.Bitmap, java.io.OutputStream) -> Unit, String> {
        val fmt = libraryPreferences.imageFormat().get()
        return fmt.encoder() to fmt.extension
    }

    private suspend fun splitTallImageIfNeeded(page: Page, tmpDir: UniFile) {
        if (!downloadPreferences.splitTallImages().get()) return

        try {
            val filenamePrefix = "%03d".format(Locale.ENGLISH, page.number)
            val imageFile = tmpDir.listFiles()?.firstOrNull { it.name.orEmpty().startsWith(filenamePrefix) }
                ?: error(
                    context.stringResource(
                        ephyra.app.core.common.R.string.download_notifier_split_page_not_found,
                        page.number,
                    ),
                )

            // If the original page was previously split, then skip
            if (imageFile.name.orEmpty().startsWith("${filenamePrefix}__")) return

            val (encoder, ext) = derivedImageEncoder()
            ImageUtil.splitTallImage(tmpDir, imageFile, filenamePrefix, encoder, ext)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to split downloaded image" }
        }
    }

    /**
     * Unified post-download processing pass that runs **after** page-count verification
     * and **before** ComicInfo creation / CBZ archiving.  Combines two concerns into one
     * ordered pipeline:
     *
     * 1. **Credit-page filtering** — removes known scanlation intro/outro/credits pages by
     *    comparing their perceptual hash (dHash) against
     *    [DownloadPreferences.blockedPageHashes].  Uses two optimizations to avoid
     *    computing dHash for every page:
     *    • *Position-aware*: credit pages are almost always at chapter boundaries, so only
     *      the first and last [BOUNDARY_PAGES] pages are checked by default.
     *    • *Aspect-ratio pre-filter*: the dominant (median) aspect ratio is computed from
     *      header-only reads; pages whose aspect ratio is within 5% of the dominant are
     *      assumed to be real content and skipped (credit pages often have a
     *      visually different aspect ratio, e.g. a landscape banner in a portrait manga).
     *
     * 2. **Stub-page merging** — merges narrow watermark strips into the preceding page
     *    when [ReaderPreferences.smartCombinePaged] is enabled. Uses header-only decoding
     *    for the stub check, so the non-stub case is inexpensive.
     *
     * @param tmpDir the temporary chapter directory.
     */
    private suspend fun postProcessPages(tmpDir: UniFile) {
        // ── Phase 1: Credit-page filtering ──────────────────────────────
        val blockedHexes = downloadPreferences.blockedPageHashes().get()
        if (blockedHexes.isNotEmpty()) {
            val blockedDHashes = blockedHexes.mapNotNull { hex ->
                runCatching { ImageUtil.hexToDHash(hex) }.getOrNull()
            }
            if (blockedDHashes.isNotEmpty()) {
                filterBlockedPagesImpl(tmpDir, blockedDHashes)
            }
        }

        // ── Phase 2: Stub-page merging ──────────────────────────────────
        if (readerPreferences.smartCombinePaged().get()) {
            mergeStubPagesImpl(tmpDir)
        }
    }

    /**
     * Removes credit/intro/outro pages using position-aware, aspect-ratio-gated dHash matching.
     *
     * Only pages near chapter boundaries (first/last [BOUNDARY_PAGES]) are candidates.
     * Among those, pages whose aspect ratio closely matches the chapter's dominant aspect ratio
     * are assumed to be real content and skipped — the expensive dHash decode is reserved for
     * pages that *look* structurally different from the majority.
     */
    private fun filterBlockedPagesImpl(tmpDir: UniFile, blockedDHashes: List<Long>) {
        val threshold = DownloadPreferences.BLOCKED_PAGE_DHASH_THRESHOLD

        val allFiles = tmpDir.listFiles()
            ?.filter { file ->
                val name = file.name.orEmpty()
                !name.endsWith(".tmp") &&
                    name !in listOf(COMIC_INFO_FILE, NOMEDIA_FILE) &&
                    ImageUtil.isImage(name)
            }
            ?.sortedBy { it.name }
            ?: return

        if (allFiles.isEmpty()) return

        // Determine the dominant (median) aspect ratio from header-only dimension reads.
        // Cache per-file aspect ratios so we don't re-read headers in the candidate loop.
        val fileAspectRatios = FloatArray(allFiles.size) { -1f }
        val validRatios = mutableListOf<Float>()
        for ((idx, file) in allFiles.withIndex()) {
            try {
                val dims = file.openInputStream().use { ImageUtil.getImageDimensions(it) }
                if (dims != null && dims.second > 0) {
                    val ar = dims.first.toFloat() / dims.second
                    fileAspectRatios[idx] = ar
                    validRatios.add(ar)
                }
            } catch (e: Exception) {
                logcat(LogPriority.DEBUG, e) { "Failed to read dimensions for ${file.name}; skipping" }
            }
        }
        val dominantAR = if (validRatios.isNotEmpty()) {
            validRatios.sort()
            validRatios[validRatios.size / 2]
        } else {
            null
        }

        // Select candidate pages: first/last BOUNDARY_PAGES of the chapter
        val candidateIndices = buildSet {
            for (i in 0 until min(BOUNDARY_PAGES, allFiles.size)) add(i)
            for (i in (allFiles.size - BOUNDARY_PAGES).coerceAtLeast(0) until allFiles.size) add(i)
        }

        for (idx in candidateIndices) {
            val file = allFiles[idx]
            try {
                // Aspect-ratio pre-filter: skip pages matching the dominant ratio within 5 %
                // Uses cached ratio from the dimension scan above — no additional I/O.
                if (dominantAR != null && fileAspectRatios[idx] > 0f) {
                    if (abs(fileAspectRatios[idx] - dominantAR) / dominantAR <= ASPECT_RATIO_TOLERANCE) continue
                }

                // Expensive: compute dHash (uses inSampleSize for reduced-resolution decode)
                val hash = file.openInputStream().use { ImageUtil.computeDHash(it) } ?: continue
                val matched = blockedDHashes.any { blocked ->
                    ImageUtil.dHashDistance(hash, blocked) <= threshold
                }
                if (matched) {
                    logcat(LogPriority.DEBUG) {
                        "Blocked page removed: ${file.name} " +
                            "(dHash=${ImageUtil.dHashToHex(hash)})"
                    }
                    file.delete()
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) {
                    "Failed to process ${file.name} for blocklist check, skipping"
                }
            }
        }
    }

    /**
     * Merges consecutive stub pages (narrow watermark strips) into the preceding page
     * using the same smart-combine logic as the reader.
     */
    private suspend fun mergeStubPagesImpl(tmpDir: UniFile) {
        val (encoder, ext) = derivedImageEncoder()

        // Build a sorted mutable list of primary page image files, excluding:
        //  • temporary files (.tmp)
        //  • metadata files (ComicInfo.xml, .nomedia)
        //  • secondary split pages (e.g. "001__002.webp") — first split ("001__001.webp") is kept
        val pageFiles = tmpDir.listFiles()
            ?.filter { file ->
                val name = file.name.orEmpty()
                !name.endsWith(".tmp") &&
                    name !in listOf(COMIC_INFO_FILE, NOMEDIA_FILE) &&
                    ImageUtil.isImage(name) &&
                    !(name.contains("__") && !name.contains("__001."))
            }
            ?.sortedBy { it.name }
            ?.toMutableList()
            ?: return

        var i = 0
        while (i < pageFiles.size - 1) {
            val current = pageFiles[i]
            val next = pageFiles[i + 1]
            try {
                // Buffer the current page once; isAnimatedAndSupported and isSmallPage both use
                // peek() internally so the buffer is not consumed by the dimension checks.
                val currentSource = current.openInputStream().use { Buffer().readFrom(it) }

                // Animated pages cannot be merged
                if (ImageUtil.isAnimatedAndSupported(currentSource)) {
                    i++
                    continue
                }

                // Header-only stub check for the next page (cheap: reads only image dimensions)
                val isStub = next.openInputStream().use { ImageUtil.isSmallPage(it, currentSource) }
                if (!isStub) {
                    i++
                    continue
                }

                // Stub confirmed: open the next page again for full bitmap decode and merge.
                // currentSource still holds all its data (peek() was used above).
                val nextSource = next.openInputStream().use { Buffer().readFrom(it) }
                val mergedBitmap = ImageUtil.mergePages(currentSource, nextSource)

                // Write the merged image to a temp file, swap it in for the current file,
                // and delete the stub.  Using a temp file prevents data loss if the write fails.
                val baseName = current.name!!.substringBeforeLast(".")
                val mergedTmp = tmpDir.createFile("$baseName.$ext.tmp")
                    ?: throw IOException("Could not create temp file for merged stub page")
                mergedTmp.openOutputStream().use { encoder(mergedBitmap, it) }
                // The merged bitmap is never handed to the UI, so it is simply dropped here and
                // reclaimed by ART. An explicit recycle would be unsafe: the merge result may
                // already be referenced by a snapshot.
                current.delete()
                next.delete()
                pageFiles.removeAt(i + 1)
                // Checked, and fatal on failure. Both source files are already deleted at this
                // point, so a failed rename cannot be recovered from: the only way to publish
                // honestly is to not publish. The enclosing catch deliberately swallows *merge*
                // errors so one undecodable page cannot fail a chapter, but that same tolerance
                // turned this into silent data loss — a chapter that downloads "successfully" with
                // two pages missing, which the reader then opens as a short chapter with no error
                // anywhere. `PageLostException` is the distinction between the two cases.
                if (!mergedTmp.renameTo("$baseName.$ext")) {
                    throw PageLostException(
                        "Merged stub page could not be renamed to $baseName.$ext; " +
                            "${current.name} and ${next.name} were already deleted",
                    )
                }
                // Update our list to point to the freshly renamed file so the next
                // iteration can check the merged page against the new next page.
                val mergedFile = tmpDir.findFile("$baseName.$ext")
                    ?: throw PageLostException(
                        "Merged file $baseName.$ext vanished immediately after a successful rename",
                    )
                pageFiles[i] = mergedFile
                // Do NOT increment i — check the merged page against the new next page
            } catch (e: PageLostException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) {
                    "Failed to merge stub page ${next.name} into ${current.name} during download"
                }
                i++
            }
        }
    }

    /**
     * Checks if the download was successful.
     *
     * @param download the download to check.
     * @param tmpDir the directory where the download is currently stored.
     */
    private fun isDownloadSuccessful(
        download: Download,
        tmpDir: UniFile,
    ): Boolean {
        // Page list hasn't been initialized
        val downloadPageCount = download.pages?.size ?: return false

        // Ensure that all pages have been downloaded
        if (download.downloadedImages != downloadPageCount) {
            return false
        }

        // Ensure that the chapter folder has all the pages
        val downloadedImagesCount = tmpDir.listFiles().orEmpty().count {
            val fileName = it.name.orEmpty()
            when {
                fileName in listOf(COMIC_INFO_FILE, NOMEDIA_FILE) -> false
                fileName.endsWith(".tmp") -> false
                // Only count the first split page and not the others
                fileName.contains("__") && !fileName.contains("__001.") -> false
                else -> true
            }
        }
        return downloadedImagesCount == downloadPageCount
    }

    /**
     * Archive the chapter pages as a CBZ.
     */
    private fun archiveChapter(
        mangaDir: UniFile,
        dirname: String,
        tmpDir: UniFile,
    ) {
        val zip = mangaDir.createFile("$dirname.cbz$TMP_DIR_SUFFIX")
            ?: throw IOException("Could not create CBZ archive file")
        ZipWriter(context, zip).use { writer ->
            tmpDir.listFiles()?.forEach { file ->
                writer.write(file)
            }
        }
        zip.renameTo("$dirname.cbz")
        tmpDir.delete()
    }

    /**
     * Copies a completed CBZ file to the configured Jellyfin library folder.
     * The folder structure is: {jellyfinFolder}/{mangaTitle}/{chapter}.cbz
     * This allows Jellyfin to discover the files via a library scan, even when
     * the app's download directory is not directly accessible to the server
     * (e.g., the Jellyfin folder is an SMB/NFS network share on a NAS).
     */
    private suspend fun copyToJellyfinLibrary(
        mangaDir: UniFile,
        dirname: String,
        mangaTitle: String,
    ) {
        val folderUri = downloadPreferences.jellyfinLibraryFolder().get()
        if (folderUri.isBlank()) return

        try {
            val jellyfinRoot = UniFile.fromUri(context, android.net.Uri.parse(folderUri))
                ?: run {
                    logcat(LogPriority.WARN) { "Jellyfin library folder is not accessible: $folderUri" }
                    return
                }

            // Create series subdirectory: {jellyfinFolder}/{mangaTitle}/
            val seriesDir = jellyfinRoot.createDirectory(
                DiskUtil.buildValidFilename(mangaTitle),
            ) ?: run {
                logcat(LogPriority.WARN) { "Failed to create series directory in Jellyfin library folder" }
                return
            }

            val cbzFile = mangaDir.findFile("$dirname.cbz") ?: run {
                logcat(LogPriority.WARN) { "CBZ file not found for copy: $dirname.cbz" }
                return
            }

            // Skip if the file already exists in the Jellyfin folder
            val destFile = seriesDir.findFile("$dirname.cbz")
            if (destFile != null && destFile.length() > 0) {
                logcat(LogPriority.DEBUG) { "CBZ already exists in Jellyfin folder: $dirname.cbz" }
                return
            }

            val newFile = seriesDir.createFile("$dirname.cbz") ?: run {
                logcat(LogPriority.WARN) { "Failed to create CBZ in Jellyfin library folder" }
                return
            }

            cbzFile.openInputStream().use { input ->
                newFile.openOutputStream().use { output ->
                    input.copyTo(output)
                }
            }

            logcat(LogPriority.INFO) { "Copied $dirname.cbz to Jellyfin library folder" }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to copy CBZ to Jellyfin library folder" }
        }
    }

    /**
     * Creates a ComicInfo.xml file inside the given directory.
     */
    private suspend fun createComicInfoFile(
        dir: UniFile,
        manga: Manga,
        chapter: Chapter,
        source: HttpSource,
    ) {
        val categories = getCategories.await(manga.id).map { it.name.trim() }.takeUnless { it.isEmpty() }
        val urls = getTracks.await(manga.id)
            .mapNotNull { track ->
                track.remoteUrl.takeUnless { url -> url.isBlank() }?.trim()
            }
            .plus(source.getChapterUrl(chapter.toSChapter()).trim())
            .distinct()

        val comicInfo = getComicInfo(
            manga,
            chapter,
            urls,
            categories,
            source.name,
            source.lang,
        )

        // Remove the old file
        dir.findFile(COMIC_INFO_FILE)?.delete()
        val comicInfoFile = dir.createFile(COMIC_INFO_FILE)
            ?: throw IOException("Could not create $COMIC_INFO_FILE")
        comicInfoFile.openOutputStream().use {
            val comicInfoString = xml.encodeToString(ComicInfo.serializer(), comicInfo)
            it.write(comicInfoString.toByteArray())
        }
    }

    /**
     * Returns true if all the queued downloads are in DOWNLOADED or ERROR state.
     */
    private fun areAllDownloadsFinished(): Boolean {
        return queueState.value.none { it.status.value <= Download.State.DOWNLOADING.value }
    }

    private fun addAllToQueue(downloads: List<Download>) {
        _queueState.update {
            downloads.forEach { download ->
                download.status = Download.State.QUEUE
            }
            store.addAll(downloads)
            it + downloads
        }
    }

    private fun removeFromQueue(download: Download) {
        _queueState.update {
            store.remove(download)
            if (download.status == Download.State.DOWNLOADING || download.status == Download.State.QUEUE) {
                download.status = Download.State.NOT_DOWNLOADED
            }
            it - download
        }
    }

    private inline fun removeFromQueueIf(predicate: (Download) -> Boolean) {
        _queueState.update { queue ->
            val downloads = queue.filter { predicate(it) }
            store.removeAll(downloads)
            downloads.forEach { download ->
                if (download.status == Download.State.DOWNLOADING || download.status == Download.State.QUEUE) {
                    download.status = Download.State.NOT_DOWNLOADED
                }
            }
            queue - downloads
        }
    }

    fun removeFromQueue(chapters: List<Chapter>) {
        val chapterIds = chapters.map { it.id }
        removeFromQueueIf { it.chapter.id in chapterIds }
    }

    fun removeFromQueue(manga: Manga) {
        removeFromQueueIf { it.manga.id == manga.id }
    }

    private fun internalClearQueue() {
        _queueState.update {
            it.forEach { download ->
                if (download.status == Download.State.DOWNLOADING || download.status == Download.State.QUEUE) {
                    download.status = Download.State.NOT_DOWNLOADED
                }
            }
            store.clear()
            emptyList()
        }
    }

    fun updateQueue(downloads: List<Download>) {
        val wasRunning = isRunning

        if (downloads.isEmpty()) {
            clearQueue()
            stop()
            return
        }

        pause()
        internalClearQueue()
        addAllToQueue(downloads)

        if (wasRunning) {
            start()
        }
    }

    companion object {
        const val TMP_DIR_SUFFIX = "_tmp"
        const val CHAPTERS_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 15
        private const val DOWNLOADS_QUEUED_WARNING_THRESHOLD = 30

        // Arbitrary minimum required space to start a download: 200 MB
        const val MIN_DISK_SPACE = 200L * 1024 * 1024

        /** Number of pages at each end of the chapter to check for credit pages. */
        private const val BOUNDARY_PAGES = 3

        /** Aspect-ratio tolerance for credit page pre-filter (5 %). */
        private const val ASPECT_RATIO_TOLERANCE = 0.05f
    }

    /**
     * A page was destroyed and could not be replaced, so the chapter is now short.
     *
     * [postProcessPages] tolerates a failed *merge* — one undecodable page should not fail a whole
     * chapter — but it is written in terms of deleting two source files and renaming a third into
     * their place, and past that delete there is nothing left to fall back on. This separates the
     * two so the tolerant path cannot swallow the unrecoverable one, and the download fails loudly
     * instead of publishing a chapter that is quietly missing pages.
     */
    private class PageLostException(message: String) : IOException(message)
}
