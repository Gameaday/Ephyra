package ephyra.app.architecture

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Static and dynamic verification tests to catch "build-only" failures caused by
 * R8 code shrinking, optimization, and resource stripping.
 *
 * R8 issues (like ClassNotFoundException or NoSuchMethodException) happen because classes
 * accessed via reflection are stripped as dead code or obfuscated. Since standard unit tests
 * run on debug variants with R8 disabled, they cannot catch these issues.
 *
 * This test class bridges the gap by programmatically parsing R8's output files:
 * 1. `seeds.txt` - Lists all classes and members matching keep rules (verifying they are kept).
 * 2. `usage.txt` - Lists all dead code stripped by R8 (verifying critical shims aren't stripped).
 *
 * It uses JUnit's `assumeTrue` to gracefully skip when running on standard un-minified debug builds,
 * but runs fully during minified release or benchmark builds.
 */
class R8OptimizationTest {

    private val mappingDirectories = listOf(
        File("../../app/build/outputs/mapping/release"),
        File("../../app/build/outputs/mapping/preview"),
        File("../../app/build/outputs/mapping/benchmark"),
        File("build/outputs/mapping/release"),
        File("build/outputs/mapping/preview"),
        File("build/outputs/mapping/benchmark")
    )

    private fun findActiveMappingDirectory(): File? {
        return mappingDirectories.firstOrNull { it.exists() && it.isDirectory }
    }

    /**
     * Verifies that critical classes (such as the Application class, launcher activities,
     * and startup watchdogs) match active `-keep` rules and are kept in the final APK.
     */
    @Test
    fun verifyR8SeedsVerifyCriticalClassesAreKept() {
        val mappingDir = findActiveMappingDirectory()
        // Gracefully skip if R8 hasn't run yet (typical for local debug test runs)
        assumeTrue(
            "R8 output mapping directory not found. Run a minified build (e.g. ./gradlew :app:assembleRelease) to run R8 tests.",
            mappingDir != null
        )

        val seedsFile = File(mappingDir, "seeds.txt")
        assumeTrue("seeds.txt not found under $mappingDir", seedsFile.exists())

        val keptClasses = seedsFile.readLines().toSet()

        // Critical entry points and components that MUST be kept by R8 rules
        val criticalClassesToKeep = listOf(
            "ephyra.app.App",
            "ephyra.app.ui.main.MainActivity",
            "ephyra.app.ui.deeplink.DeepLinkActivity",
            "ephyra.app.startup.StartupGuard"
        )

        criticalClassesToKeep.forEach { className ->
            val isKept = keptClasses.any { it.contains(className) }
            assertTrue(
                "Critical class $className must be kept by Proguard/R8 rules to prevent startup crash (not found in seeds.txt)",
                isKept
            )
        }
    }

    /**
     * Verifies that reflection-sensitive shims, database adapters, or serialization
     * components are NOT stripped out by R8 dead code elimination.
     */
    @Test
    fun verifyR8UsageVerifiesReflectionSensitiveClassesAreNotStripped() {
        val mappingDir = findActiveMappingDirectory()
        assumeTrue("R8 output mapping directory not found.", mappingDir != null)

        val usageFile = File(mappingDir, "usage.txt")
        assumeTrue("usage.txt not found under $mappingDir", usageFile.exists())

        val strippedClasses = usageFile.readLines()

        // Reflection-sensitive classes or packages that must never be stripped as dead code
        val reflectionSensitivePrefixes = listOf(
            "ephyra.app.AppInfo" // Crucial app metadata shim used by external extensions
        )

        reflectionSensitivePrefixes.forEach { prefix ->
            val wasStripped = strippedClasses.any { it.trim().startsWith(prefix) }
            assertTrue(
                "Reflection-sensitive class or member starting with '$prefix' was stripped by R8! Add a keep rule in proguard-rules.pro.",
                !wasStripped
            )
        }
    }
}
