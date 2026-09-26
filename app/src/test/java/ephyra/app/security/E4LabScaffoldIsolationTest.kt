package ephyra.app.security

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Keeps the `E4-lab` test scaffolding out of shippable builds.
 *
 * `E4LabFixtureSeeder` writes committed fixture media onto the device so the reader can be
 * exercised against known geometry, and `E4LabFixtureReceiver` exposes it over a broadcast. That
 * is exactly the kind of capability that must never ship: a debug receiver that writes to
 * external storage is a remote-writable-content surface, and if it were ever registered in the
 * main manifest it would be present in every release.
 *
 * The current isolation is the **source set**, not a runtime flag. That is the stronger form -- a
 * flag can be forgotten, a source set cannot -- so this asserts the source set rather than
 * trusting a comment. It is a structural check, derived from the tree, with no stored baseline.
 */
class E4LabScaffoldIsolationTest {

    @Test
    fun `no E4-lab scaffolding exists in a shippable source set`() {
        val offenders = TrackedFileNames.inMainSources()
            .filter { it.contains("E4Lab") }
        assertTrue(
            offenders.isEmpty(),
            "E4-lab scaffolding must not exist under a shippable source set, but found:\n  " +
                offenders.joinToString("\n  ") +
                "\nIt is intentionally debug-only: it writes fixture media to external storage " +
                "and is reachable by broadcast. Move it back to app/src/debug.",
        )
    }

    @Test
    fun `the release manifest does not register an E4-lab receiver`() {
        val manifest = File(TrackedFileNames.repositoryRoot(), "app/src/main/AndroidManifest.xml")
        assertTrue(manifest.isFile, "app/src/main/AndroidManifest.xml is missing")
        val text = manifest.readText()
        assertTrue(
            !text.contains("E4LAB"),
            "The shippable manifest registers an E4-lab receiver. Debug-only scaffolding must " +
                "never appear in app/src/main/AndroidManifest.xml.",
        )
    }

    @Test
    fun `the scaffolding this gate guards actually exists in debug`() {
        // Without this, emptying app/src/debug would satisfy the two rules above vacuously and
        // the gate would guard nothing while reporting green.
        val debug = TrackedFileNames.inSourceSet("app/src/debug")
            .filter { it.contains("E4Lab") }
        assertTrue(
            debug.isNotEmpty(),
            "No E4-lab scaffolding found under app/src/debug. The isolation rules above would " +
                "pass vacuously, so this asserts the thing being protected still exists.",
        )
    }
}
