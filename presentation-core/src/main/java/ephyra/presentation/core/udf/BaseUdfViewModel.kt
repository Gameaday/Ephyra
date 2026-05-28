package ephyra.presentation.core.udf

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
 * Standardized base ViewModel implementing a robust Unidirectional Data Flow (UDF) pattern.
 *
 * @param State The immutable type representing the screen's visual state.
 * @param Event The sealed type representing user/system inputs emitted from the UI.
 * @param Effect The sealed type representing one-time actions emitted to the UI (e.g. Navigation, Toasts).
 */
abstract class BaseUdfViewModel<State, Event, Effect>(initialState: State) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    /**
     * Primary entrance point for processing UI events.
     */
    abstract fun onEvent(event: Event)

    /**
     * Safely updates the immutable UI state.
     */
    protected fun updateState(update: (State) -> State) {
        _state.update(update)
    }

    /**
     * Emits a one-time side-effect to the UI layer.
     * Uses the suspending send call in viewModelScope to guarantee delivery
     * even during layout transitions or configuration changes.
     */
    protected fun emitEffect(effect: Effect) {
        viewModelScope.launch {
            _effects.send(effect)
        }
    }
}

