package ephyra.feature.browse.extension.details

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import ephyra.domain.source.service.SourceManager
import eu.kanade.tachiyomi.source.Source
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class SourcePreferencesViewModelTest {

    private val sourceManager: SourceManager = mockk()
    private val isInitializedFlow = MutableStateFlow(false)
    private val testDispatcher = StandardTestDispatcher()
    private val mockSource: Source = mockk {
        every { name } returns "MangaDex"
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { sourceManager.isInitialized } returns isInitializedFlow
        every { sourceManager.getOrStub(123L) } returns mockSource
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is loading`() = runTest {
        val viewModel = SourcePreferencesViewModel(
            savedStateHandle = SavedStateHandle(),
            sourceManager = sourceManager,
        )

        viewModel.state.test {
            val item = awaitItem()
            assertTrue(item.isLoading)
            assertEquals("", item.sourceTitle)
        }
    }

    @Test
    fun `init waits for source initialization and updates state with source title`() = runTest {
        val viewModel = SourcePreferencesViewModel(
            savedStateHandle = SavedStateHandle(),
            sourceManager = sourceManager,
        )

        viewModel.state.test {
            val initial = awaitItem()
            assertTrue(initial.isLoading)

            viewModel.init(123L)
            advanceUntilIdle()

            // Still loading because isInitializedFlow is false
            expectNoEvents()

            // Now signal that sources are initialized
            isInitializedFlow.value = true
            advanceUntilIdle()

            val success = awaitItem()
            assertFalse(success.isLoading)
            assertEquals("MangaDex", success.sourceTitle)
        }
    }

    @Test
    fun `SavedStateHandle with sourceId automatically initializes`() = runTest {
        isInitializedFlow.value = true
        val savedState = SavedStateHandle(mapOf("sourceId" to 123L))
        val viewModel = SourcePreferencesViewModel(
            savedStateHandle = savedState,
            sourceManager = sourceManager,
        )

        viewModel.state.test {
            val initial = awaitItem()
            advanceUntilIdle()

            val success = awaitItem()
            assertFalse(success.isLoading)
            assertEquals("MangaDex", success.sourceTitle)
        }
    }
}
