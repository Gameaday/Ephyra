package ephyra.feature.settings.screen

import app.cash.turbine.test
import ephyra.core.common.util.Result
import ephyra.domain.content.source.SourceType
import ephyra.domain.content.source.interactor.GetAvailableSources
import ephyra.domain.content.source.interactor.RemoveCustomSource
import ephyra.domain.content.source.interactor.UnifiedSource
import ephyra.domain.content.source.interactor.UpdateCustomSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SourceManagementViewModelTest {

    private val getAvailableSources: GetAvailableSources = mockk()
    private val updateCustomSource: UpdateCustomSource = mockk(relaxed = true)
    private val removeCustomSource: RemoveCustomSource = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()

    private val sourcesFlow = MutableStateFlow<List<UnifiedSource>>(emptyList())
    private val sampleSource = UnifiedSource(
        id = 1L,
        name = "Test Source",
        baseUrl = "https://example.com",
        sourceType = SourceType.REMOTE_EXTENSION,
        enabled = true,
        extensionId = null,
        lastHealthCheck = 0,
        failureCount = 0,
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { getAvailableSources() } returns sourcesFlow
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initialization loads sources and updates state`() = runTest {
        sourcesFlow.value = listOf(sampleSource)
        val viewModel = SourceManagementViewModel(
            getAvailableSources,
            updateCustomSource,
            removeCustomSource,
        )

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(listOf(sampleSource), state.sources)
            assertFalse(state.isLoading)
            assertNull(state.error)
        }
    }

    @Test
    fun `RemoveSource event removes source and reloads sources`() = runTest {
        coEvery { removeCustomSource.removeSource("https://example.com") } returns Result.Success(Unit)

        val viewModel = SourceManagementViewModel(
            getAvailableSources,
            updateCustomSource,
            removeCustomSource,
        )

        viewModel.onEvent(SourceManagementEvent.RemoveSource("https://example.com"))

        coVerify(exactly = 1) {
            removeCustomSource.removeSource("https://example.com")
        }
    }
}
