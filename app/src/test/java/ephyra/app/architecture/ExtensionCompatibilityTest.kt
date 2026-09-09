package ephyra.app.architecture

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the runtime classpath contract with dynamically loaded extension APKs.
 *
 * Extension APKs are compiled against the host application's classes. If the host stops
 * shipping a class an extension references (e.g. `okhttp3.zstd.Zstd` from the separate
 * `com.squareup.okhttp3:okhttp-zstd` OkHttp module), every source network call fails with
 * `NoClassDefFoundError` at runtime — wrapped by `ExtensionCallBoundary` as
 * "Source 'X' encountered an error" — which silently breaks chapter loading, search,
 * covers, and library actions on source-driven screens.
 */
class ExtensionCompatibilityTest {

    @Test
    fun `okhttp zstd classes are on the runtime classpath for extension APKs`() {
        val requiredClasses = listOf(
            "okhttp3.zstd.Zstd",
        )

        requiredClasses.forEach { className ->
            val found = try {
                Class.forName(className) != null
            } catch (_: ClassNotFoundException) {
                false
            } catch (_: LinkageError) {
                false
            }

            assertTrue(
                "$className must be on the runtime classpath. " +
                    "Extension APKs reference it, and removing the 'okhttp-zstd' dependency " +
                    "from :core:common breaks all network calls from dynamically loaded sources. " +
                    "See doc/MIGRATION_PLAN.md Phase 12 and CHANGELOG.",
                found,
            )
        }
    }
}
