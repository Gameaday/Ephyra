package ephyra.feature.library

import app.cash.turbine.test
import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.TriState
import ephyra.domain.base.BasePreferences
import ephyra.domain.category.interactor.SetDisplayMode
import ephyra.domain.category.interactor.SetSortModeForCategory
import ephyra.domain.category.model.Category
import ephyra.domain.library.model.LibraryDisplayMode
import ephyra.domain.library.model.LibrarySort
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.track.service.Tracker
import ephyra.domain.track.service.TrackerManager
import ephyra.presentation.core.ui.AppInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibrarySettingsViewModelTest {

    private val basePreferences: BasePreferences = mockk(relaxed = true)
    private val libraryPreferences: LibraryPreferences = mockk(relaxed = true)
    private val setDisplayMode: SetDisplayMode = mockk(relaxed = true)
    private val setSortModeForCategory: SetSortModeForCategory = mockk(relaxed = true)
    private val trackerManager: TrackerManager = mockk(relaxed = true)
    private val appInfo: AppInfo = mockk(relaxed = true)

    private val filterDownloadedPref: Preference<TriState> = mockk(relaxed = true)
    private val filterTrackingPref: Preference<TriState> = mockk(relaxed = true)

    private val loggedInTrackersFlow = MutableStateFlow<List<Tracker>>(emptyList())
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { trackerManager.loggedInTrackersFlow() } returns loggedInTrackersFlow
        every { libraryPreferences.filterDownloaded() } returns filterDownloadedPref
        every { libraryPreferences.filterTracking(any()) } returns filterTrackingPref
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): LibrarySettingsViewModel = LibrarySettingsViewModel(
        basePreferences,
        libraryPreferences,
        setDisplayMode,
        setSortModeForCategory,
        trackerManager,
        appInfo,
    )

    @Test
    fun `initial state has empty trackers`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            assertEquals(emptyList<Tracker>(), awaitItem().loggedInTrackers)
        }
    }

    @Test
    fun `trackers update emits into state`() = runTest {
        val fakeTracker: Tracker = mockk(relaxed = true) {
            every { id } returns 1L
            every { name } returns "MyAnimeList"
        }

        val viewModel = createViewModel()

        viewModel.state.test {
            assertEquals(emptyList<Tracker>(), awaitItem().loggedInTrackers)

            loggedInTrackersFlow.value = listOf(fakeTracker)
            val updated = awaitItem()
            assertEquals(1, updated.loggedInTrackers.size)
            assertEquals("MyAnimeList", updated.loggedInTrackers[0].name)
        }
    }

    @Test
    fun `ToggleFilter toggles TriState preference`() = runTest {
        coEvery { filterDownloadedPref.get() } returns TriState.DISABLED
        val viewModel = createViewModel()

        viewModel.onEvent(LibrarySettingsScreenEvent.ToggleFilter(LibraryPreferences::filterDownloaded))

        coVerify { filterDownloadedPref.set(TriState.ENABLED_IS) }
    }

    @Test
    fun `ToggleTracker toggles tracker filter preference`() = runTest {
        coEvery { filterTrackingPref.get() } returns TriState.DISABLED
        val viewModel = createViewModel()

        viewModel.onEvent(LibrarySettingsScreenEvent.ToggleTracker(123))

        coVerify { filterTrackingPref.set(TriState.ENABLED_IS) }
    }

    @Test
    fun `SetDisplayMode invokes interactor`() = runTest {
        val viewModel = createViewModel()

        viewModel.onEvent(LibrarySettingsScreenEvent.SetDisplayMode(LibraryDisplayMode.List))

        verify { setDisplayMode.await(LibraryDisplayMode.List) }
    }

    @Test
    fun `SetSort invokes interactor`() = runTest {
        val category: Category = mockk(relaxed = true)
        val viewModel = createViewModel()

        viewModel.onEvent(
            LibrarySettingsScreenEvent.SetSort(
                category = category,
                mode = LibrarySort.Type.Alphabetical,
                direction = LibrarySort.Direction.Ascending,
            ),
        )

        coVerify {
            setSortModeForCategory.await(
                category = category,
                type = LibrarySort.Type.Alphabetical,
                direction = LibrarySort.Direction.Ascending,
            )
        }
    }
}
