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
    fun `back inside a tab is intercepted rather than leaving the app`() {
        val home = sourceFile("app/src/main/java/ephyra/app/ui/home/HomeScreen.kt")
        val text = home.readText()

        assertTrue(
            text.contains("BackHandler"),
            "HomeScreen installs no BackHandler, so pressing back inside a non-Library tab pops the " +
                "app-level stack and leaves the app from a screen the user only navigated within.",
        )
        assertTrue(
            BACK_POP_TO_ROOT.containsMatchIn(text),
            "the back handler must return to a tab root, not merely consume the event",
        )
    }

    @Test
    fun `the tab root list is declared once and shared`() {
        val home = sourceFile("app/src/main/java/ephyra/app/ui/home/HomeScreen.kt")
        val text = home.readText()

        // Two independent copies of the root list could disagree, and the failure would be silent:
        // back would stop working at a root, or would keep intercepting when already at one.
        val declaration = text.indexOf("private val TAB_ROOT_ROUTES")
        assertTrue(declaration >= 0, "TAB_ROOT_ROUTES is not declared")
        assertTrue(
            Regex("\\bTAB_ROOT_ROUTES\\b").findAll(text).count() >= 2,
            "TAB_ROOT_ROUTES is declared but never used; the back handler must compare against it",
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

    private companion object {
        /**
         * A tab controller created as a local `val`.
         *
         * Anchored on `val <name> = rememberNavController()` so a defaulted constructor parameter,
         * which reads `name: Type = rememberNavController()`, cannot match.
         */
        val CREATES_CONTROLLER = Regex("""val\s+\w*NavController\w*\s*=\s*rememberNavController\(\)""")

        /** A back handler that pops to a tab root rather than merely swallowing the event. */
        val BACK_POP_TO_ROOT = Regex(
            """BackHandler\s*\{[^}]*popBackStack\(""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
    }
}
