package ephyra.feature.library

import app.cash.turbine.test
import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.TriState
import ephyra.core.download.DownloadCache
import ephyra.domain.base.BasePreferences
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.category.interactor.SetMangaCategories
import ephyra.domain.category.model.Category
import ephyra.domain.chapter.interactor.SetReadStatus
import ephyra.domain.chapter.repository.ChapterRepository
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.history.interactor.GetNextChapters
import ephyra.domain.library.model.LibraryManga
import ephyra.domain.library.model.LibrarySort
import ephyra.domain.library.model.sort
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
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val chapterRepository: ChapterRepository = mockk(relaxed = true)
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

        // Sort preferences. `libraryPreferences` is relaxed, so these need explicit flows for the
        // combine to emit at all; the sort assertions below replace them with mutable ones.
        every { libraryPreferences.sortingMode().changes() } returns flowOf(LibrarySort.default)
        every { libraryPreferences.randomSortSeed().changes() } returns flowOf(0)
        every { libraryPreferences.categorizedDisplaySettings().changes() } returns flowOf(false)
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
            chapterRepository = chapterRepository,
            setReadStatus = setReadStatus,
            updateManga = updateManga,
            evictChapterCacheForManga = mockk(relaxed = true),
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

    /**
     * Regression test for "sort does not work on the library screen".
     *
     * The re-sort pipeline is keyed on `LibraryData` equality. The sort was previously read straight
     * from the preference inside `applySort`, so it lived outside the flow: `distinctUntilChanged()`
     * saw an unchanged `LibraryData` and discarded the emission, and the grid kept the old order
     * until the library contents happened to change. This drives the sort through the state flow and
     * asserts the visible order actually moves.
     */
    @Test
    fun `changing the sort mode reorders the library`() = runTest(testDispatcher) {
        val dateAddedDescending = LibrarySort(LibrarySort.Type.DateAdded, LibrarySort.Direction.Descending)
        val sortFlow = MutableStateFlow(dateAddedDescending)
        every { libraryPreferences.sortingMode().changes() } returns sortFlow

        // "Bravo" was added most recently, "Alpha" least recently.
        val bravo = libraryManga(id = 1L, title = "Bravo", dateAdded = 200L)
        val alpha = libraryManga(id = 2L, title = "Alpha", dateAdded = 100L)
        every { getLibraryManga.subscribe() } returns flowOf(listOf(alpha, bravo))
        every { getCategories.subscribe() } returns flowOf(emptyList())

        val viewModel = createViewModel()

        viewModel.state.test {
            // Descending by date added puts the newest first.
            awaitStateWithIds(listOf(1L, 2L), sort = dateAddedDescending)

            // Now sort by title ascending: "Alpha" must move ahead of "Bravo".
            val alphabeticalAscending = LibrarySort(
                LibrarySort.Type.Alphabetical,
                LibrarySort.Direction.Ascending,
            )
            sortFlow.value = alphabeticalAscending

            awaitStateWithIds(listOf(2L, 1L), sort = alphabeticalAscending)
        }
    }

    /**
     * Re-tapping Random writes a new seed. That seed was read outside the flow, so the shuffle never
     * reached the grid and the control appeared dead.
     */
    @Test
    fun `a new random seed reshuffles the library`() = runTest(testDispatcher) {
        val seedFlow = MutableStateFlow(0)
        every {
            libraryPreferences.sortingMode().changes()
        } returns flowOf(LibrarySort(LibrarySort.Type.Random, LibrarySort.Direction.Ascending))
        every { libraryPreferences.randomSortSeed().changes() } returns seedFlow

        val items = (1L..6L).map { libraryManga(id = it, title = "Title $it", dateAdded = it) }
        every { getLibraryManga.subscribe() } returns flowOf(items)
        every { getCategories.subscribe() } returns flowOf(emptyList())

        val viewModel = createViewModel()

        viewModel.state.test {
            val before = awaitAnyIds(count = 6, seed = 0)

            // Try a handful of seeds: one particular seed can reproduce the same order by chance, so
            // this asserts that *some* seed moves the order rather than that any single one does.
            var changed = false
            for (seed in 1..10) {
                seedFlow.value = seed
                val shuffled = awaitAnyIds(count = 6, seed = seed)
                if (shuffled != before) {
                    changed = true
                    break
                }
            }
            assertTrue(changed, "changing the random seed never changed the library order")
        }
    }

    /**
     * Drains state emissions until the grid publishes [expected] in display order, returning that
     * emission.
     *
     * `groupedFavorites` and `libraryData` are written by two independent `collectLatest` writers, so an
     * emission can carry a freshly-replaced `libraryData` beside a `groupedFavorites` still holding the
     * previous ordering. The first matching emission therefore proves nothing; this waits for the
     * ordering itself, and fails with what it actually saw if it never arrives.
     *
     * Waiting for a *value* rather than a count is what makes this an assertion rather than a sleep:
     * with the sort read outside the flow the grid never re-orders, and this exhausts and throws.
     *
     * Returns the [LibraryViewModel.State] rather than just the ids so callers can assert on the same
     * emission instead of asking Turbine for a value that has already been consumed.
     */
    private suspend fun app.cash.turbine.ReceiveTurbine<LibraryViewModel.State>.awaitStateWithIds(
        expected: List<Long>,
        sort: LibrarySort? = null,
        seed: Int? = null,
    ): LibraryViewModel.State {
        var last: List<Long> = emptyList()
        repeat(200) {
            val state = awaitItem()
            if (sort != null && state.libraryData.sortPreferences.sortMode != sort) return@repeat
            if (seed != null && state.libraryData.sortPreferences.randomSortSeed != seed) return@repeat
            val category = state.displayedCategories.firstOrNull() ?: return@repeat
            last = state.getItemsForCategory(category).map { it.id }
            if (last == expected) return state
        }
        error("expected the library in order $expected but the last observed order was $last")
    }

    private fun LibraryViewModel.State.displayedIds(): List<Long> =
        displayedCategories.firstOrNull()?.let { getItemsForCategory(it).map { item -> item.id } }
            ?: emptyList()

    /** Waits for the first grid that holds [count] items, without asserting their order. */
    private suspend fun app.cash.turbine.ReceiveTurbine<LibraryViewModel.State>.awaitAnyIds(
        count: Int,
        seed: Int? = null,
    ): List<Long> {
        repeat(200) {
            val state = awaitItem()
            if (seed != null && state.libraryData.sortPreferences.randomSortSeed != seed) return@repeat
            val category = state.displayedCategories.firstOrNull() ?: return@repeat
            val ids = state.getItemsForCategory(category).map { it.id }
            if (ids.size == count) return ids
        }
        error("the library never published a grouped list of $count items")
    }

    /**
     * The sort dialog must highlight the sort that produced the order on screen.
     *
     * It used to derive the highlighted option from `category.sort`. The Default tab is synthesized
     * with flags 0, so that always read "Alphabetical, descending" no matter what the user picked --
     * the grid would have re-ordered while the dialog insisted it had not.
     */
    @Test
    fun `the Default tab reports the stored sort rather than its zero flags`() = runTest(testDispatcher) {
        val chosen = LibrarySort(LibrarySort.Type.DateAdded, LibrarySort.Direction.Ascending)
        every { libraryPreferences.sortingMode().changes() } returns MutableStateFlow(chosen)

        val items = listOf(
            libraryManga(id = 1L, title = "Bravo", dateAdded = 200L),
            libraryManga(id = 2L, title = "Alpha", dateAdded = 100L),
        )
        every { getLibraryManga.subscribe() } returns flowOf(items)
        every { getCategories.subscribe() } returns flowOf(emptyList())

        val viewModel = createViewModel()

        viewModel.state.test {
            // DateAdded ascending puts the older entry first: Bravo was added at 200, Alpha at 100.
            val current = awaitStateWithIds(listOf(2L, 1L), sort = chosen)

            val defaultTab = current.displayedCategories.first()
            assertTrue(defaultTab.isSystemCategory, "expected the Default tab, got ${defaultTab.name}")

            // The dialog resolves through `sortFor`, so it agrees with the rendered order...
            assertEquals(chosen, current.sortFor(defaultTab))
            // ...whereas decoding the tab's flags still yields Alphabetical/Descending -- the stale
            // value the dialog used to highlight, because the Default tab is always built with
            // flags 0. Asserting it explicitly pins the exact regression rather than just "not equal".
            assertEquals(
                LibrarySort(LibrarySort.Type.Alphabetical, LibrarySort.Direction.Descending),
                defaultTab.sort,
            )
        }
    }

    private fun libraryManga(id: Long, title: String, dateAdded: Long): LibraryManga {
        val manga: Manga = mockk(relaxed = true) {
            every { this@mockk.id } returns id
            every { source } returns 100L
            every { favorite } returns true
            every { this@mockk.title } returns title
            every { this@mockk.dateAdded } returns dateAdded
        }
        return LibraryManga(
            manga = manga,
            categories = listOf(0L),
            totalChapters = 10,
            readCount = 0,
            bookmarkCount = 0,
            latestUpload = 0,
            chapterFetchedAt = 0,
            lastRead = 0,
        )
    }
}
