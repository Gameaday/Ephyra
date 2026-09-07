package ephyra.feature.more

import app.cash.turbine.test
import ephyra.core.common.preference.Preference
import ephyra.domain.base.BasePreferences
import ephyra.domain.download.model.Download
import ephyra.domain.download.service.DownloadManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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
class MoreViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val downloadManager = mockk<DownloadManager>(relaxed = true)
    private val basePreferences = mockk<BasePreferences>()
    private val downloadedOnlyPref = mockk<Preference<Boolean>>(relaxed = true)
    private val incognitoModePref = mockk<Preference<Boolean>>(relaxed = true)

    private val isDownloaderRunningFlow = MutableSharedFlow<Boolean>(replay = 1)
    private val queueStateFlow = MutableStateFlow<List<Download>>(emptyList())

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { downloadedOnlyPref.getSync() } returns false
        every { downloadedOnlyPref.changes() } returns MutableStateFlow(false)

        every { incognitoModePref.getSync() } returns false
        every { incognitoModePref.changes() } returns MutableStateFlow(false)

        every { basePreferences.downloadedOnly() } returns downloadedOnlyPref
        every { basePreferences.incognitoMode() } returns incognitoModePref

        isDownloaderRunningFlow.tryEmit(false)
        every { downloadManager.isDownloaderRunning } returns isDownloaderRunningFlow
        every { downloadManager.queueState } returns queueStateFlow
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = MoreViewModel(
        downloadManager = downloadManager,
        preferences = basePreferences,
    )

    @Test
    fun `initial state reflects defaults`() {
        val viewModel = createViewModel()
        val state = viewModel.state.value

        assertFalse(state.downloadedOnly)
        assertFalse(state.incognitoMode)
        assertEquals(DownloadQueueState.Stopped, state.downloadQueueState)
    }

    @Test
    fun `SetDownloadedOnly updates preference`() {
        val viewModel = createViewModel()
        viewModel.onEvent(MoreEvent.SetDownloadedOnly(true))
        verify { downloadedOnlyPref.set(true) }
    }

    @Test
    fun `SetIncognitoMode updates preference`() {
        val viewModel = createViewModel()
        viewModel.onEvent(MoreEvent.SetIncognitoMode(true))
        verify { incognitoModePref.set(true) }
    }

    @Test
    fun `download queue state updates when downloader runs with items`() = runTest(testDispatcher) {
        val downloadMock = mockk<Download>(relaxed = true)
        val viewModel = createViewModel()

        viewModel.state.test {
            awaitItem() // initial

            // Emit downloads and running state
            queueStateFlow.value = listOf(downloadMock)
            isDownloaderRunningFlow.emit(true)
            testDispatcher.scheduler.advanceUntilIdle()

            val state = awaitItem()
            assertTrue(state.downloadQueueState is DownloadQueueState.Downloading)
            assertEquals(1, (state.downloadQueueState as DownloadQueueState.Downloading).pending)
        }
    }
}
