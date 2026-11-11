package ephyra.app.ui.home

import ephyra.domain.navigation.motion.MotionPolicy
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The main area slides between the five bottom-nav destinations in bar order and keeps the
 * fade-through for a nested screen reached inside a tab.
 *
 * **Why peer-ness is a test and not a comment.** The peer/not-peer decision picks which of two
 * transitions runs: peers slide in bar order, a nested screen fades through. If the decision is
 * wrong the transition still runs — it just moves the wrong way or dissolves where it should
 * slide, the kind of defect that survives review and gets reported as "the animation feels off"
 * rather than as a bug.
 *
 * **The reversal, recorded.** The owner's motion audit (2026-11-09) removed the tab slide as
 * elements flying; the owner's 2026-11-11 directive re-legalised exactly that one movement —
 * sibling tabs read as pages of a book or tablet — so the direction tests below are re-pinned
 * from [MotionPolicy.tabSlideDirection].
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

    @Test
    fun `the slide direction follows bar order`() {
        assertEquals(1, MotionPolicy.tabSlideDirection(tabIndexOf(library), tabIndexOf(more)))
        assertEquals(-1, MotionPolicy.tabSlideDirection(tabIndexOf(more), tabIndexOf(library)))
        assertEquals(1, MotionPolicy.tabSlideDirection(tabIndexOf(updates), tabIndexOf(browse)))
        assertEquals(-1, MotionPolicy.tabSlideDirection(tabIndexOf(browse), tabIndexOf(updates)))
    }
}
