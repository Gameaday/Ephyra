package ephyra.presentation.core.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Standard marker interface representing the immutable state of a Compose Screen.
 */
interface UiState

/**
 * Standard marker interface representing user interactions or intents sent to the ViewModel.
 */
interface UiEvent

/**
 * Standard marker interface representing one-off, asynchronous side-effects (e.g. navigation, alerts, snackbars).
 */
interface UiEffect

/**
 * A production-grade base ViewModel implementing a robust, thread-safe, and compile-safe
 * Unidirectional Data Flow (UDF) contract.
 *
 * Exposes:
 * - [state]: Read-only [StateFlow] representing the immutable screen data state.
 * - [effects]: Cold stream of one-off asynchronous [UiEffect] side effects (navigation, toast alerts, etc.).
 *
 * Enforces:
 * - [onEvent]: Standard dispatching channel for processing incoming user interaction [UiEvent]s.
 */
abstract class BaseViewModel<State : UiState, Event : UiEvent, Effect : UiEffect>(
    initialState: State,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    /**
     * Helper property to easily fetch the current active UI state snapshot.
     */
    protected val currentState: State
        get() = _state.value

    /**
     * Main dispatching channel for processing user interactions.
     * All UI inputs must pass through this handler to maintain UDF principles.
     */
    abstract fun onEvent(event: Event)

    /**
     * Updates the active UI state thread-safely via an atomic update callback.
     */
    protected fun updateState(update: (State) -> State) {
        _state.update(update)
    }

    /**
     * Emits a one-off asynchronous [UiEffect] to the UI flow.
     */
    protected fun sendEffect(effect: Effect) {
        viewModelScope.launch {
            _effects.send(effect)
        }
    }
}
