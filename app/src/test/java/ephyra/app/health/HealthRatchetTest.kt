package ephyra.app.health

import org.junit.jupiter.api.Assertions.assertEquals
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
 * Two metrics deliberately use a different rule. `mainSourceFiles` is compared for exact
 * equality: it legitimately grows during the reconstruction, and an exact check is what keeps the
 * final legacy-deletion target measurable. `testSourceFiles` is a floor, because deleting a test
 * needs a recorded replacement rather than a silent removal.
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
    fun `main source files move only with a deliberate baseline update`() {
        val actual = projectSources().count { it.path.contains(mainPathSegment) }
        assertEquals(
            baseline.mainSourceFiles,
            actual,
            "main source files moved from ${baseline.mainSourceFiles} to $actual. " +
                "Growth is expected during the reconstruction, but the baseline must be updated " +
                "deliberately so the final legacy-deletion target stays measurable.",
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
                    it.name != selfName
            }
            .toList()
    }

    private fun buildFiles(): List<File> = File(root, "")
        .walkTopDown()
        .filter { it.isFile && it.name == "build.gradle.kts" && !it.path.contains(buildPathSegment) }
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
        val mainPathSegment = "${File.separator}src${File.separator}main${File.separator}"
        val testPathSegment = "${File.separator}src${File.separator}test${File.separator}"
        val androidTestPathSegment = "${File.separator}src${File.separator}androidTest${File.separator}"
    }
}
