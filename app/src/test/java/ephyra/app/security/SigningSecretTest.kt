package ephyra.app.security

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * SEC-001 — no credential literal in version control.
 *
 * The original defect was concrete: `app/build.gradle.kts` carried
 * `storePassword = "ephyra"` and `keyPassword = "ephyra"` for the nightly signing config. A
 * keystore password committed to a repository is not a secret with limited blast radius; it is a
 * published one, and it is the password that authorises signing as the release identity.
 *
 * This gate exists because removing the literal once proves nothing. The failure mode repeats, and
 * it is easiest to introduce while wiring up a new build type or a new signing config. So the
 * check is structural: credential-valued keys in any build script must resolve through
 * [signingSecret], never through a literal.
 *
 * E2 by construction — it reads the build scripts from disk, so it fails in a plain JVM run with no
 * device, which is exactly where a credential leak must be caught.
 */
class SigningSecretTest {

    @Test
    fun `no build script assigns a literal password to a signing key`() {
        val offenders = buildFiles()
            .flatMap { file -> file.readText().lineSequence().toList() }
            .filter { line -> CREDENTIAL_ASSIGNMENT.matches(line) }
            .toList()

        assertTrue(
            offenders.isEmpty(),
            "Hardcoded signing credential(s) found:\n" +
                offenders.joinToString("\n") { "  $it" } +
                "\nRead signing secrets via signingSecret(\"name\"), which resolves from Gradle " +
                "properties or the EPHYRA_-prefixed environment variable. A literal here " +
                "publishes the release signing password.",
        )
    }

    @Test
    fun `nightly signing reads its secrets through the indirection`() {
        val app = File(repositoryRoot(), "app/build.gradle.kts")
        val text = app.readText()

        assertTrue(
            text.contains("signingSecret(\"nightlyStorePassword\")"),
            "The nightly signing config must source its store password via signingSecret().",
        )
        assertTrue(
            text.contains("signingSecret(\"nightlyKeyPassword\")"),
            "The nightly signing config must source its key password via signingSecret().",
        )
    }

    @Test
    fun `no unacknowledged keystore file is committed to the tree`() {
        val committed = committedKeystores()
            .map { it.relativeTo(repositoryRoot()).invariantSeparatorsPath }
            .toSet()

        val unacknowledged = committed - ACKNOWLEDGED_KEYSTORES
        assertTrue(
            unacknowledged.isEmpty(),
            "Keystore material must not be committed, and these are not acknowledged in " +
                "ACKNOWLEDGED_KEYSTORES: ${unacknowledged.sorted()}. A committed keystore plus a " +
                "known password lets anyone with repository access sign builds as this identity.",
        )
    }

    @Test
    fun `each acknowledged keystore still exists`() {
        val committed = committedKeystores()
            .map { it.relativeTo(repositoryRoot()).invariantSeparatorsPath }
            .toSet()

        val stale = ACKNOWLEDGED_KEYSTORES - committed
        assertTrue(
            stale.isEmpty(),
            "ACKNOWLEDGED_KEYSTORES still lists ${stale.sorted()}, which no longer exists. Remove " +
                "the acknowledgement so the exception list reflects reality, and treat removal of " +
                "the file as the prompt to rotate the key.",
        )
    }

    private fun committedKeystores(): List<File> = File(repositoryRoot(), "")
        .walkTopDown()
        .filter { it.isFile && it.extension.lowercase() in KEYSTORE_EXTENSIONS }
        .filterNot { it.path.contains("${File.separator}build${File.separator}") }
        .filterNot { it.path.contains("${File.separator}.git${File.separator}") }
        .filterNot { it.path.contains("${File.separator}.kilo${File.separator}") }
        .toList()

    private fun buildFiles(): List<File> = File(repositoryRoot(), "")
        .walkTopDown()
        .filter { it.isFile && it.name == "build.gradle.kts" }
        .filterNot { it.path.contains("${File.separator}build${File.separator}") }
        .filterNot { it.path.contains("${File.separator}.kilo${File.separator}") }
        .toList()

    private fun repositoryRoot(): File {
        var directory: File? = File(".").absoluteFile
        while (directory != null && !File(directory, "settings.gradle.kts").exists()) {
            directory = directory.parentFile
        }
        return requireNotNull(directory) { "Could not locate repository root" }
    }

    private companion object {
        val KEYSTORE_EXTENSIONS = setOf("jks", "keystore", "p12", "pfx")

        /**
         * SEC-001 residual debt, recorded rather than hidden.
         *
         * `app/nightly.keystore` is tracked in git, and its password was a literal in
         * `app/build.gradle.kts` until this change. Both facts together meant the nightly signing
         * identity was reproducible by anyone with repository access — removing the literal stops
         * new leaks but does not retract the published one.
         *
         * This entry exists so the gate stays meaningful: a *new* keystore fails immediately, and
         * the acknowledgement is itself required to still be necessary, so deleting the file forces
         * a deliberate edit rather than silently closing the exception. The real remediation is
         * rotation — a key that was published must be treated as compromised, and rotating it is an
         * operational action outside the build. Tracked in `doc/REBUILD_STATUS.md` as SEC-001.
         */
        val ACKNOWLEDGED_KEYSTORES = setOf("app/nightly.keystore")

        /**
         * Matches `storePassword = "literal"` / `keyPassword = 'literal'` style assignments.
         * A `null` right-hand side is fine — that is the "unset, fail later" path.
         */
        val CREDENTIAL_ASSIGNMENT = Regex(
            """(?i)^\s*(storePassword|keyPassword|storeFile|keyAlias)\s*=\s*(""" +
                """|"[^"]*"|'[^']*')\s*$""",
        )
    }
}
