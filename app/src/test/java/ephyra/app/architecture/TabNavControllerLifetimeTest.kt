package ephyra.app.architecture

import ephyra.app.security.TrackedFileNames
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Structural gate for **P0-2**: the bottom-tab `NavController` must not be created inside the
 * composition that navigation disposes.
 *
 * **The defect this prevents.** `HomeScreen` created its tab controller with `rememberNavController()`
 * in its own body. `HomeScreen` is the body of the app-level `composable(ScreenRoutes.Home.route)`,
 * so navigating to a series detail **disposed that composition and the controller with it** —
 * destroying the tab back stack and every entry `saveState` had saved. Returning rebuilt a fresh
 * controller at `Library`. The user-visible consequences were that each tab lost its scroll
 * position, filter and search query on every detail visit, and any destination pushed inside a tab
 * was silently discarded.
 *
 * **Why the cause was the lifetime and not the graph.** ADR-0011 records that the nested tab
 * `NavHost` is the *correct* pattern — each tab needs an independent back stack — and that the
 * defect ADR-0002 was reaching for is duplicate mutable state, not physical nesting. This gate
 * encodes exactly that: the controller must be hoisted above the `NavHost` that disposes it. It
 * would be easy to "fix" this by flattening the two `NavHost`s, which would look like a fix and
 * would discard per-tab back stacks entirely.
 *
 * **What it cannot check.** That the hoisted controller is actually *used* — that is the
 * `MainActivity` side of the same change, asserted by the second rule below.
 */
class TabNavControllerLifetimeTest {

    @Test
    fun `HomeScreen does not create its own tab controller`() {
        val home = sourceFile("app/src/main/java/ephyra/app/ui/home/HomeScreen.kt")
        assertTrue(home.exists(), "HomeScreen.kt not found at the expected path")

        val text = home.readText()

        // The body is everything after the *parameter list*, which ends at the first `) {` at or
        // after the signature. The first honest version sliced with `substringBefore("\n)")`, which
        // cut at the parameter list's own closing paren and therefore produced an empty body — so
        // the gate passed on the exact defect it was written for. That is the eighth blind gate in
        // this programme's history, and it is why the slice is now anchored on `) {` and why the
        // falsification probe below is part of the suite rather than a one-off check.
        val signature = text.substringAfter("fun HomeScreen(")
        val body = signature.substringAfter(") {", missingDelimiterValue = "")
        assertTrue(
            body.isNotEmpty(),
            "could not locate the HomeScreen body; the gate would silently check nothing",
        )

        // A creation in the parameter list is a default value and is fine -- that is what previews
        // and tests use. A creation in the *body* is the defect.
        val bodyCreates = CREATES_CONTROLLER.containsMatchIn(body)
        assertTrue(
            !bodyCreates,
            "HomeScreen must not call rememberNavController() in its body. The tab controller is " +
                "owned by MainActivity so it outlives the Home composition; creating it here makes " +
                "it a child of the composition that navigating to a series detail disposes, which " +
                "destroys every tab's back stack and saved state.",
        )
    }

    @Test
    fun `MainActivity creates the tab controller outside the NavHost`() {
        val activity = sourceFile("app/src/main/java/ephyra/app/ui/main/MainActivity.kt")
        assertTrue(activity.exists(), "MainActivity.kt not found at the expected path")

        val text = activity.readText()
        assertTrue(
            CREATES_CONTROLLER.containsMatchIn(text),
            "MainActivity must own the tab controller, or nothing else can give it a lifetime that " +
                "outlives the Home composition.",
        )

        // The creation must come before the NavHost that passes it in. Ordering is the whole fix:
        // a controller created below its consumer would still be scoped to the wrong subtree.
        val created = text.indexOf("val bottomNavController = rememberNavController()")
        val consumed = text.indexOf("bottomNavController = bottomNavController")
        assertTrue(created >= 0, "MainActivity does not create a bottomNavController at all")
        assertTrue(
            consumed > created,
            "the tab controller is consumed before it is created; it must be hoisted above the " +
                "NavHost that hands it to HomeScreen",
        )
    }

    @Test
    fun `back at a tab root exits instead of hopping to the previous tab`() {
        val home = sourceFile("app/src/main/java/ephyra/app/ui/home/HomeScreen.kt")
        // Comments stripped: the prose above the tab shell *names* the removed walk to explain
        // it, so a raw readText() would let the description of the defect trip a rule about it.
        val text = codeOf(home)

        // **This rule is a deliberate reversal of an earlier one, on the owner's defect report
        // (2026-11-08).** The tab shell used to intercept back and walk a tab-visit history: back
        // at a main tab after visiting another main tab navigated to that tab - pressed again, it
        // swapped between the two most recent tabs - instead of leaving, and the gesture replayed
        // a tab-slide animation the predictive-back hand-off never previews. The earlier rule
        // asserted the walk existed; it encoded the defect as a contract. Back at a peer
        // destination now exits to the system, per Android's top-level guidance; each tab's
        // state survives through the tab strip's saveState/restoreState, not a history.
        assertTrue(
            !text.contains("BackHandler"),
            "HomeScreen must not intercept back at the tab shell: the tab-history walk hopped " +
                "between the two most recent tabs on back (the reported defect), where the system " +
                "back should exit through the predictive-back hand-off.",
        )
        for (token in listOf("tabHistory", "appendTabHistory", "previousTabIn", "dropLastTab")) {
            assertTrue(
                !text.contains(token),
                "the $token machinery of the removed back walk must not come back with it",
            )
        }

        // **Amended on the owner's second defect report (2026-11-11).** The exit above only held
        // at the start tab: tab switching left `Library` - the graph's start destination -
        // beneath every other tab, so back from History or Updates popped the tab stack and
        // dropped the user into Library - the very hop this test is named after - through pop
        // slots that also inverted the slide. Every tab-switch site must clear the stack, so no
        // tab is ever beneath another and the gesture reaches the system from every tab root.
        assertTrue(
            Regex("popUpTo\\(0\\)").containsMatchIn(text),
            "Tab switching must clear the tab stack (popUpTo(0)): with the start destination left " +
                "beneath every other tab, back from a non-start tab popped the stack and dropped " +
                "the user into Library instead of leaving through the system hand-off.",
        )
        assertTrue(
            Regex("inclusive = true").findAll(text).count() >= 3,
            "All three tab-switch sites - the openTab event, the bar item and the rail item - must " +
                "clear the stack inclusively; one site left behind reintroduces the hop from that tab.",
        )
    }

    @Test
    fun `the tab root list is declared once and shared`() {
        val home = sourceFile("app/src/main/java/ephyra/app/ui/home/HomeScreen.kt")
        val text = home.readText()

        // Two independent copies of the root list could disagree, and the failure would be
        // silent: the slide direction would stop matching the order of the buttons the user
        // taps. The list serves the tab index (and through it the slide axis); a second copy
        // would let the strip and the motion disagree.
        val declaration = text.indexOf("private val TAB_ROOT_ROUTES")
        assertTrue(declaration >= 0, "TAB_ROOT_ROUTES is not declared")
        assertTrue(
            Regex("\\bTAB_ROOT_ROUTES\\b").findAll(text).count() >= 2,
            "TAB_ROOT_ROUTES is declared but never used; the tab index must read it",
        )
    }

    @Test
    fun `the creation matcher distinguishes a body creation from a default parameter`() {
        // The gate turns on this distinction, so it is asserted directly rather than trusted.
        assertTrue(
            CREATES_CONTROLLER.containsMatchIn("    val bottomNavController = rememberNavController()"),
            "a body-level creation must be detected",
        )
        assertTrue(
            !CREATES_CONTROLLER.containsMatchIn(
                "    bottomNavController: NavHostController = rememberNavController(),",
            ),
            "a defaulted parameter is how previews and tests get a controller without a caller, and " +
                "must not be mistaken for the defect",
        )
    }

    private fun sourceFile(relative: String): File = File(TrackedFileNames.repositoryRoot(), relative)

    /** Source with comments removed, so prose describing a rule cannot satisfy or trip it. */
    private fun codeOf(file: File): String = file.readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("//.*"), "")

    private companion object {
        /**
         * A tab controller created as a local `val`.
         *
         * Anchored on `val <name> = rememberNavController()` so a defaulted constructor parameter,
         * which reads `name: Type = rememberNavController()`, cannot match.
         */
        val CREATES_CONTROLLER = Regex("""val\s+\w*NavController\w*\s*=\s*rememberNavController\(\)""")
    }
}
