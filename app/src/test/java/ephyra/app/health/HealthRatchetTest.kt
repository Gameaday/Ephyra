package ephyra.app.health

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Repository health ratchets.
 *
 * Ceilings (module count, dependency edges, TODO/FIXME, deprecated markers) fail the build when
 * exceeded, so health debt cannot silently accumulate. Lowering a ceiling is a deliberate act and
 * is the mechanism that makes the end state strictly better than the start.
 *
 * Two metrics deliberately use a different rule. `testSourceFiles` is a floor, because deleting a
 * test needs a recorded replacement rather than a silent removal. `mainSourceFiles` is also a
 * ceiling rather than an exact match: the reconstruction adds contract types by design, and an
 * exact rule turned every legitimate addition into a forced manual baseline edit, which trains
 * people to bump the number without reading it. The legacy-deletion target is measured by
 * watching the ceiling's value over time, not by the equality of a single run.
 *
 * `releaseApkMibPerAbi` is recorded but NOT gated. Measuring it requires a full release build,
 * which is neither cheap nor deterministic enough to run on every change; it is reported in
 * doc/BUILD_HEALTH.md and measured on a schedule.
 *
 * These metrics are deterministic and cheap, so they gate every change. Timing budgets are
 * deliberately excluded: they are noisy and are measured on a schedule (see doc/BUILD_HEALTH.md).
 */
class HealthRatchetTest {
    private val root = repositoryRoot()
    private val baseline = loadBaseline()

    @Test
    fun `module count does not grow without a deliberate baseline update`() {
        val settings = File(root, "settings.gradle.kts").readText()
        val actual = Regex("""include\("([^"]+)"\)""").findAll(settings).count()
        assertTrue(
            actual <= baseline.moduleCount,
            "Module count regressed: $actual > ${baseline.moduleCount}. " +
                "Every added module costs configuration and aggregation time. " +
                "Prefer a package boundary inside an existing module.",
        )
    }

    @Test
    fun `inter-project dependency edges do not grow`() {
        var edges = 0
        buildFiles().forEach { edges += Regex("""projects\.[a-zA-Z]+""").findAll(it.readText()).count() }
        assertTrue(
            edges <= baseline.projectDependencyEdges,
            "Dependency edges regressed: $edges > ${baseline.projectDependencyEdges}. " +
                "New edges cost build time and usually indicate a layering leak.",
        )
    }

    @Test
    fun `unimplemented markers do not accumulate`() {
        val actual = projectSources()
            .sumOf { Regex("""\bTODO\b|\bFIXME\b""").findAll(it.readText()).count() }
        assertTrue(
            actual <= baseline.todoFixmeMarkers,
            "TODO/FIXME markers regressed: $actual > ${baseline.todoFixmeMarkers}. " +
                "Track intentional debt in doc/REBUILD_STATUS.md, not in code comments.",
        )
    }

    @Test
    fun `deprecated markers do not accumulate`() {
        val actual = projectSources()
            .sumOf { Regex("""@Deprecated""").findAll(it.readText()).count() }
        assertTrue(
            actual <= baseline.deprecatedMarkers,
            "Deprecated markers regressed: $actual > ${baseline.deprecatedMarkers}. " +
                "Each @Deprecated is a bridge to a replacement path this program is meant to remove. " +
                "Deleting the legacy call site is cheaper than keeping the bridge.",
        )
    }

    @Test
    fun `main source files do not grow without a deliberate baseline update`() {
        val actual = projectSources().count { it.path.contains(mainPathSegment) }
        assertTrue(
            actual <= baseline.mainSourceFiles,
            "main source files grew from ${baseline.mainSourceFiles} to $actual. " +
                "Growth is expected during the reconstruction, so raise the baseline deliberately " +
                "in health-baseline.json and BUILD_HEALTH.md rather than leaving it to drift.",
        )
    }

    @Test
    fun `test source files are not removed`() {
        val actual = projectSources().count {
            it.path.contains(testPathSegment) || it.path.contains(androidTestPathSegment)
        }
        assertTrue(
            actual >= baseline.testSourceFiles,
            "Test source files fell from ${baseline.testSourceFiles} to $actual. " +
                "Deleting tests requires a recorded replacement, not a silent removal.",
        )
    }

    /**
     * Sources that represent project debt. This file is excluded because its own pattern
     * literals would otherwise be counted as TODO/FIXME markers and the ratchet would fail
     * on itself.
     */
    private fun projectSources(): List<File> {
        val selfName = javaClass.simpleName + ".kt"
        return File(root, "")
            .walkTopDown()
            .filter {
                it.isFile &&
                    (it.extension == "kt" || it.extension == "java") &&
                    !it.path.contains(buildPathSegment) &&
                    !it.path.contains(gitPathSegment) &&
                    !it.path.contains(toolingPathSegment) &&
                    it.name != selfName
            }
            .toList()
    }

    private fun buildFiles(): List<File> = File(root, "")
        .walkTopDown()
        .filter {
            it.isFile &&
                it.name == "build.gradle.kts" &&
                !it.path.contains(buildPathSegment) &&
                !it.path.contains(toolingPathSegment)
        }
        .toList()

    private fun repositoryRoot(): File {
        var directory: File? = File(".").absoluteFile
        while (directory != null && !File(directory, "settings.gradle.kts").exists()) {
            directory = directory.parentFile
        }
        return requireNotNull(directory) { "Could not locate repository root from ${File(".").absolutePath}" }
    }

    private fun loadBaseline(): HealthBaseline {
        val loader = requireNotNull(javaClass.classLoader) { "Test class loader unavailable" }
        val stream = requireNotNull(loader.getResourceAsStream("health-baseline.json")) {
            "health-baseline.json is missing from test resources"
        }
        return stream.use { HealthBaseline.parse(it.readBytes().decodeToString()) }
    }

    private companion object {
        val buildPathSegment = "${File.separator}build${File.separator}"
        val gitPathSegment = "${File.separator}.git${File.separator}"

        /**
         * Agent Manager worktrees live at `.kilo/worktrees/<name>/` and contain a full second copy
         * of the repository. They are tooling scratch space, not project sources, so counting them
         * makes every metric report the project as roughly doubled.
         *
         * This was not hypothetical: with one worktree present, `mainSourceFiles` read 2432 against
         * a 1217 ceiling and `projectDependencyEdges` read 332 against a 166 ceiling, while the
         * real tree measured exactly 1217 and exactly 166. A ceiling ratchet that fires on the mere
         * existence of a worktree cannot gate anything — it would either block every agent run or
         * train people to raise ceilings. The fix is to exclude the copy, not to re-baseline
         * against it.
         */
        val toolingPathSegment = "${File.separator}.kilo${File.separator}"
        val mainPathSegment = "${File.separator}src${File.separator}main${File.separator}"
        val testPathSegment = "${File.separator}src${File.separator}test${File.separator}"
        val androidTestPathSegment = "${File.separator}src${File.separator}androidTest${File.separator}"
    }
}
