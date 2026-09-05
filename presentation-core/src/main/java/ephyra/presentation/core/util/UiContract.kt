package ephyra.presentation.core.util

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
