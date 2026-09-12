package ephyra.feature.library

import app.cash.turbine.test
import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.TriState
import ephyra.core.download.DownloadCache
import ephyra.domain.base.BasePreferences
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.category.interactor.SetMangaCategories
import ephyra.domain.category.model.Category
import ephyra.domain.chapter.interactor.GetBookmarkedChaptersByMangaId
import ephyra.domain.chapter.interactor.GetChaptersByMangaId
import ephyra.domain.chapter.interactor.SetReadStatus
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.history.interactor.GetNextChapters
import ephyra.domain.library.model.LibraryManga
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.library.service.LibraryUpdateScheduler
import ephyra.domain.manga.interactor.GetLibraryManga
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.service.CoverCache
import ephyra.domain.source.service.SourceManager
import ephyra.domain.track.interactor.GetTracksPerManga
import ephyra.domain.track.service.TrackerManager
import ephyra.feature.library.presentation.components.LibraryFilterType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val getLibraryManga: GetLibraryManga = mockk(relaxed = true)
    private val getCategories: GetCategories = mockk(relaxed = true)
    private val getTracksPerManga: GetTracksPerManga = mockk(relaxed = true)
    private val getNextChapters: GetNextChapters = mockk(relaxed = true)
    private val getChaptersByMangaId: GetChaptersByMangaId = mockk(relaxed = true)
    private val getBookmarkedChaptersByMangaId: GetBookmarkedChaptersByMangaId = mockk(relaxed = true)
    private val setReadStatus: SetReadStatus = mockk(relaxed = true)
    private val updateManga: UpdateManga = mockk(relaxed = true)
    private val setMangaCategories: SetMangaCategories = mockk(relaxed = true)
    private val preferences: BasePreferences = mockk(relaxed = true)
    private val libraryPreferences: LibraryPreferences = mockk(relaxed = true)
    private val coverCache: CoverCache = mockk(relaxed = true)
    private val sourceManager: SourceManager = mockk(relaxed = true)
    private val downloadManager: DownloadManager = mockk(relaxed = true)
    private val downloadCache: DownloadCache = mockk(relaxed = true)
    private val trackerManager: TrackerManager = mockk(relaxed = true)
    private val libraryUpdateScheduler: LibraryUpdateScheduler = mockk(relaxed = true)

    private val unreadPref: Preference<TriState> = mockk(relaxed = true)
    private val downloadedPref: Preference<TriState> = mockk(relaxed = true)
    private val startedPref: Preference<TriState> = mockk(relaxed = true)
    private val bookmarkedPref: Preference<TriState> = mockk(relaxed = true)
    private val completedPref: Preference<TriState> = mockk(relaxed = true)
    private val sourceHealthPref: Preference<TriState> = mockk(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // Mock default flows required by LibraryViewModel.init
        coEvery { libraryPreferences.lastUsedCategory().get() } returns 0
        every { getCategories.subscribe() } returns flowOf(emptyList())
        every { getLibraryManga.subscribe() } returns flowOf(emptyList())
        every { getTracksPerManga.subscribe() } returns flowOf(emptyMap())
        every { trackerManager.loggedInTrackersFlow() } returns flowOf(emptyList())
        every { downloadCache.changes } returns
            kotlinx.coroutines.flow.MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

        // Badge & restriction preferences
        every { libraryPreferences.downloadBadge().changes() } returns flowOf(false)
        every { libraryPreferences.unreadBadge().changes() } returns flowOf(false)
        every { libraryPreferences.localBadge().changes() } returns flowOf(false)
        every { libraryPreferences.languageBadge().changes() } returns flowOf(false)
        every { libraryPreferences.autoUpdateMangaRestrictions().changes() } returns flowOf(emptySet())
        every { preferences.downloadedOnly().changes() } returns flowOf(false)

        // Filter preferences
        every { libraryPreferences.filterDownloaded() } returns downloadedPref
        every { libraryPreferences.filterUnread() } returns unreadPref
        every { libraryPreferences.filterStarted() } returns startedPref
        every { libraryPreferences.filterBookmarked() } returns bookmarkedPref
        every { libraryPreferences.filterCompleted() } returns completedPref
        every { libraryPreferences.filterIntervalCustom().changes() } returns flowOf(TriState.DISABLED)
        every { libraryPreferences.filterSourceHealthDead() } returns sourceHealthPref
        every { libraryPreferences.filterContentTypeManga().changes() } returns flowOf(TriState.DISABLED)

        every { downloadedPref.changes() } returns flowOf(TriState.DISABLED)
        every { unreadPref.changes() } returns flowOf(TriState.DISABLED)
        every { startedPref.changes() } returns flowOf(TriState.DISABLED)
        every { bookmarkedPref.changes() } returns flowOf(TriState.DISABLED)
        every { completedPref.changes() } returns flowOf(TriState.DISABLED)
        every { sourceHealthPref.changes() } returns flowOf(TriState.DISABLED)

        coEvery { downloadedPref.get() } returns TriState.DISABLED
        coEvery { unreadPref.get() } returns TriState.DISABLED
        coEvery { startedPref.get() } returns TriState.DISABLED
        coEvery { bookmarkedPref.get() } returns TriState.DISABLED
        coEvery { completedPref.get() } returns TriState.DISABLED
        coEvery { sourceHealthPref.get() } returns TriState.DISABLED

        // Display mode preferences
        every { libraryPreferences.categoryTabs().changes() } returns flowOf(false)
        every { libraryPreferences.categoryNumberOfItems().changes() } returns flowOf(false)
        every { libraryPreferences.showContinueReadingButton().changes() } returns flowOf(false)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): LibraryViewModel {
        return LibraryViewModel(
            getLibraryManga = getLibraryManga,
            getCategories = getCategories,
            getTracksPerManga = getTracksPerManga,
            getNextChapters = getNextChapters,
            getChaptersByMangaId = getChaptersByMangaId,
            getBookmarkedChaptersByMangaId = getBookmarkedChaptersByMangaId,
            setReadStatus = setReadStatus,
            updateManga = updateManga,
            setMangaCategories = setMangaCategories,
            preferences = preferences,
            libraryPreferences = libraryPreferences,
            coverCache = coverCache,
            sourceManager = sourceManager,
            downloadManager = downloadManager,
            downloadCache = downloadCache,
            trackerManager = trackerManager,
            libraryUpdateScheduler = libraryUpdateScheduler,
        )
    }

    @Test
    fun `ToggleFilter toggles TriState on matching preference`() = runTest {
        val viewModel = createViewModel()

        viewModel.onEvent(LibraryScreenEvent.ToggleFilter(LibraryFilterType.Unread))
        io.mockk.coVerify(timeout = 2000) { unreadPref.set(TriState.ENABLED_IS) }

        viewModel.onEvent(LibraryScreenEvent.ToggleFilter(LibraryFilterType.Downloaded))
        io.mockk.coVerify(timeout = 2000) { downloadedPref.set(TriState.ENABLED_IS) }

        viewModel.onEvent(LibraryScreenEvent.ToggleFilter(LibraryFilterType.SourceHealthDead))
        io.mockk.coVerify(timeout = 2000) { sourceHealthPref.set(TriState.ENABLED_IS) }
    }

    @Test
    fun `EnableHealthFilter sets source health dead filter to ENABLED_IS`() = runTest {
        val viewModel = createViewModel()

        viewModel.onEvent(LibraryScreenEvent.EnableHealthFilter)
        io.mockk.coVerify(timeout = 2000) { sourceHealthPref.set(TriState.ENABLED_IS) }
    }

    @Test
    fun `Search event updates searchQuery in state`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            val initial = awaitItem()
            assertNull(initial.searchQuery)

            viewModel.onEvent(LibraryScreenEvent.Search("Berserk"))
            val updated = awaitItem()
            assertEquals("Berserk", updated.searchQuery)
        }
    }

    @Test
    fun `Dialog events open and close settings dialog in state`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            val initial = awaitItem()
            assertNull(initial.dialog)

            viewModel.onEvent(LibraryScreenEvent.ShowSettingsDialog)
            val dialogState = awaitItem()
            assertEquals(LibraryViewModel.Dialog.SettingsSheet, dialogState.dialog)

            viewModel.onEvent(LibraryScreenEvent.CloseDialog)
            val closedState = awaitItem()
            assertNull(closedState.dialog)
        }
    }

    @Test
    fun `Uncategorized manga with category 0 is grouped into system category`() = runTest {
        val testManga: Manga = mockk(relaxed = true) {
            every { id } returns 42L
            every { source } returns 100L
            every { favorite } returns true
        }
        val libraryManga = LibraryManga(
            manga = testManga,
            categories = listOf(0L),
            totalChapters = 10,
            readCount = 0,
            bookmarkCount = 0,
            latestUpload = 0,
            chapterFetchedAt = 0,
            lastRead = 0,
        )
        every { getLibraryManga.subscribe() } returns flowOf(listOf(libraryManga))
        every { getCategories.subscribe() } returns flowOf(emptyList())

        val viewModel = createViewModel()

        viewModel.state.test {
            var current = awaitItem()
            while (!current.libraryData.isInitialized || current.displayedCategories.isEmpty()) {
                current = awaitItem()
            }

            assertTrue(current.libraryData.showSystemCategory)
            assertEquals(1, current.displayedCategories.size)
            assertEquals(Category.UNCATEGORIZED_ID, current.displayedCategories.first().id)
            val itemsInSystemCategory = current.getItemsForCategory(current.displayedCategories.first())
            assertEquals(1, itemsInSystemCategory.size)
            assertEquals(42L, itemsInSystemCategory.first().id)
        }
    }
}
