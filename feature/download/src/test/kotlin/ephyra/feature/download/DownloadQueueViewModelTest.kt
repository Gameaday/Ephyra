package ephyra.feature.download

import app.cash.turbine.test
import ephyra.domain.download.model.Download
import ephyra.domain.download.service.DownloadManager
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadQueueViewModelTest {

    private val downloadManager: DownloadManager = mockk(relaxed = true)

    private val queueStateFlow = MutableStateFlow<List<Download>>(emptyList())
    private val isRunningFlow = MutableStateFlow(false)
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        io.mockk.every { downloadManager.queueState } returns queueStateFlow
        io.mockk.every { downloadManager.isDownloaderRunning } returns isRunningFlow
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): DownloadQueueViewModel {
        return DownloadQueueViewModel(downloadManager = downloadManager)
    }

    @Test
    fun `initial state has empty downloads and isDownloaderRunning false`() = runTest {
        val viewModel = createViewModel()
        viewModel.state.test {
            val initial = awaitItem()
            assertTrue(initial.isEmpty)
            assertFalse(initial.isDownloaderRunning)
        }
    }

    @Test
    fun `queueState and isDownloaderRunning updates reflect in unified state`() = runTest {
        val viewModel = createViewModel()
        val mockDownload: Download = mockk()

        viewModel.state.test {
            awaitItem() // initial

            queueStateFlow.value = listOf(mockDownload)
            val updatedQueue = awaitItem()
            assertEquals(1, updatedQueue.downloads.size)
            assertFalse(updatedQueue.isDownloaderRunning)

            isRunningFlow.value = true
            val updatedRunning = awaitItem()
            assertEquals(1, updatedRunning.downloads.size)
            assertTrue(updatedRunning.isDownloaderRunning)
        }
    }

    @Test
    fun `StartDownloads event invokes manager`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEvent(DownloadQueueScreenEvent.StartDownloads)

        verify(exactly = 1) { downloadManager.startDownloads() }
    }

    @Test
    fun `PauseDownloads event invokes manager`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEvent(DownloadQueueScreenEvent.PauseDownloads)

        verify(exactly = 1) { downloadManager.pauseDownloads() }
    }

    @Test
    fun `ClearQueue event invokes manager`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEvent(DownloadQueueScreenEvent.ClearQueue)

        verify(exactly = 1) { downloadManager.clearQueue() }
    }

    @Test
    fun `Reorder event invokes manager`() = runTest {
        val viewModel = createViewModel()
        val mockDownloads: List<Download> = listOf(mockk())
        viewModel.onEvent(DownloadQueueScreenEvent.Reorder(mockDownloads))

        verify(exactly = 1) { downloadManager.reorderQueue(mockDownloads) }
    }

    @Test
    fun `Cancel event invokes manager`() = runTest {
        val viewModel = createViewModel()
        val mockDownloads: List<Download> = listOf(mockk())
        viewModel.onEvent(DownloadQueueScreenEvent.Cancel(mockDownloads))

        verify(exactly = 1) { downloadManager.cancelQueuedDownloads(mockDownloads) }
    }
}
