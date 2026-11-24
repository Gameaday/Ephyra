package ephyra.domain.download.service

import ephyra.domain.chapter.model.Chapter
import ephyra.domain.download.model.Download
import ephyra.domain.manga.model.Manga
import ephyra.source.api.SourceGateway
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface DownloadManager {
    val isRunning: Boolean
    val queueState: StateFlow<List<Download>>
    val isDownloaderRunning: Flow<Boolean>

    fun startDownloads()
    fun pauseDownloads()
    fun clearQueue()
    fun getQueuedDownloadOrNull(chapterId: Long): Download?
    suspend fun startDownloadNow(chapterId: Long)
    fun reorderQueue(downloads: List<Download>)
    fun downloadChapters(manga: Manga, chapters: List<Chapter>, autoStart: Boolean = true)
    fun addDownloadsToStartOfQueue(downloads: List<Download>)
    fun buildPageList(source: SourceGateway, manga: Manga, chapter: Chapter): List<Page>
    suspend fun awaitCacheReady()
    fun isChapterDownloaded(
        chapterName: String,
        chapterScanlator: String?,
        chapterUrl: String,
        mangaTitle: String,
        sourceId: Long,
    ): Boolean

    fun getDownloadCount(): Int
    fun getDownloadCount(manga: Manga): Int
    fun cancelQueuedDownloads(downloads: List<Download>)
    fun deleteChapters(chapters: List<Chapter>, manga: Manga, source: SourceGateway)
    fun deleteManga(manga: Manga, source: SourceGateway, removeQueued: Boolean = true)
    suspend fun enqueueChaptersToDelete(chapters: List<Chapter>, manga: Manga)
    suspend fun deletePendingChapters()
    fun renameSource(oldSource: SourceGateway, newSource: SourceGateway)
    suspend fun renameManga(manga: Manga, newTitle: String)
    suspend fun renameChapter(source: SourceGateway, manga: Manga, oldChapter: Chapter, newChapter: Chapter)
    fun statusFlow(): Flow<Download>
    fun progressFlow(): Flow<Download>
}
