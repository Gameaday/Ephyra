package ephyra.app.ui.home

import ephyra.presentation.core.ui.navigation.ScreenRoutes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The main area crossfades between the five bottom-nav destinations and keeps the fade-through
 * for a nested screen reached inside a tab.
 *
 * **Why peer-ness is a test and not a comment.** The peer/not-peer decision picks which of two
 * no-movement transitions runs: peers crossfade, a nested screen fades through. If the decision
 * is wrong the transition still runs — it just dissolves the wrong way, the kind of defect that
 * survives review and gets reported as "the animation feels off" rather than as a bug.
 *
 * The slide this file's predecessor pinned was removed by the owner's motion audit (2026-11-09):
 * a horizontal tab move reads as elements flying, the motion the audit rejected, so the direction
 * it used to test no longer exists.
 */
class TabPeerTransitionTest {

    private val library = ScreenRoutes.Library.route
    private val updates = ScreenRoutes.Updates.route
    private val history = ScreenRoutes.History.route
    private val browse = ScreenRoutes.Browse.route
    private val more = ScreenRoutes.More.route

    @Test
    fun `two tab roots are peers in either direction`() {
        assertTrue(isTabPeerTransition(library, updates))
        assertTrue(isTabPeerTransition(more, library))
        assertTrue(isTabPeerTransition(browse, history))
    }

    @Test
    fun `a non-tab destination is not a peer`() {
        assertFalse(isTabPeerTransition(library, "manga/123"))
        assertFalse(isTabPeerTransition("manga/123", library))
        assertFalse(isTabPeerTransition(null, library))
        assertFalse(isTabPeerTransition(library, null))
    }

    @Test
    fun `the tab order matches the order of the bar`() {
        assertEquals(0, tabIndexOf(library))
        assertEquals(1, tabIndexOf(updates))
        assertEquals(2, tabIndexOf(history))
        assertEquals(3, tabIndexOf(browse))
        assertEquals(4, tabIndexOf(more))
    }
}
