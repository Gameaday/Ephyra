package ephyra.feature.settings.screen.debug

import android.content.Context
import androidx.work.WorkManager
import androidx.work.WorkQuery
import app.cash.turbine.test
import ephyra.core.common.util.system.workManager
import ephyra.domain.ui.UiPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkerInfoViewModelTest {

    private val context: Context = mockk(relaxed = true)
    private val uiPreferences: UiPreferences = mockk(relaxed = true)
    private val workManager: WorkManager = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("ephyra.core.common.util.system.WorkManagerExtensionsKt")
        every { context.workManager } returns workManager
        every { workManager.getWorkInfosFlow(any<WorkQuery>()) } returns flowOf(emptyList())
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("ephyra.core.common.util.system.WorkManagerExtensionsKt")
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state collects empty worker lists with dashes`() = runTest {
        val viewModel = WorkerInfoViewModel(context, uiPreferences)

        viewModel.state.test {
            val state = awaitItem()
            assertEquals("-\n", state.finished)
            assertEquals("-\n", state.running)
            assertEquals("-\n", state.enqueued)
        }
    }
}
