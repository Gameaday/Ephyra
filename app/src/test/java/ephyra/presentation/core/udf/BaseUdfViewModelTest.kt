package ephyra.presentation.core.udf

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BaseUdfViewModelTest {

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Concrete helper types for test VM instantiation ───────────────────

    private data class TestState(
        val count: Int,
        val text: String,
    )

    private sealed interface TestEvent {
        data class Increment(val amount: Int) : TestEvent
        data class UpdateText(val newText: String) : TestEvent
        data class TriggerEffect(val message: String) : TestEvent
    }

    private sealed interface TestEffect {
        data class ShowToast(val message: String) : TestEffect
    }

    private class TestViewModel(initialState: TestState) :
        BaseUdfViewModel<TestState, TestEvent, TestEffect>(initialState) {

        override fun onEvent(event: TestEvent) {
            when (event) {
                is TestEvent.Increment -> updateState { it.copy(count = it.count + event.amount) }
                is TestEvent.UpdateText -> updateState { it.copy(text = event.newText) }
                is TestEvent.TriggerEffect -> emitEffect(TestEffect.ShowToast(event.message))
            }
        }

        fun triggerDirectStateUpdate(newCount: Int) {
            updateState { it.copy(count = newCount) }
        }

        fun triggerDirectEffect(message: String) {
            emitEffect(TestEffect.ShowToast(message))
        }
    }

    // ── Tests ────────────────────────────────────────────────────────────

    @Test
    fun `initial state is correctly assigned`() {
        val initialState = TestState(count = 0, text = "initial")
        val viewModel = TestViewModel(initialState)

        assertEquals(initialState, viewModel.state.value) {
            "The ViewModel must present the exact initial state specified at construction"
        }
    }

    @Test
    fun `state updates are correctly reflected through events`() {
        val viewModel = TestViewModel(TestState(count = 0, text = "initial"))

        viewModel.onEvent(TestEvent.Increment(5))
        assertEquals(5, viewModel.state.value.count) {
            "State should update immediately and accumulate the increment amount"
        }

        viewModel.onEvent(TestEvent.UpdateText("hello"))
        assertEquals("hello", viewModel.state.value.text) {
            "State should correctly update textual parameters"
        }
    }

    @Test
    fun `state updates are correctly reflected through direct calls`() {
        val viewModel = TestViewModel(TestState(count = 0, text = "initial"))

        viewModel.triggerDirectStateUpdate(42)
        assertEquals(42, viewModel.state.value.count) {
            "Direct calls to updateState must update the state value as expected"
        }
    }

    @Test
    fun `effects emitted via onEvent are successfully collected`() = runTest {
        val viewModel = TestViewModel(TestState(count = 0, text = "initial"))
        val collectedEffects = mutableListOf<TestEffect>()

        val collectJob = launch {
            viewModel.effects.collect { collectedEffects.add(it) }
        }

        // Trigger collector registration
        advanceUntilIdle()

        viewModel.onEvent(TestEvent.TriggerEffect("Toast 1"))
        viewModel.onEvent(TestEvent.TriggerEffect("Toast 2"))

        // Run collector execution to consume buffer
        advanceUntilIdle()

        assertEquals(2, collectedEffects.size) {
            "Buffered channel must deliver all emitted side-effects"
        }
        assertEquals(TestEffect.ShowToast("Toast 1"), collectedEffects[0])
        assertEquals(TestEffect.ShowToast("Toast 2"), collectedEffects[1])

        collectJob.cancel()
    }

    @Test
    fun `effects emitted directly are successfully collected`() = runTest {
        val viewModel = TestViewModel(TestState(count = 0, text = "initial"))
        val collectedEffects = mutableListOf<TestEffect>()

        val collectJob = launch {
            viewModel.effects.collect { collectedEffects.add(it) }
        }

        // Trigger collector registration
        advanceUntilIdle()

        viewModel.triggerDirectEffect("Direct Toast")

        // Run collector execution to consume buffer
        advanceUntilIdle()

        assertEquals(1, collectedEffects.size) {
            "Direct emitEffect invocations must properly propagate to collectors"
        }
        assertEquals(TestEffect.ShowToast("Direct Toast"), collectedEffects[0])

        collectJob.cancel()
    }
}
