package ephyra.app.architecture

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Structural gate for **NAV-001**: app-level navigation signals must be instance state owned by the
 * composition, not global mutable singletons.
 *
 * **The defect this prevents.** `ScreenRoutes.kt` declared two `object` singletons:
 * `BottomNavVisibilityController` (a `MutableStateFlow<Boolean>`) and `NavigationEvents` (a
 * `MutableSharedFlow<String>` for tab reselect). Both were process-global, both outlived the
 * Activity that should own them, and any module could mutate either. The concrete hazards:
 *
 * - A test that hid the bottom bar mutated state that every later test in the same process read,
 *   so a passing test could make an unrelated test fail depending on execution order. This is the
 *   same defect class as `B-046` and the `MangaViewModelTest` flake, but in shared global state
 *   rather than in class-level mock `val`s.
 * - A feature could react to another feature's tab reselect, and that coupling was invisible in
 *   the build graph because the bus was reached through a singleton rather than a parameter.
 *
 * **What this gate does and does not assert.** It asserts that the two singletons are gone and that
 * the shell provides the coordinator. It does **not** assert that the coordinator behaves
 * correctly — `NavigationCoordinatorTest` covers hide/show/reselect semantics. The split matters: a
 * behavioural test of the coordinator would pass with the singletons still present, because it
 * would be testing a fresh instance, not the one production uses. That is exactly the gap that
 * let this defect survive: every existing test was green while the production path went through a
 * global.
 */
class NavigationCoordinatorOwnershipTest {

    private fun sourceFile(relativePath: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relativePath)
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        return File(".", relativePath).absoluteFile
    }

    private fun textOf(relativePath: String): String {
        val file = sourceFile(relativePath)
        assertTrue(file.exists(), "$relativePath not found")
        return file.readText()
    }

    @Test
    fun `the global navigation singletons are gone`() {
        val screenRoutes =
            textOf("presentation-core/src/main/java/ephyra/presentation/core/ui/navigation/ScreenRoutes.kt")

        for (forbidden in listOf("object BottomNavVisibilityController", "object NavigationEvents")) {
            assertFalse(
                screenRoutes.contains(forbidden),
                "`$forbidden` is a process-global mutable singleton again. NAV-001 moved " +
                    "bottom-bar visibility and tab reselect onto `NavigationCoordinator`, " +
                    "provided by the shell through `LocalNavigationCoordinator`, so state cannot " +
                    "outlive the Activity that owns it and one test cannot mutate another's.",
            )
        }

        // The two flow types were only ever imported for those singletons. Leaving the imports
        // behind would suggest the file still owns navigation state.
        assertFalse(
            screenRoutes.contains("import kotlinx.coroutines.flow.MutableStateFlow") ||
                screenRoutes.contains("import kotlinx.coroutines.flow.MutableSharedFlow"),
            "ScreenRoutes.kt no longer owns navigation state, so it must not import the mutable " +
                "flow types it needed only for the deleted singletons.",
        )
    }

    @Test
    fun `no module reaches the navigation singletons by name`() {
        // Comments are stripped before matching, because the removal note in ScreenRoutes.kt names
        // both singletons and would otherwise trip this on its own documentation.
        val offenders = mutableListOf<String>()
        for (path in trackedKotlinFiles()) {
            if (path.name == "ScreenRoutes.kt" || path.name == "NavigationCoordinator.kt") continue
            if (path.name == "NavigationCoordinatorOwnershipTest.kt") continue
            for (line in path.readLines()) {
                val code = line.substringBefore("//").trim()
                if (code.contains("BottomNavVisibilityController") || code.contains("NavigationEvents")) {
                    offenders += "${path.name}: $code"
                }
            }
        }
        assertTrue(
            offenders.isEmpty(),
            "navigation state must be reached through `LocalNavigationCoordinator`, not a global " +
                "singleton:\n  " + offenders.joinToString("\n  "),
        )
    }

    @Test
    fun `the shell provides the coordinator`() {
        val main = textOf("app/src/main/java/ephyra/app/ui/main/MainActivity.kt")

        assertTrue(
            main.contains("LocalNavigationCoordinator provides navigationCoordinator"),
            "MainActivity must provide the NavigationCoordinator, or every consumer that resolves " +
                "it from the composition will fail at runtime. It is created with `remember` so it " +
                "is scoped to the Activity's composition rather than to a destination that " +
                "navigation disposes.",
        )
        assertTrue(
            main.contains("remember { NavigationCoordinator() }"),
            "The coordinator must be remembered at Activity scope. Creating it per-composition " +
                "would reintroduce the defect in a subtler form: two screens in the same Activity " +
                "would hold different instances and neither would see the other's state.",
        )
    }

    @Test
    fun `reselect consumers filter by their own route`() {
        // The route filter is what stops one tab's reselect waking another tab's collector. It is
        // the only thing standing between a shared event stream and every tab reacting to every
        // other tab, so its absence is a real defect rather than a style preference.
        val consumers =
            listOf(
                "feature/library/src/main/kotlin/ephyra/feature/library/LibraryScreen.kt" to
                    "ScreenRoutes.Library.route",
                "feature/history/src/main/kotlin/ephyra/feature/history/HistoryScreen.kt" to
                    "ScreenRoutes.History.route",
                "feature/updates/src/main/kotlin/ephyra/feature/updates/UpdatesScreen.kt" to
                    "ScreenRoutes.Updates.route",
            )
        for ((path, route) in consumers) {
            val text = textOf(path)
            assertTrue(
                text.contains("navigationCoordinator.reselectEvents"),
                "$path must collect reselect events from the coordinator.",
            )
            assertTrue(
                text.contains("filter { it == $route }"),
                "$path must filter reselect events by $route. The reselect stream is shared by " +
                    "every tab, so an unfiltered collector reacts to other tabs' taps.",
            )
        }
    }

    private fun trackedKotlinFiles(): List<File> {
        val out = mutableListOf<File>()
        for (module in MODULES) {
            val root = sourceFile(module)
            if (!root.exists()) continue
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { out += it }
        }
        return out
    }

    private companion object {
        val MODULES =
            listOf(
                "app/src/main",
                "presentation-core/src/main",
                "feature/library/src/main",
                "feature/history/src/main",
                "feature/updates/src/main",
            )
    }
}
