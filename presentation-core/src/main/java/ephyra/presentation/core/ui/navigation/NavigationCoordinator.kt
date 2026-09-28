package ephyra.presentation.core.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single owner of app-level navigation signals, replacing two global mutable singletons.
 *
 * **Why this exists.** [NAV-001] as originally written asked for "one main `NavHost`", which
 * ADR-0011 amended: a nested `NavHost` per bottom tab is the correct pattern, because each tab
 * needs its own back stack, and flattening it would discard per-tab state. The defect ADR-0002 was
 * actually reaching for is narrower and is this file's subject — **duplicate mutable navigation
 * state living in singletons**:
 *
 * - `BottomNavVisibilityController` was an `object` holding a `MutableStateFlow`. Any screen could
 *   mutate it, it survived the Activity that should own it, and two `MainActivity` instances — or
 *   two tests — shared one value. A test that hid the bar leaked that state into every later test in
 *   the process.
 * - `NavigationEvents` was an `object` holding a `MutableSharedFlow` with `extraBufferCapacity = 1`
 *   and `tryEmit`. Tab reselect was delivered through a global bus that any module could publish to,
 *   so a feature could react to another feature's reselect and the coupling was invisible in the
 *   build graph.
 *
 * **What changes.** The state still exists and the behaviour is identical, but it is now *instance*
 * state owned by the composition that creates it and provided downward — the same pattern this
 * codebase already uses for `AppNavigator` and `LocalNavController`. A caller resolves the
 * coordinator from the composition rather than reaching for a singleton, so there is no way to
 * mutate navigation state without being inside a composition that installed one, and a test that
 * provides its own instance cannot affect another.
 *
 * **What deliberately does not change.** The reselect event stays a hot `SharedFlow` rather than
 * becoming per-tab state. The tab strip is the publisher and the active tab is the subscriber, and
 * making that a direct call would require the strip to know which subscriber is mounted. The
 * `route` filter is what keeps one tab's reselect from waking another tab's collector, and it is
 * unchanged.
 *
 * A missing coordinator is an error rather than a default. A silent default would reintroduce
 * exactly the failure this replaces: a screen that cannot find its owner would appear to work while
 * driving nothing.
 */
class NavigationCoordinator {

    /**
     * Whether the bottom navigation bar is showing.
     *
     * A screen enters selection mode by hiding it and shows it again on exit. Ownership is the host
     * shell (`HomeScreen`), which is the only thing that renders the bar.
     */
    private val _isBottomNavVisible = MutableStateFlow(true)
    val isBottomNavVisible: StateFlow<Boolean> = _isBottomNavVisible.asStateFlow()

    /** Hides the bottom bar, e.g. while a list is in selection mode. */
    fun hideBottomNav() {
        _isBottomNavVisible.value = false
    }

    /** Shows the bottom bar again. */
    fun showBottomNav() {
        _isBottomNavVisible.value = true
    }

    /**
     * Sets bottom-bar visibility explicitly.
     *
     * Preferred over hide/show at a call site that is not a simple toggle, because it cannot leave
     * the bar in a state the caller did not ask for.
     */
    fun setBottomNavVisible(visible: Boolean) {
        _isBottomNavVisible.value = visible
    }

    /**
     * Tab reselect events, keyed by the tab's route.
     *
     * `extraBufferCapacity = 1` with `tryEmit` is deliberate and unchanged: a reselect with no
     * collector yet must not fail, and the buffer is what makes that true. It also means a reselect
     * is a *hint* — a tab that is not currently mounted drops it, which is correct, because a tab
     * the user cannot see has nothing to reselect.
     */
    private val _reselectEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val reselectEvents: SharedFlow<String> = _reselectEvents.asSharedFlow()

    /** Signals that [route]'s tab was tapped while already selected. */
    fun triggerReselect(route: String) {
        _reselectEvents.tryEmit(route)
    }
}

/**
 * The coordinator for the current composition.
 *
 * Errors when absent rather than defaulting, so a screen outside the shell's provider fails loudly
 * instead of silently mutating a global that nothing renders.
 */
val LocalNavigationCoordinator = staticCompositionLocalOf<NavigationCoordinator> {
    error("No NavigationCoordinator provided")
}
