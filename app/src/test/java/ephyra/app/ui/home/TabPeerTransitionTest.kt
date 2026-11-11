package ephyra.app.ui.home

import ephyra.app.security.TrackedFileNames
import ephyra.domain.navigation.motion.MotionPolicy
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import java.io.File
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

    @Test
    fun `a pop between tab peers does not reverse the slide`() {
        // The pop slots once swapped the index order before asking for a direction, which
        // inverted exactly one family of moves: into Library, the start destination, whose
        // transitions fired as pops. The owner's defect report (2026-11-11) described it
        // precisely - the slide to Library ran the wrong way while every other tab pair read
        // correctly. Peers have no hierarchy to reverse: whichever slots fire, the direction
        // is bar order.
        val home = sourceFile("app/src/main/java/ephyra/app/ui/home/HomeScreen.kt")
        val code = codeOf(home)
        val swapped = Regex(
            "tabIndexOf\\(targetState\\.destination\\.route\\),\\s*\\n\\s*tabIndexOf\\(initialState\\.destination\\.route\\),",
        )
        assertFalse(
            swapped.containsMatchIn(code),
            "A tab NavHost slot must not re-derive the slide direction from swapped indices: " +
                "peers slide in bar order whether the transition is a push or a pop.",
        )
    }

    private fun sourceFile(relative: String): File = File(TrackedFileNames.repositoryRoot(), relative)

    /** Source with comments removed, so prose describing a rule cannot satisfy or trip it. */
    private fun codeOf(file: File): String = file.readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("//.*"), "")
}
