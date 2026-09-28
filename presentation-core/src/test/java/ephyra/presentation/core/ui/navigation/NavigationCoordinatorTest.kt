package ephyra.presentation.core.ui.navigation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Behavioural coverage for [NavigationCoordinator].
 *
 * Separate from `NavigationCoordinatorOwnershipTest` on purpose. That gate asserts the coordinator
 * is *the thing production uses* — that the two global singletons are gone and the shell provides
 * it. This one asserts it *behaves*. Neither substitutes for the other, and the reason is the
 * defect this replaced: the singletons had no behavioural test either, and every suite was green
 * while production drove a process-global object.
 *
 * No `runTest` is used. `presentation-core` declares no coroutines-test dependency and these
 * assertions do not need one: the flows hold their value synchronously, and a reselect with a
 * live subscriber is delivered without needing a test dispatcher. Adding the dependency for a
 * handful of assertions this module does not otherwise carry would be gratuitous.
 */
class NavigationCoordinatorTest {

    @Test
    fun `the bottom bar starts visible`() {
        assertTrue(NavigationCoordinator().isBottomNavVisible.value)
    }

    @Test
    fun `hiding and showing the bottom bar is observable`() {
        val coordinator = NavigationCoordinator()

        coordinator.hideBottomNav()
        assertFalse(coordinator.isBottomNavVisible.value)

        coordinator.showBottomNav()
        assertTrue(coordinator.isBottomNavVisible.value)
    }

    /**
     * `setBottomNavVisible` exists so a caller that is not toggling cannot leave the bar in a
     * state it did not ask for. Idempotence is asserted because a screen recomposing while a
     * selection mode ends must not be able to flip the bar back.
     */
    @Test
    fun `explicit visibility is idempotent`() {
        val coordinator = NavigationCoordinator()

        coordinator.setBottomNavVisible(false)
        coordinator.setBottomNavVisible(false)
        assertFalse(coordinator.isBottomNavVisible.value)

        coordinator.setBottomNavVisible(true)
        coordinator.setBottomNavVisible(true)
        assertTrue(coordinator.isBottomNavVisible.value)
    }

    /**
     * The property that made the singleton unsafe: two coordinators must not see each other's
     * state.
     *
     * With the old `object`, this could not even be written — there was only ever one instance, so
     * "does A's state leak into B" had no meaning. That is the whole point of the change, so it is
     * asserted first.
     */
    @Test
    fun `two coordinators do not share state`() {
        val first = NavigationCoordinator()
        val second = NavigationCoordinator()

        first.hideBottomNav()

        assertFalse(first.isBottomNavVisible.value)
        assertTrue(
            second.isBottomNavVisible.value,
            "a second coordinator must not observe the first's state; that was the singleton defect",
        )
    }

    /**
     * `extraBufferCapacity = 1` with `tryEmit` is deliberate: a reselect fired before the target
     * tab is mounted must not fail or throw. This pins that it stays non-suspending and does not
     * throw with no collector attached.
     */
    @Test
    fun `a reselect with no collector does not throw`() {
        NavigationCoordinator().triggerReselect("library")
    }

    @Test
    fun `a reselect for one tab does not change bottom bar visibility`() {
        // The two signals share a coordinator but not a meaning. A tab reselect asks the tab to
        // scroll to top; it has no business hiding the navigation bar. Asserting the separation
        // keeps a future convenience method from conflating them.
        val coordinator = NavigationCoordinator()
        coordinator.triggerReselect("library")
        assertTrue(coordinator.isBottomNavVisible.value)
    }
}
