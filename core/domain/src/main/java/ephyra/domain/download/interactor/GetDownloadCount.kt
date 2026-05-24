package ephyra.domain.download.interactor

import ephyra.domain.download.service.DownloadManager
import ephyra.domain.manga.model.Manga

/**
 * Domain use-case wrapping DownloadManager to retrieve download counts.
 *
 * This prevents the ViewModel from directly depending on the data-layer DownloadManager
 * interface, strictly enforcing Clean Architecture boundaries.
 */
class GetDownloadCount(
    private val downloadManager: DownloadManager,
) {
    /**
     * Returns the count of downloaded chapters for the given manga.
     */
    suspend operator fun invoke(manga: Manga): Long {
        return downloadManager.getDownloadCount(manga).toLong()
    }

    /**
     * Checks whether a specific chapter is already downloaded.
     */
    fun isChapterDownloaded(
        chapterName: String,
        chapterScanlator: String?,
        chapterUrl: String,
        mangaTitle: String,
        sourceId: Long,
    ): Boolean {
        return downloadManager.isChapterDownloaded(
            chapterName = chapterName,
            chapterScanlator = chapterScanlator,
            chapterUrl = chapterUrl,
            mangaTitle = mangaTitle,
            sourceId = sourceId,
        )
    }
}
