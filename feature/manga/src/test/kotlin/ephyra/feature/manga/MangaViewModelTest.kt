package ephyra.feature.manga

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import ephyra.core.common.preference.Preference
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
        every { sortDescending() } returns true
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
        every { sourceManager.isInitialized } returns MutableStateFlow(true)
        every { sourceManager.getOrStub(100L) } returns testSource
        coEvery { getMangaAndChapters.subscribe(1L) } returns flowOf(testManga to listOf(chapter1, chapter2))
        every { mangaTrackInteractor.loggedInTrackersFlow() } returns flowOf(emptyList())
        every { downloadCache.changes } returns MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }
        every { downloadManager.queueState } returns MutableStateFlow<List<Download>>(emptyList())

        val startPref: Preference<LibraryPreferences.ChapterSwipeAction> = mockk(relaxed = true) {
            every { changes() } returns flowOf(LibraryPreferences.ChapterSwipeAction.ToggleRead)
        }
        val endPref: Preference<LibraryPreferences.ChapterSwipeAction> = mockk(relaxed = true) {
            every { changes() } returns flowOf(LibraryPreferences.ChapterSwipeAction.ToggleBookmark)
        }
        every { libraryPreferences.swipeToStartAction() } returns startPref
        every { libraryPreferences.swipeToEndAction() } returns endPref

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
    fun `SavedStateHandle with mangaId auto-initializes state to Success`() = runTest {
        val vm = MangaViewModel(
            savedStateHandle = SavedStateHandle(mapOf("mangaId" to 1L, "fromSource" to false)),
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

        vm.state.test {
            val success = awaitItem() as MangaViewModel.State.Success
            assertEquals(testManga, success.manga)
            assertEquals(testSource, success.source)
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

    @Test
    fun `FetchAllFromSource triggers syncChaptersWithSource on mangaChapterInteractor`() = runTest {
        viewModel.init(1L, false)
        viewModel.onEvent(MangaScreenEvent.FetchAllFromSource(manualFetch = true))

        coVerify {
            mangaChapterInteractor.syncChaptersWithSource(
                chapters = any(),
                manga = any(),
                source = any(),
                manualFetch = true,
            )
        }
    }

    @Test
    fun `getNextUnreadChapter returns oldest unread chapter when nothing in progress`() = runTest {
        val ch1 = Chapter.create().copy(id = 10L, mangaId = 1L, chapterNumber = 1.0, read = false, lastPageRead = 0)
        val ch2 = Chapter.create().copy(id = 20L, mangaId = 1L, chapterNumber = 2.0, read = false, lastPageRead = 0)
        coEvery { getMangaAndChapters.subscribe(1L) } returns flowOf(testManga to listOf(ch2, ch1))

        viewModel.state.test {
            assertEquals(MangaViewModel.State.Loading, awaitItem())
            viewModel.init(1L, false)
            var success: MangaViewModel.State.Success? = null
            while (success == null || success.chapterListItems.isEmpty()) {
                val item = awaitItem()
                if (item is MangaViewModel.State.Success) {
                    success = item
                }
            }
            assertEquals(ch1.id, viewModel.getNextUnreadChapter()?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getNextUnreadChapter prioritizes chapter currently in progress`() = runTest {
        val ch1 = Chapter.create().copy(id = 10L, mangaId = 1L, chapterNumber = 1.0, read = true, lastPageRead = 10)
        val ch2 = Chapter.create().copy(id = 20L, mangaId = 1L, chapterNumber = 2.0, read = false, lastPageRead = 5)
        val ch3 = Chapter.create().copy(id = 30L, mangaId = 1L, chapterNumber = 3.0, read = false, lastPageRead = 0)
        coEvery { getMangaAndChapters.subscribe(1L) } returns flowOf(testManga to listOf(ch3, ch2, ch1))

        viewModel.state.test {
            assertEquals(MangaViewModel.State.Loading, awaitItem())
            viewModel.init(1L, false)
            var success: MangaViewModel.State.Success? = null
            while (success == null || success.chapterListItems.isEmpty()) {
                val item = awaitItem()
                if (item is MangaViewModel.State.Success) {
                    success = item
                }
            }
            assertEquals(ch2.id, viewModel.getNextUnreadChapter()?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `chapter gap creates MissingCount items with unique negative IDs`() = runTest {
        val ch1 = Chapter.create().copy(id = 10L, mangaId = 1L, chapterNumber = 1.0)
        val ch5 = Chapter.create().copy(id = 50L, mangaId = 1L, chapterNumber = 5.0)
        val ch10 = Chapter.create().copy(id = 100L, mangaId = 1L, chapterNumber = 10.0)
        coEvery { getMangaAndChapters.subscribe(1L) } returns flowOf(testManga to listOf(ch10, ch5, ch1))

        viewModel.state.test {
            assertEquals(MangaViewModel.State.Loading, awaitItem())
            viewModel.init(1L, false)
            var success: MangaViewModel.State.Success? = null
            while (success == null || success.chapterListItems.isEmpty()) {
                val item = awaitItem()
                if (item is MangaViewModel.State.Success) {
                    success = item
                }
            }
            val missingCountItems = success.chapterListItems.filterIsInstance<ChapterList.MissingCount>()
            assertEquals(2, missingCountItems.size)
            assertTrue(missingCountItems.all { it.id < 0 })
            assertEquals(missingCountItems.map { it.id }.toSet().size, missingCountItems.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
