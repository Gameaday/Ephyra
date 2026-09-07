package ephyra.app.ui.home

import app.cash.turbine.test
import ephyra.core.common.preference.Preference
import ephyra.core.download.DownloadCache
import ephyra.domain.base.BasePreferences
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.source.interactor.GetIncognitoState
import ephyra.domain.source.service.SourcePreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val getIncognitoState: GetIncognitoState = mockk(relaxed = true)
    private val basePreferences: BasePreferences = mockk(relaxed = true)
    private val downloadCache: DownloadCache = mockk(relaxed = true)
    private val libraryPreferences: LibraryPreferences = mockk(relaxed = true)
    private val sourcePreferences: SourcePreferences = mockk(relaxed = true)

    private val incognitoFlow = MutableSharedFlow<Boolean>(replay = 1)
    private val downloadedOnlyPref: Preference<Boolean> = mockk(relaxed = true)
    private val downloadedOnlyFlow = MutableSharedFlow<Boolean>(replay = 1)
    private val isInitializingFlow = MutableStateFlow(false)

    private val showUpdatesCountPref: Preference<Boolean> = mockk(relaxed = true)
    private val showUpdatesCountFlow = MutableSharedFlow<Boolean>(replay = 1)
    private val updatesCountPref: Preference<Int> = mockk(relaxed = true)
    private val updatesCountFlow = MutableSharedFlow<Int>(replay = 1)

    private val extUpdatesCountPref: Preference<Int> = mockk(relaxed = true)
    private val extUpdatesCountFlow = MutableSharedFlow<Int>(replay = 1)

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { getIncognitoState.subscribe(null) } returns incognitoFlow
        every { basePreferences.downloadedOnly() } returns downloadedOnlyPref
        every { downloadedOnlyPref.changes() } returns downloadedOnlyFlow
        every { downloadCache.isInitializing } returns isInitializingFlow

        every { libraryPreferences.newShowUpdatesCount() } returns showUpdatesCountPref
        every { showUpdatesCountPref.changes() } returns showUpdatesCountFlow
        every { libraryPreferences.newUpdatesCount() } returns updatesCountPref
        every { updatesCountPref.changes() } returns updatesCountFlow

        every { sourcePreferences.extensionUpdatesCount() } returns extUpdatesCountPref
        every { extUpdatesCountPref.changes() } returns extUpdatesCountFlow

        incognitoFlow.tryEmit(false)
        downloadedOnlyFlow.tryEmit(false)
        showUpdatesCountFlow.tryEmit(true)
        updatesCountFlow.tryEmit(0)
        extUpdatesCountFlow.tryEmit(0)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = HomeViewModel(
        getIncognitoState = getIncognitoState,
        basePreferences = basePreferences,
        downloadCache = downloadCache,
        libraryPreferences = libraryPreferences,
        sourcePreferences = sourcePreferences,
    )

    @Test
    fun `initial state reflects defaults`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.incognito)
            assertFalse(state.downloadOnly)
            assertFalse(state.indexing)
            assertEquals(0, state.updatesBadgeCount)
            assertEquals(0, state.extensionsBadgeCount)
        }
    }

    @Test
    fun `incognito flow updates state`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            awaitItem()
            incognitoFlow.emit(true)
            val updated = awaitItem()
            assertTrue(updated.incognito)
        }
    }

    @Test
    fun `downloaded only flow updates state`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            awaitItem()
            downloadedOnlyFlow.emit(true)
            val updated = awaitItem()
            assertTrue(updated.downloadOnly)
        }
    }

    @Test
    fun `indexing flow updates state`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            awaitItem()
            isInitializingFlow.value = true
            val updated = awaitItem()
            assertTrue(updated.indexing)
        }
    }

    @Test
    fun `updates badge count combines show flag and count`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            awaitItem()
            updatesCountFlow.emit(5)
            val stateWith5 = awaitItem()
            assertEquals(5, stateWith5.updatesBadgeCount)

            showUpdatesCountFlow.emit(false)
            val stateWith0 = awaitItem()
            assertEquals(0, stateWith0.updatesBadgeCount)
        }
    }

    @Test
    fun `extension updates badge count updates state`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            awaitItem()
            extUpdatesCountFlow.emit(3)
            val updated = awaitItem()
            assertEquals(3, updated.extensionsBadgeCount)
        }
    }
}
