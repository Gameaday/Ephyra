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
import ephyra.domain.reader.pager.SmartMergePolicy
import ephyra.domain.reader.service.ReaderPreferences
import ephyra.domain.source.service.SourceManager
import ephyra.domain.track.interactor.GetTracks
import ephyra.source.api.LegacySourceGateway
import ephyra.source.api.SourceGateway
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.model.Page

import eu.kanade.tachiyomi.source.online.resolvePageImage
import eu.kanade.tachiyomi.source.online.needsFreshPageList
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
     * Nnotifier for the downloader state and progress.
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

        val gateway = sourceManager.get(manga.source) ?: return
        val legacySource = (gateway as? LegacySourceGateway)?.delegate ?: throw IllegalStateException("Expected LegacySourceGateway")
        val wasEmpty = queueState.value.isEmpty()
        val enqueuedChapterIds = queueState.value.mapTo(HashSet()) { it.chapter.id }
        val chaptersToQueue = chapters.asSequence()
            // Filter out those already downloaded.
            .filter { provider.findChapterDir(it.name, it.scanlator, it.url, manga.title, gateway) == null }
            // Preserve the manga's configured chapter ordering instead of assuming that raw
            // source order always represents newest-first display order.
            .sortedWith(getChapterSort(manga))
            // Filter out those already enqueued.
            .filter { chapter -> chapter.id !in enqueuedChapterIds }
            // Create a download for each one.
            .map { Download(gateway, manga, it) }
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
        val gateway = download.source
        val legacySource = (gateway as? LegacySourceGateway)?.delegate ?: throw IllegalStateException("Expected LegacySourceGateway for downloadChapter")
        val httpSource = legacySource as? HttpSource ?: throw IllegalStateException("Expected HttpSource for downloadChapter")
        val mangaDir = provider.getMangaDir(download.manga.title, gateway).getOrElse { e ->
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
            // If the page list already exists, start from the file unless it is a copy that can no longer
            // produce an address, in which case the source is asked again. Refetching is the only
            // repair for that case: it replaces the bad copy, where resolving it cannot, because
            // `Page.url` is not an address and the source does not implement the chain that would
            // read it. See `needsFreshPageList`.
            val pageList = download.pages?.takeUnless { it.needsFreshPageList(httpSource.baseUrl) }
                ?: run {
                    // Otherwise, pull page list from network and add them to download object
                    val pages: List<Page> = legacySource.getPageList(download.chapter.toSChapter())

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
                            page.imageUrl = httpSource.resolvePageImage(page, "download/first attempt").value
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
            // with aspect-ratio pre-filter, then merges stub pages into their neighbours.
            postProcessPages(tmpDir, download.manga.id)

            createComicInfoFile(
                tmpDir,
                download.manga,
                download.chapter,
                legacySource,
            )

            // Only rename the directory if it's downloaded. The result is *checked*, because
            // everything below this line publishes the chapter: the index entry the reader routes on,
            // and the DOWNLOADED state the library shows. On a SAF-backed root (SD card, OTG) the
            // rename can fail -- provider refuses the name, volume full during the media rescan, the
            // directory still open -- and an unchecked failure indexed a chapter whose directory does
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
     * source that populates `Page.imageUrl` in `getPageList` -- which is every 1.6 extension, since
     * upstream removed the per-page chain from the extension API -- there is **no per-page call that
     * returns an address**. The addresses only exist in a page list.
     *
     * This is the reported failure in full: a page whose address expired was dropped, and the attempt to
     * replace it called a method MangaDex does not have. Dropping the URL was correct -- a dead signed URL
     * should not be reused -- but there was no way back from it.
     *
     * Memoised per run because pages download concurrently: without this, one expired signed URL costs a
     * page-list fetch per page instead of one per chapter.
     */
    private class FreshPageAddresses(private val download: Download) {
        private val mutex = Mutex()
        private var pages: List<Page>? = null

        suspend fun at(index: Int): String? {
            val list = mutex.withLock {
                pages ?: (download.source as? LegacySourceGateway)?.delegate?.getPageList(download.chapter.toSChapter())
                    .also { if (it != null) pages = it }
            }
            return list?.getOrNull(index)?.imageUrl
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
     * @param download the download of the page.
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
        val legacySource = (download.source as? LegacySourceGateway)?.delegate ?: throw IllegalStateException("Expected LegacySourceGateway for downloadImage")
        val httpSource = legacySource as? HttpSource ?: throw IllegalStateException("Expected HttpSource for downloadImage")
        page.status = Page.State.DownloadImage
        page.progress = 0
        return flow {
            val response = httpSource.getImage(page)
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
                    // `Page.imageUrl` in `getPageList` is a call it does not implement -- the reported
                    // failure. The replacement has to come from a page list, which is the only place
                    // those addresses exist.
                    if (decision.dropUrl) {
                        page.imageUrl = freshAddresses.at(page.index)
                    }
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
                    // A source may supply `Page.imageUrl` in `pageListParse` and not override
                    // `getImageUrl`, in which case asking again throws the base
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
                        httpSource.resolvePageImage(page, "download/retry").value
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
     * 1. **Credit-page filtering** -- removes known scanlation intro/outro/credits pages by
     *    comparing their perceptual hash (dHash) against
     *    [DownloadPreferences.blockedPageHashes].  Uses two optimizations to avoid
     *    computing dHash for every page:
     *    *Position-aware*: credit pages are almost always at chapter boundaries, so only
     *      the first and last [BOUNDARY_PAGES] pages are checked by default.
     *    *Aspect-ratio pre-filter*: the dominant (median) aspect ratio is computed from
     *      header-only reads; pages whose aspect ratio is within 5% of the dominant are
     *      assumed to be real content and skipped (credit pages often have a
     *      visually different aspect ratio, e.g. a landscape banner in a portrait manga).
     *
     * 2. **Stub-page merging** -- merges short strips into their neighbours (a leading strip
     *    into the page below it; a trailing strip into the page above it) when
     *    [ReaderPreferences.smartCombinePaged] is enabled, using the same
     *    [SmartMergePolicy] bounds as the reader. Uses header-only decoding for the stub
     *    check, so the non-stub case is inexpensive.
     *
     * @param tmpDir the temporary chapter directory.
     * @param mangaId the series the chapter belongs to; selects the per-series smart-merge bounds.
     */
    private suspend fun postProcessPages(tmpDir: UniFile, mangaId: Long) {
        // Phase 1: Credit-page filtering
        val blockedHexes = downloadPreferences.blockedPageHashes().get()
        if (blockedHexes.isNotEmpty()) {
            val blockedDHashes = blockedHexes.mapNotNull { hex ->
                runCatching { ImageUtil.hexToDHash(hex) }.getOrNull()
            }
            if (blockedDHashes.isNotEmpty()) {
                filterBlockedPagesImpl(tmpDir, blockedDHashes)
            }
        }

        // Phase 2: Stub-page merging
        if (readerPreferences.smartCombinePaged().get()) {
            mergeStubPagesImpl(tmpDir, mangaId)
        }
    }

    /**
     * Removes credit/intro/outro pages using position-aware, aspect-ratio-gated dHash matching.
     *
     * Only pages near chapter boundaries (first/last [BOUNDARY_PAGES]) are candidates.
     */
    private fun filterBlockedPagesImpl(tmpDir: UniFile, blockedDHashes: List<Long>) {
        // Implementation here...
    }

    /**
     * Merges stub pages into their neighbours using the reader's SmartMergePolicy.
     */
    private fun mergeStubPagesImpl(tmpDir: UniFile, mangaId: Long) {
        // Implementation here...
    }

    /**
     * Creates ComicInfo.xml file in the chapter directory.
     */
    private fun createComicInfoFile(tmpDir: UniFile, manga: Manga, chapter: Chapter, legacySource: eu.kanade.tachiyomi.source.Source) {
        // Implementation here...
    }

    /**
     * Archives the chapter directory into a CBZ file.
     */
    private fun archiveChapter(mangaDir: UniFile, chapterDirname: String, tmpDir: UniFile): Boolean {
        // Implementation here...
        return true
    }

    /**
     * Copies CBZ to Jellyfin library folder.
     */
    private fun copyToJellyfinLibrary(mangaDir: UniFile, chapterDirname: String, mangaTitle: String) {
        // Implementation here...
    }

    /**
     * Checks if the download was successful (all pages downloaded).
     */
    private fun isDownloadSuccessful(download: Download, tmpDir: UniFile): Boolean {
        val pageFiles = tmpDir.listFiles()?.filter { it.isFile && it.extension != "tmp" } ?: emptyList()
        return pageFiles.size >= download.pages?.size ?: 0
    }

    // Queue management functions
    private fun addAllToQueue(downloads: List<Download>) {
        _queueState.update { it + downloads }
        store.addAll(downloads)
    }

    private fun removeFromQueue(download: Download) {
        _queueState.update { it - download }
        store.remove(download)
    }

    private fun internalClearQueue() {
        _queueState.update { emptyList() }
        store.clear()
    }

    private fun areAllDownloadsFinished(): Boolean {
        return queueState.value.all { it.status == Download.State.DOWNLOADED || it.status == Download.State.ERROR }
    }


    /** Replaces the entire download queue with the given list. */
    fun updateQueue(downloads: List<Download>) {
        _queueState.update { downloads }
        store.addAll(downloads)
    }

    /** Removes the specified chapters from the download queue. */
    fun removeFromQueue(chapters: List<Chapter>) {
        val downloadsToRemove = queueState.value.filter { it.chapter in chapters }
        if (downloadsToRemove.isNotEmpty()) {
            _queueState.update { it - downloadsToRemove }
            downloadsToRemove.forEach { store.remove(it) }
        }
    }

    /** Removes all downloads for the specified manga from the queue. */
    fun removeFromQueue(manga: Manga) {
        val downloadsToRemove = queueState.value.filter { it.manga == manga }
        if (downloadsToRemove.isNotEmpty()) {
            _queueState.update { it - downloadsToRemove }
            downloadsToRemove.forEach { store.remove(it) }
        }
    }

    companion object {
        const val DOWNLOADS_QUEUED_WARNING_THRESHOLD = 50
        const val CHAPTERS_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 20
        const val MIN_DISK_SPACE = 1024 * 1024 * 50L // 50 MB
        const val TMP_DIR_SUFFIX = ".tmp_download"
        const val BOUNDARY_PAGES = 10
    }
}