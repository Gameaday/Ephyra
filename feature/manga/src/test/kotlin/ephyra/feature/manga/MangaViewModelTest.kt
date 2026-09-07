package ephyra.feature.manga

import app.cash.turbine.test
import ephyra.core.download.DownloadCache
import ephyra.domain.base.BasePreferences
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.chapter.interactor.GetAvailableScanlators
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.download.model.Download
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.jellyfin.interactor.SyncJellyfin
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.interactor.GetDuplicateLibraryManga
import ephyra.domain.manga.interactor.GetExcludedScanlators
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.interactor.GetMangaWithChapters
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.service.CoverCache
import ephyra.domain.reader.service.ReaderPreferences
import ephyra.domain.source.service.SourceManager
import ephyra.feature.manga.interactor.MangaChapterInteractor
import ephyra.feature.manga.interactor.MangaInfoInteractor
import ephyra.feature.manga.interactor.MangaTrackInteractor
import ephyra.presentation.core.ui.AppInfo
import eu.kanade.tachiyomi.source.Source
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MangaViewModelTest {

    private val getManga: GetManga = mockk(relaxed = true)
    private val downloadManager: DownloadManager = mockk(relaxed = true)
    private val downloadCache: DownloadCache = mockk(relaxed = true)
    private val getMangaAndChapters: GetMangaWithChapters = mockk(relaxed = true)
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga = mockk(relaxed = true)
    private val getAvailableScanlators: GetAvailableScanlators = mockk(relaxed = true)
    private val getExcludedScanlators: GetExcludedScanlators = mockk(relaxed = true)
    private val getCategories: GetCategories = mockk(relaxed = true)
    private val sourceManager: SourceManager = mockk(relaxed = true)
    private val mangaInfoInteractor: MangaInfoInteractor = mockk(relaxed = true)
    private val mangaChapterInteractor: MangaChapterInteractor = mockk(relaxed = true)
    private val mangaTrackInteractor: MangaTrackInteractor = mockk(relaxed = true)
    private val syncJellyfin: SyncJellyfin = mockk(relaxed = true)
    private val libraryPreferences: LibraryPreferences = mockk(relaxed = true)
    private val readerPreferences: ReaderPreferences = mockk(relaxed = true)
    private val basePreferences: BasePreferences = mockk(relaxed = true)
    private val coverCache: CoverCache = mockk(relaxed = true)
    private val appInfo: AppInfo = mockk(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()

    private val testManga: Manga = mockk(relaxed = true) {
        every { id } returns 1L
        every { source } returns 100L
        every { favorite } returns false
    }

    private val testSource: Source = mockk(relaxed = true) {
        every { id } returns 100L
    }

    private val chapter1: Chapter = mockk(relaxed = true) {
        every { id } returns 10L
        every { mangaId } returns 1L
    }
    private val chapter2: Chapter = mockk(relaxed = true) {
        every { id } returns 20L
        every { mangaId } returns 1L
    }

    private lateinit var viewModel: MangaViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        coEvery { getManga.subscribe(1L) } returns flowOf(testManga)
        every { sourceManager.getOrStub(100L) } returns testSource
        coEvery { getMangaAndChapters.subscribe(1L) } returns flowOf(testManga to listOf(chapter1, chapter2))
        every { mangaTrackInteractor.loggedInTrackersFlow() } returns flowOf(emptyList())
        every { downloadCache.changes } returns MutableSharedFlow<Unit>()
        every { downloadManager.queueState } returns MutableStateFlow<List<Download>>(emptyList())

        viewModel = MangaViewModel(
            getManga = getManga,
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
            libraryPreferences = libraryPreferences,
            readerPreferences = readerPreferences,
            basePreferences = basePreferences,
            coverCache = coverCache,
            appInfo = appInfo,
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading and init transitions to Success`() = runTest {
        viewModel.state.test {
            assertEquals(MangaViewModel.State.Loading, awaitItem())

            viewModel.init(1L, false)

            val success = awaitItem() as MangaViewModel.State.Success
            assertEquals(testManga, success.manga)
            assertEquals(testSource, success.source)
            assertFalse(success.isAnySelected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ToggleSelection and ClearSelection modify selectedChapterIds`() = runTest {
        viewModel.init(1L, false)

        viewModel.state.test {
            val success = awaitItem() as MangaViewModel.State.Success
            assertTrue(success.selectedChapterIds.isEmpty())

            val item = ChapterList.Item(chapter = chapter1)
            viewModel.onEvent(MangaScreenEvent.ToggleSelection(item, selected = true))

            val selectedState = awaitItem() as MangaViewModel.State.Success
            assertTrue(10L in selectedState.selectedChapterIds)
            assertTrue(selectedState.isAnySelected)

            viewModel.onEvent(MangaScreenEvent.ClearSelection)

            val clearedState = awaitItem() as MangaViewModel.State.Success
            assertTrue(clearedState.selectedChapterIds.isEmpty())
            assertFalse(clearedState.isAnySelected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Dialog events show and dismiss dialog in state`() = runTest {
        viewModel.init(1L, false)

        viewModel.state.test {
            val initial = awaitItem() as MangaViewModel.State.Success
            assertNull(initial.dialog)

            viewModel.onEvent(MangaScreenEvent.ShowSettingsDialog)

            val sheetState = awaitItem() as MangaViewModel.State.Success
            assertEquals(MangaViewModel.Dialog.SettingsSheet, sheetState.dialog)

            viewModel.onEvent(MangaScreenEvent.DismissDialog)

            val dismissedState = awaitItem() as MangaViewModel.State.Success
            assertNull(dismissedState.dialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ShowShareRecommendationDialog populates ShareRecommendation dialog in state`() = runTest {
        viewModel.init(1L, false)

        viewModel.state.test {
            val initial = awaitItem() as MangaViewModel.State.Success
            assertNull(initial.dialog)

            viewModel.onEvent(MangaScreenEvent.ShowShareRecommendationDialog)

            val shareDialogState = awaitItem() as MangaViewModel.State.Success
            assertTrue(shareDialogState.dialog is MangaViewModel.Dialog.ShareRecommendation)
            val dialog = shareDialogState.dialog as MangaViewModel.Dialog.ShareRecommendation
            assertEquals(testManga, dialog.manga)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleFavorite calls mangaInfoInteractor updateFavorite`() = runTest {
        coEvery { mangaInfoInteractor.updateFavorite(1L, true) } returns true

        viewModel.init(1L, false)
        viewModel.toggleFavorite()

        coVerify { mangaInfoInteractor.updateFavorite(1L, true) }
    }
}
