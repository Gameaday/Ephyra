package ephyra.feature.browse.source.browse

import app.cash.turbine.test
import ephyra.core.common.preference.Preference
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.category.interactor.SetMangaCategories
import ephyra.domain.chapter.interactor.SetMangaDefaultChapterFlags
import ephyra.domain.library.model.LibraryDisplayMode
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.interactor.GetDuplicateLibraryManga
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.service.CoverCache
import ephyra.domain.source.interactor.GetIncognitoState
import ephyra.domain.source.interactor.GetRemoteManga
import ephyra.domain.source.service.SourceManager
import ephyra.domain.source.service.SourcePreferences
import ephyra.domain.track.interactor.AddTracks
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class BrowseSourceViewModelTest {

    private val sourceManager: SourceManager = mockk()
    private val sourcePreferences: SourcePreferences = mockk()
    private val libraryPreferences: LibraryPreferences = mockk()
    private val coverCache: CoverCache = mockk(relaxed = true)
    private val getRemoteManga: GetRemoteManga = mockk(relaxed = true)
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga = mockk(relaxed = true)
    private val getCategories: GetCategories = mockk(relaxed = true)
    private val setMangaCategories: SetMangaCategories = mockk(relaxed = true)
    private val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags = mockk(relaxed = true)
    private val getManga: GetManga = mockk(relaxed = true)
    private val updateManga: UpdateManga = mockk(relaxed = true)
    private val addTracks: AddTracks = mockk(relaxed = true)
    private val getIncognitoState: GetIncognitoState = mockk(relaxed = true)

    private val displayModePref: Preference<LibraryDisplayMode> = mockk(relaxed = true)
    private val lastUsedSourcePref: Preference<Long> = mockk(relaxed = true)
    private val hideInLibraryPref: Preference<Boolean> = mockk(relaxed = true)

    private val catalogueSource: CatalogueSource = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { displayModePref.getSync() } returns LibraryDisplayMode.default
        every { displayModePref.changes() } returns emptyFlow()
        every { sourcePreferences.sourceDisplayMode() } returns displayModePref

        every { lastUsedSourcePref.set(any()) } returns Unit
        every { sourcePreferences.lastUsedSource() } returns lastUsedSourcePref

        coEvery { hideInLibraryPref.get() } returns false
        every { sourcePreferences.hideInLibraryItems() } returns hideInLibraryPref

        every { catalogueSource.id } returns 100L
        every { catalogueSource.getFilterList() } returns FilterList()
        every { sourceManager.getOrStub(100L) } returns catalogueSource
        every { getIncognitoState.await(100L) } returns false
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): BrowseSourceViewModel {
        return BrowseSourceViewModel(
            sourceManager = sourceManager,
            sourcePreferences = sourcePreferences,
            libraryPreferences = libraryPreferences,
            coverCache = coverCache,
            getRemoteManga = getRemoteManga,
            getDuplicateLibraryManga = getDuplicateLibraryManga,
            getCategories = getCategories,
            setMangaCategories = setMangaCategories,
            setMangaDefaultChapterFlags = setMangaDefaultChapterFlags,
            getManga = getManga,
            updateManga = updateManga,
            addTracks = addTracks,
            getIncognitoState = getIncognitoState,
        )
    }

    @Test
    fun `initial state has default Popular listing`() = runTest {
        val viewModel = createViewModel()
        viewModel.state.test {
            val initial = awaitItem()
            assertTrue(initial.listing is BrowseSourceViewModel.Listing.Popular)
            assertNull(initial.dialog)
            assertNull(initial.toolbarQuery)
        }
    }

    @Test
    fun `init updates state for catalogue source`() = runTest {
        val viewModel = createViewModel()
        viewModel.init(100L, GetRemoteManga.QUERY_POPULAR)
        advanceUntilIdle()

        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.listing is BrowseSourceViewModel.Listing.Popular)
            assertEquals(100L, viewModel.sourceId)
            assertEquals(catalogueSource, viewModel.source)
            assertEquals(100L, state.sourceId)
            assertEquals(catalogueSource, state.source)
        }
    }

    @Test
    fun `mangaPagerFlowFlow emits when initialized with popular listing`() = runTest {
        val viewModel = createViewModel()
        viewModel.init(100L, GetRemoteManga.QUERY_POPULAR)
        advanceUntilIdle()

        viewModel.mangaPagerFlowFlow.test {
            val flow = awaitItem()
            org.junit.jupiter.api.Assertions.assertNotNull(flow)
        }
    }

    @Test
    fun `SetListing event updates state listing`() = runTest {
        val viewModel = createViewModel()
        viewModel.state.test {
            awaitItem()

            viewModel.onEvent(BrowseSourceScreenEvent.SetListing(BrowseSourceViewModel.Listing.Latest))
            val updated = awaitItem()
            assertTrue(updated.listing is BrowseSourceViewModel.Listing.Latest)
            assertNull(updated.toolbarQuery)
        }
    }

    @Test
    fun `SetToolbarQuery event updates toolbarQuery in state`() = runTest {
        val viewModel = createViewModel()
        viewModel.state.test {
            awaitItem()

            viewModel.onEvent(BrowseSourceScreenEvent.SetToolbarQuery("Solo Leveling"))
            val updated = awaitItem()
            assertEquals("Solo Leveling", updated.toolbarQuery)
        }
    }

    @Test
    fun `SetDialog event updates and clears dialog in state`() = runTest {
        val viewModel = createViewModel()
        viewModel.state.test {
            awaitItem()

            viewModel.onEvent(BrowseSourceScreenEvent.SetDialog(BrowseSourceViewModel.Dialog.Filter))
            val withDialog = awaitItem()
            assertEquals(BrowseSourceViewModel.Dialog.Filter, withDialog.dialog)

            viewModel.onEvent(BrowseSourceScreenEvent.SetDialog(null))
            val withoutDialog = awaitItem()
            assertNull(withoutDialog.dialog)
        }
    }

    @Test
    fun `ResetFilters event resets filters to source default filter list`() = runTest {
        val viewModel = createViewModel()
        viewModel.init(100L, GetRemoteManga.QUERY_POPULAR)
        advanceUntilIdle()

        viewModel.state.test {
            awaitItem()

            viewModel.onEvent(BrowseSourceScreenEvent.ResetFilters)
            val updated = awaitItem()
            assertEquals(0, updated.filters.size)
        }
    }
}
