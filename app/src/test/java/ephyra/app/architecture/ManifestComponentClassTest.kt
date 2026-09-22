package ephyra.app.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the packaged manifest against component declarations whose backing class is no longer
 * on the app's runtime classpath.
 *
 * This failure mode is startup-fatal *and* silent. Android instantiates every manifest-declared
 * `<provider>` in `ActivityThread.handleBindApplication`, which runs **before**
 * `Application.onCreate()`. A `ClassNotFoundException` raised there kills the process before the
 * app's own `GlobalExceptionHandler` / `StartupFailureActivity` can run, so all the user sees is
 * the app closing instantly — no crash dialog, no error screen.
 *
 * This is not hypothetical: removing the Shizuku dependency left
 * `android:name="rikka.shizuku.ShizukuProvider"` behind in `app/src/main/AndroidManifest.xml`,
 * and every launch of that build died during provider installation.
 */
class ManifestComponentClassTest {

    /** Manifest elements whose `android:name` is a class name (unlike `meta-data`/`intent-filter`). */
    private val componentTags = listOf(
        "application",
        "activity",
        "activity-alias",
        "service",
        "receiver",
        "provider",
        "instrumentation",
    )

    /** The `:app` module namespace, used to resolve relative names such as `.ui.main.MainActivity`. */
    private val appNamespace = "ephyra.app"

    @Test
    fun everyDeclaredComponentClassResolvesOnTheRuntimeClasspath() {
        val root = repositoryRoot()
        val manifests = appManifestFiles(root)
        assertTrue("No app/src/**/AndroidManifest.xml found under ${root.absolutePath}", manifests.isNotEmpty())

        val classLoader = requireNotNull(javaClass.classLoader) { "Test class loader is unavailable" }
        val unresolved = mutableListOf<String>()

        manifests.forEach { manifest ->
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest)
            val manifestPath = manifest.toRelativeString(root)

            componentTags.forEach { tag ->
                val nodes = document.getElementsByTagName(tag)
                for (index in 0 until nodes.length) {
                    val declaredName = nodes.item(index)
                        .attributes
                        ?.getNamedItem("android:name")
                        ?.nodeValue
                        ?.takeIf { it.isNotBlank() }
                        ?: continue

                    val className = if (declaredName.startsWith(".")) {
                        "$appNamespace$declaredName"
                    } else {
                        declaredName
                    }

                    if (!isLoadable(className, classLoader)) {
                        unresolved += "<$tag android:name=\"$declaredName\"> -> $className ($manifestPath)"
                    }
                }
            }
        }

        assertTrue(
            buildString {
                appendLine("The manifest declares components whose classes are missing from the app classpath:")
                unresolved.forEach { appendLine("  - $it") }
                appendLine()
                appendLine(
                    "Providers are instantiated before Application.onCreate(), so a missing provider " +
                        "class kills the process on launch with no crash screen. Restore the dependency " +
                        "that provided the class, or delete the stale manifest entry.",
                )
            },
            unresolved.isEmpty(),
        )
    }

    /**
     * `Class.forName` is used (rather than a resource lookup) so that linkage problems — such as a
     * class present in a jar but with an unresolvable superclass — count as failures too.
     */
    private fun isLoadable(className: String, classLoader: ClassLoader): Boolean =
        runCatching { Class.forName(className, false, classLoader) }.isSuccess

    private fun repositoryRoot(): File {
        var directory: File? = File(".").absoluteFile
        while (directory != null && !File(directory, "settings.gradle.kts").exists()) {
            directory = directory.parentFile
        }
        return requireNotNull(directory) {
            "Could not locate the repository root (settings.gradle.kts) from ${File(".").absolutePath}"
        }
    }

    /** All source manifests of the `:app` module, including build-variant source sets. */
    private fun appManifestFiles(root: File): List<File> =
        File(root, "app/src")
            .walkTopDown()
            .filter { it.isFile && it.name == "AndroidManifest.xml" }
            .sorted()
            .toList()
}
