package ephyra.feature.manga.interactor

import ephyra.core.common.extension.runExtensionCall
import ephyra.core.common.preference.TriState
import ephyra.core.common.util.system.logcat
import ephyra.domain.chapter.interactor.FilterChaptersForDownload
import ephyra.domain.chapter.interactor.SetMangaDefaultChapterFlags
import ephyra.domain.chapter.interactor.SetReadStatus
import ephyra.domain.chapter.interactor.SyncChaptersWithSource
import ephyra.domain.chapter.interactor.UpdateChapter
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.chapter.model.ChapterUpdate
import ephyra.domain.chapter.model.toSChapter
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.interactor.SetMangaChapterFlags
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.toSManga
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import logcat.LogPriority
import javax.inject.Inject

class MangaChapterInteractor @Inject constructor(
    private val setMangaChapterFlags: SetMangaChapterFlags,
    private val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags,
    private val setReadStatus: SetReadStatus,
    private val updateChapter: UpdateChapter,
    private val updateManga: UpdateManga,
    private val libraryPreferences: LibraryPreferences,
    private val filterChaptersForDownload: FilterChaptersForDownload,
    private val syncChaptersWithSource: SyncChaptersWithSource,
    private val downloadManager: DownloadManager,
) {
    suspend fun setUnreadFilter(manga: Manga, state: TriState) {
        val flag = when (state) {
            TriState.DISABLED -> Manga.SHOW_ALL
            TriState.ENABLED_IS -> Manga.CHAPTER_SHOW_UNREAD
            TriState.ENABLED_NOT -> Manga.CHAPTER_SHOW_READ
        }
        setMangaChapterFlags.awaitSetUnreadFilter(manga, flag)
    }

    suspend fun setDownloadedFilter(manga: Manga, state: TriState) {
        val flag = when (state) {
            TriState.DISABLED -> Manga.SHOW_ALL
            TriState.ENABLED_IS -> Manga.CHAPTER_SHOW_DOWNLOADED
            TriState.ENABLED_NOT -> Manga.CHAPTER_SHOW_NOT_DOWNLOADED
        }
        setMangaChapterFlags.awaitSetDownloadedFilter(manga, flag)
    }

    suspend fun setBookmarkedFilter(manga: Manga, state: TriState) {
        val flag = when (state) {
            TriState.DISABLED -> Manga.SHOW_ALL
            TriState.ENABLED_IS -> Manga.CHAPTER_SHOW_BOOKMARKED
            TriState.ENABLED_NOT -> Manga.CHAPTER_SHOW_NOT_BOOKMARKED
        }
        setMangaChapterFlags.awaitSetBookmarkFilter(manga, flag)
    }

    suspend fun setDisplayMode(manga: Manga, mode: Long) {
        setMangaChapterFlags.awaitSetDisplayMode(manga, mode)
    }

    suspend fun setSorting(manga: Manga, sort: Long) {
        setMangaChapterFlags.awaitSetSortingModeOrFlipOrder(manga, sort)
    }

    suspend fun bookmarkChapters(chapters: List<Chapter>, bookmarked: Boolean) {
        chapters
            .filterNot { it.bookmark == bookmarked }
            .map { ChapterUpdate(id = it.id, bookmark = bookmarked) }
            .let { updateChapter.awaitAll(it) }
    }

    suspend fun markChaptersRead(chapters: List<Chapter>, read: Boolean) {
        setReadStatus.await(
            read = read,
            chapters = chapters.toTypedArray(),
        )
    }

    fun deleteChapters(chapters: List<Chapter>, manga: Manga, source: Source) {
        downloadManager.deleteChapters(chapters, manga, source)
    }

    fun downloadChapters(chapters: List<Chapter>, manga: Manga) {
        downloadManager.downloadChapters(manga, chapters)
    }

    suspend fun filterChaptersForDownload(manga: Manga, chapters: List<Chapter>): List<Chapter> {
        return filterChaptersForDownload.await(manga, chapters)
    }

    suspend fun syncChaptersWithSource(
        chapters: List<Chapter>,
        manga: Manga,
        source: Source,
        manualFetch: Boolean,
    ): List<Chapter> {
        val sManga = manga.toSManga()
        val sChapters = chapters.map { it.toSChapter() }
        val update = runCatching {
            runExtensionCall(sourceName = source.name) {
                source.getMangaUpdate(
                    manga = sManga,
                    chapters = sChapters,
                    fetchDetails = true,
                    fetchChapters = true,
                )
            }
        }.onSuccess { updateResult ->
            runCatching {
                updateManga.awaitUpdateFromSource(manga, updateResult.manga, manualFetch = manualFetch)
            }.onFailure { e ->
                logcat(LogPriority.WARN, e) {
                    "Failed to update manga details from source '${source.name}'"
                }
            }
        }.getOrElse { e ->
            logcat(LogPriority.ERROR, e) { "Failed to fetch chapter update from source '${source.name}'" }
            if (manualFetch) throw e
            // Background refresh: fall back to the currently known local chapters so an
            // offline/broken source never wipes the chapter list.
            SMangaUpdate(sManga, sChapters)
        }
        return syncChaptersWithSource.await(
            update.chapters,
            manga,
            source,
            manualFetch,
        )
    }

    suspend fun setCurrentSettingsAsDefault(manga: Manga, applyToExisting: Boolean) {
        libraryPreferences.setChapterSettingsDefault(manga)
        if (applyToExisting) {
            setMangaDefaultChapterFlags.awaitAll()
        }
    }

    suspend fun resetToDefaultSettings(manga: Manga) {
        setMangaDefaultChapterFlags.await(manga)
    }
}
