package ephyra.domain.download.service

import ephyra.domain.chapter.model.Chapter
import ephyra.domain.download.model.Download
import ephyra.domain.manga.model.Manga
import eu.kanade.tachiyomi.source.Source
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
    fun buildPageList(source: Source, manga: Manga, chapter: Chapter): List<Page>
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
    fun deleteChapters(chapters: List<Chapter>, manga: Manga, source: Source)
    fun deleteManga(manga: Manga, source: Source, removeQueued: Boolean = true)
    suspend fun enqueueChaptersToDelete(chapters: List<Chapter>, manga: Manga)
    fun deletePendingChapters()
    fun renameSource(oldSource: Source, newSource: Source)
    suspend fun renameManga(manga: Manga, newTitle: String)
    suspend fun renameChapter(source: Source, manga: Manga, oldChapter: Chapter, newChapter: Chapter)
    fun statusFlow(): Flow<Download>
    fun progressFlow(): Flow<Download>
}
