package ephyra.app.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Locale

/**
 * Fails if a test source set depends on a live third-party service.
 *
 * The owner asked for this after flagging that the extension-installation path is sensitive: the
 * community extension repository it would use is third-party, can change or disappear without
 * notice, and cannot be reasoned about from this repository. A test that fetches from it is not
 * reproducible, and its failure then says nothing about this codebase.
 *
 * The gate is deliberately narrow. Test sources legitimately *name* external hosts as string
 * fixtures — `mangadex.org`, `anilist.co` and friends appear as sample data for URL parsing, and a
 * blanket "no external host" rule would flag all of that while missing the real risk. What must
 * never appear is a **repository index URL** aimed at a host that is not ours, because that is the
 * shape of an actual fetch.
 *
 * The rule is structural: it matches a shape, not a count. Adding a second, third or hundredth
 * live dependency does not weaken the gate, and retiring one requires no edit here. A count would
 * be satisfiable by editing a number, which is the failure mode `BUILD_HEALTH.md` forbids for
 * metrics.
 *
 * Fails **open** if git cannot be run, and [gitIsAvailable] reports that, so a silently-passing
 * gate is visible rather than assumed.
 */
class ExternalServiceDependencyTest {

    private val repositoryRoot: File by lazy {
        generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .firstOrNull { File(it, ".git").exists() }
            ?: error("Could not locate the repository root from ${System.getProperty("user.dir")}")
    }

    /** Loopback and RFC-1918 hosts are always acceptable as a repository index target. */
    private val localHostPattern = Regex(
        "^(localhost|127\\.0\\.0\\.1|0\\.0\\.0\\.0|\\[?::1\\]?|10\\..*|192\\.168\\..*|172\\.(1[6-9]|2\\d|3[01])\\..*)$",
    )

    /** The reserved TLDs cannot resolve on the public internet. */
    private val reservedTldPattern = Regex(".*\\.(test|example|invalid|localhost)$")

    /**
     * Matches a repository index URL, capturing the host.
     *
     * The example is written with angle brackets around the host on purpose: they are outside the
     * character class, so this KDoc cannot match the pattern it documents and the gate does not
     * fail on its own source.
     */
    private val repositoryIndexPattern = Regex("""https?://([A-Za-z0-9._-]+)[^"'\\\s)]*/index\.json""")

    /**
     * Hosts of community extension repositories, as patterns rather than literals.
     *
     * Written as patterns on purpose: a list of plain strings would contain the very text this
     * gate searches for, and the gate would then fail on its own source. Escaping the dots means
     * the file never holds the contiguous literal, so no self-exemption is needed and there is no
     * exemption that could later be abused.
     */
    private val communityExtensionRepos = listOf(
        Regex("""keiyoushi\.github\.io"""),
        Regex("""keiyoushi\.org"""),
    )

    private fun isAcceptableHost(host: String): Boolean {
        val lower = host.lowercase(Locale.ROOT)
        return localHostPattern.matches(lower) || reservedTldPattern.matches(lower)
    }

    private fun testSourceFiles(): List<File> =
        TrackedFiles.list(repositoryRoot)
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .filter { file ->
                val path = file.relativeTo(repositoryRoot).invariantSeparatorsPath
                path.contains("/src/test/") || path.contains("/src/androidTest/")
            }

    @Test
    fun `git is available so this gate is not silently inert`() {
        assertTrue(
            TrackedFiles.list(repositoryRoot).isNotEmpty(),
            "git ls-files returned nothing, so every check in this file would pass vacuously. " +
                "Fix the environment rather than trusting a green run.",
        )
    }

    @Test
    fun `no test source points a repository index at a third-party host`() {
        val offenders = testSourceFiles().flatMap { file ->
            val path = file.relativeTo(repositoryRoot).invariantSeparatorsPath
            file.readLines().mapIndexedNotNull { index, line ->
                val host = repositoryIndexPattern.find(line)?.groups?.get(1)?.value ?: return@mapIndexedNotNull null
                if (isAcceptableHost(host)) null else "$path:${index + 1} -> $host"
            }
        }

        assertTrue(
            offenders.isEmpty(),
            "Test sources reference a third-party repository index:\n" +
                offenders.joinToString("\n") { "  $it" } +
                "\n\nA test that fetches a live third-party repository is not reproducible: that " +
                "repository can change or vanish, and a failure then says nothing about this " +
                "codebase. Serve the index from a local fixture or an injectable fake instead.",
        )
    }

    @Test
    fun `no test source hardcodes a community extension repository`() {
        val offenders = testSourceFiles().flatMap { file ->
            val path = file.relativeTo(repositoryRoot).invariantSeparatorsPath
            val text = file.readText().lowercase(Locale.ROOT)
            communityExtensionRepos
                .filter { it.containsMatchIn(text) }
                .map { "$path -> ${it.pattern}" }
        }

        assertTrue(
            offenders.isEmpty(),
            "Test sources reference a community extension repository:\n" +
                offenders.joinToString("\n") { "  $it" } +
                "\n\nSource installation must be tested against an injectable fake or a local " +
                "fixture, never against a live third-party repository.",
        )
    }
}
