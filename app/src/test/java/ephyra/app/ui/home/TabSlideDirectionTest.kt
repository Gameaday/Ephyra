package ephyra.app.ui.home

import ephyra.presentation.core.ui.navigation.ScreenRoutes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The main area slides between the five bottom-nav destinations instead of fading.
 *
 * **Why direction is a test and not a comment.** The slide direction is derived from each tab's
 * position in the bar. If that derivation is wrong the transition still runs and still looks
 * animated — it just travels the wrong way, so tapping the tab to the *right* of the current one
 * sends the new page in from the left. That is the kind of defect that survives review and gets
 * reported as "the animation feels off" rather than as a bug, so the direction is pinned here.
 *
 * The helper is tri-state on purpose: `null` means "not two peers on the tab axis" (a nested screen
 * inside a tab), which must fall back to the fade-through rather than be read as "backward".
 */
class TabSlideDirectionTest {

    private val library = ScreenRoutes.Library.route
    private val updates = ScreenRoutes.Updates.route
    private val history = ScreenRoutes.History.route
    private val browse = ScreenRoutes.Browse.route
    private val more = ScreenRoutes.More.route

    @Test
    fun `moving right in the tab bar slides forward`() {
        assertEquals(true, tabSlideForward(library, updates))
        assertEquals(true, tabSlideForward(library, more))
        assertEquals(true, tabSlideForward(browse, more))
    }

    @Test
    fun `moving left in the tab bar slides backward`() {
        assertEquals(false, tabSlideForward(more, browse))
        assertEquals(false, tabSlideForward(more, library))
        assertEquals(false, tabSlideForward(updates, library))
    }

    @Test
    fun `a non-tab destination has no tab-axis direction`() {
        assertNull(tabSlideForward(library, "manga/123"))
        assertNull(tabSlideForward("manga/123", library))
        assertNull(tabSlideForward(null, library))
        assertNull(tabSlideForward(library, null))
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
