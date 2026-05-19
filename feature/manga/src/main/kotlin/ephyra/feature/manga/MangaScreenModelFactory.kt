package ephyra.feature.manga

import android.content.Context
import androidx.lifecycle.Lifecycle
import ephyra.domain.base.BasePreferences
import ephyra.domain.ui.UiPreferences
import ephyra.domain.library.service.LibraryPreferences
import ephyra.feature.reader.setting.ReaderPreferences
import ephyra.core.download.DownloadManager
import ephyra.core.download.DownloadCache
import ephyra.domain.manga.interactor.GetMangaWithChapters
import ephyra.domain.manga.interactor.GetDuplicateLibraryManga
import ephyra.domain.chapter.interactor.GetAvailableScanlators
import ephyra.domain.manga.interactor.GetExcludedScanlators
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.source.service.SourceManager
import ephyra.feature.manga.interactor.MangaInfoInteractor
import ephyra.feature.manga.interactor.MangaChapterInteractor
import ephyra.feature.manga.interactor.MangaTrackInteractor
import ephyra.domain.jellyfin.interactor.SyncJellyfin

class MangaScreenModelFactory(
    private val context: Context,
    private val basePreferences: BasePreferences,
    private val uiPreferences: UiPreferences,
    private val libraryPreferences: LibraryPreferences,
    private val readerPreferences: ReaderPreferences,
    private val downloadManager: DownloadManager,
    private val downloadCache: DownloadCache,
    private val getMangaAndChapters: GetMangaWithChapters,
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga,
    private val getAvailableScanlators: GetAvailableScanlators,
    private val getExcludedScanlators: GetExcludedScanlators,
    private val getCategories: GetCategories,
    private val sourceManager: SourceManager,
    private val mangaInfoInteractor: MangaInfoInteractor,
    private val mangaChapterInteractor: MangaChapterInteractor,
    private val mangaTrackInteractor: MangaTrackInteractor,
    private val syncJellyfin: SyncJellyfin,
) {
    fun create(lifecycle: Lifecycle, mangaId: Long, isFromSource: Boolean): MangaScreenModel {
        return MangaScreenModel(
            context = context,
            lifecycle = lifecycle,
            mangaId = mangaId,
            isFromSource = isFromSource,
            basePreferences = basePreferences,
            uiPreferences = uiPreferences,
            libraryPreferences = libraryPreferences,
            readerPreferences = readerPreferences,
            downloadManager = downloadManager,
            downloadCache = downloadCache,
            getMangaAndChapters = getMangaAndChapters,
            getDuplicateLibraryManga = getDuplicateLibraryManga,
            getAvailableScanlators = getAvailableScanlators,
            getExcludedScanlators = getExcludedScanlators,
            getCategories = getCategories,
            sourceManager = sourceManager,
            mangaInfoInteractor = mangaInfoInteractor,
            mangaChapterInteractor = mangaChapterInteractor,
            mangaTrackInteractor = mangaTrackInteractor,
            syncJellyfin = syncJellyfin,
        )
    }
}
