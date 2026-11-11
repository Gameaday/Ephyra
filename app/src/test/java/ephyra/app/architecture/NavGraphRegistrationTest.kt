package ephyra.app.architecture

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Structural gates for the root navigation graph's registration contract.
 *
 * **The defect this prevents.** The graph's destinations are registered in three places nothing
 * couples: `MainActivity` (its inline destinations plus every feature's `FeatureApi`),
 * `HomeScreen` (the five tab roots of the nested tab graph), and — for the features — one
 * `FeatureApi` implementation per feature module. `MainActivity` used to wrap each feature's
 * registration in a catch-and-log, so a failing registration left the graph silently missing
 * every destination that feature owns: the owner tapped More -> About (2026-11-11) and crashed
 * with "Navigation destination that matches route about cannot be found in the navigation
 * graph" — a registration-time defect surfacing as a random user tap, its real cause visible
 * only in a logcat line nobody reads. No test read the graph's side of the contract, so nothing
 * could fail before the crash did.
 *
 * Two rules, both read from source following the pattern established by
 * `MotionConsistencyTest`: a behavioural test would need a device, and the contract is textual —
 * a route is either named at a `composable` registration or it exists in no graph.
 *
 * Known, deliberate limits, so the gates stay honest: a navigation through a variable
 * (`navigate(item.route)`, or a route built into a local first) cannot be resolved to a
 * destination name and is out of scope, and a route that is defined but never navigated
 * (`MatchResults`) imposes no requirement.
 */
class NavGraphRegistrationTest {

    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "app/src/main/java/ephyra/app/ui/main/MainActivity.kt").exists()) return dir
            dir = dir.parentFile
        }
        throw AssertionError("repo root not found above ${File(".").absolutePath}")
    }

    /** Source with prose removed, so a comment describing a mechanism cannot satisfy a rule. */
    private fun codeOf(relativePath: String): String = File(repoRoot(), relativePath)
        .readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("//.*"), "")

    /** Every main-source-set Kotlin file under [relativePath], prose-stripped, as one text. */
    private fun codeTree(relativePath: String): String = File(repoRoot(), relativePath)
        .walkTopDown()
        .filter {
            it.isFile && it.extension == "kt" &&
                it.path.contains("/src/main/") &&
                !it.path.contains("/build/") &&
                !it.path.contains("/.git/")
        }
        .joinToString("\n") { it.readText() }
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("//.*"), "")

    private val mainActivity = "app/src/main/java/ephyra/app/ui/main/MainActivity.kt"
    private val homeScreen = "app/src/main/java/ephyra/app/ui/home/HomeScreen.kt"
    private val screenRoutes =
        "presentation-core/src/main/java/ephyra/presentation/core/ui/navigation/ScreenRoutes.kt"

    /** Every destination name the app defines on [ephyra.presentation.core.ui.navigation.ScreenRoutes]. */
    private fun routeMembers(): Set<String> = Regex(
        """(?:data )?object\s+(\w+)\s*:\s*ScreenRoutes\(""",
    ).findAll(codeOf(screenRoutes))
        .map { it.groupValues[1] }
        .toSet()

    /** Every destination name on the [ephyra.presentation.core.ui.navigation.Screen] sealed interface. */
    private fun screenMembers(): Set<String> {
        val block = codeOf(screenRoutes).substringAfter("sealed interface Screen")
        return Regex("""(?:data )?(?:object|class)\s+(\w+)""")
            .findAll(block)
            .map { it.groupValues[1] }
            .toSet()
    }

    /** The three registration sites: MainActivity's inline destinations, the tab graph, the FeatureApis. */
    private fun registrationCode(): String {
        val featureApis = File(repoRoot(), "feature")
            .walkTopDown()
            .filter { it.isFile && it.name.endsWith("FeatureApi.kt") && !it.path.contains("/build/") }
            .map { it.readText() }
            .joinToString("\n")
        return codeOf(mainActivity) + "\n" + codeOf(homeScreen) + "\n" + featureApis
    }

    /**
     * Every route the app can navigate to must be registered somewhere, or the first tap that
     * reaches it crashes with "Navigation destination ... cannot be found in the navigation
     * graph" — the owner's More -> About crash (2026-11-11), which no compiler error and no
     * test could see because registration and navigation live in different modules.
     */
    @Test
    fun `every route the app navigates to is registered at a composable`() {
        val sources = codeTree("app/src/main/java") + "\n" +
            codeTree("feature") + "\n" +
            codeTree("presentation-core/src/main/java")
        val registrations = registrationCode()

        val stringNavigations = Regex("""navigate\(\s*ScreenRoutes\.(\w+)\.(?:route|createRoute)""")
            .findAll(sources)
            .map { it.groupValues[1] }
            .toSortedSet()
        assertTrue(
            stringNavigations.isNotEmpty(),
            "The scan must find navigate sites, or it is reading the wrong tree and gates nothing.",
        )

        val unregistered = stringNavigations.filter { name ->
            !Regex("""composable\(\s*(route\s*=\s*)?ScreenRoutes\.$name\.route""")
                .containsMatchIn(registrations)
        }
        assertTrue(
            unregistered.isEmpty(),
            "Routes navigated somewhere but registered nowhere — tapping them crashes with " +
                "'Navigation destination ... cannot be found': $unregistered. Register each in " +
                "MainActivity, HomeScreen's tab graph, or the owning FeatureApi.",
        )

        // Typed-route navigations (`navigate(Screen.MangaDetails(...))`) resolve against a
        // `composable<Screen.X>` registration, or against the string registration of the
        // same-named ScreenRoutes member, so a member registered only one way still counts.
        val typedNavigations = Regex("""navigate\(\s*(?:[\w.]*\.)?Screen\.(\w+)""")
            .findAll(sources)
            .map { it.groupValues[1] }
            .filter { it in screenMembers() }
            .toSortedSet()
        val typedUnregistered = typedNavigations.filter { name ->
            val typed = Regex("""composable<(?:[\w.]*\.)?Screen\.$name[>(]""").containsMatchIn(registrations)
            val string = name in routeMembers() &&
                Regex("""composable\(\s*(route\s*=\s*)?ScreenRoutes\.$name\.route""")
                    .containsMatchIn(registrations)
            !typed && !string
        }
        assertTrue(
            typedUnregistered.isEmpty(),
            "Typed routes navigated somewhere but registered nowhere — tapping them crashes " +
                "with 'Navigation destination ... cannot be found': $typedUnregistered.",
        )
    }

    /**
     * A failed `FeatureApi.register` must fail composition, not write a logcat line and
     * continue. The catch-and-log this replaces left a graph that was silently missing every
     * destination of the failed feature, and the defect surfaced later as the owner's
     * More -> About crash (2026-11-11) instead of at startup where the cause was on screen.
     * A half-built graph cannot be repaired at runtime, so the failure belongs here, naming
     * the feature that failed.
     */
    @Test
    fun `a feature registration failure is not swallowed`() {
        val window = codeOf(mainActivity)
            .substringAfter("featureApis.forEach")
            .substringBefore("HandleOnNewIntent")

        assertTrue(
            window.contains("featureApi.register(this, navController)"),
            "MainActivity must register every FeatureApi on the root graph; if the call moved, " +
                "move this gate with it — the registration loop is the contract's enforcement point.",
        )
        assertFalse(
            window.contains("logcat"),
            "The registration loop must not downgrade a failure to a log: a swallowed failure " +
                "leaves the graph silently missing every destination the failed feature owns, " +
                "and the defect only surfaces when the user first taps one of them — the " +
                "owner's More -> About crash (2026-11-11).",
        )
        assertTrue(
            window.contains("throw"),
            "A failed registration must throw: the graph would be missing destinations the " +
                "user can still reach, and that state cannot be repaired at runtime.",
        )
    }
}
