package ephyra.domain.download.interactor

import ephyra.domain.chapter.model.Chapter
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.manga.model.Manga

/**
 * Domain use-case wrapping DownloadManager.downloadChapters and related operations.
 *
 * Prevents ViewModels from directly depending on the data-layer DownloadManager interface,
 * enforcing Clean Architecture separation.
 */
class DownloadChapters(
    private val downloadManager: DownloadManager,
) {
    /**
     * Queues chapters for download.
     */
    suspend operator fun invoke(manga: Manga, chapters: List<Chapter>, autoStart: Boolean = true) {
        downloadManager.downloadChapters(manga, chapters, autoStart)
    }

    /**
     * Returns whether the download cache has been initialized from disk.
     */
    suspend fun awaitCacheReady() {
        downloadManager.awaitCacheReady()
    }

    /**
     * Returns the queued download for a chapter, or null if not queued.
     */
    fun getQueuedDownloadOrNull(chapterId: Long) = downloadManager.getQueuedDownloadOrNull(chapterId)
}
