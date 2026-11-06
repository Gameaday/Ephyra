package ephyra.domain.source.registry

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The single-owner gate `ADR-0012` requires, in the `SourceApiBoundaryTest` style.
 *
 * **Why a source scan rather than a compile error.** `profiled_domains_list` is a preference
 * *key* — the compiler cannot see who reads it, and neither the type system nor any IDE
 * inspection distinguishes one reader of the key from another. Before `SRC-011`, three
 * components answered "which sources exist" by reading this key independently, and their
 * disagreement was indistinguishable from corruption at the point of use.
 *
 * After `ADR-0017` the registry itself no longer reads the key: `profiled_domains_list` had no
 * writer since the heuristic engine was removed (`ADR-0015`), so the preference — and the
 * profile path behind it — was deleted outright. The gate stays, tightened to *nobody*: the day
 * someone reintroduces a reader of the dead key, this fails first with a message that says why,
 * instead of failing later as a user-visible defect.
 *
 * Tests, not just production code, keep the gate honest: only `src/main` is scanned, because a
 * test may set up state through the preference in order to observe it.
 */
class SourceRegistryOwnershipTest {

    @Test
    fun `nobody reads the profiled-domains preference`() {
        val root = repositoryRoot()
        val offenders = productionSources(root)
            .mapNotNull { file ->
                if (file.readText().contains("profiled_domains_list")) {
                    file.relativeTo(root).invariantSeparatorsPath
                } else {
                    null
                }
            }

        assertTrue(
            offenders.isEmpty(),
            "profiled_domains_list is dead (ADR-0017 deleted the profile path that read it):\n" +
                offenders.joinToString("\n") { " - $it reads the key directly" } +
                "\nSource existence belongs to SourceRegistry; read it from it.",
        )
    }

    @Test
    fun `production code carries no hardcoded default-domain trio`() {
        val root = repositoryRoot()
        // The pre-ADR-0012 code injected a default trio (manganato.com, asuratoons.com, …) as a
        // hidden second owner of existence: it made "no sources configured" indistinguishable
        // from "these three" and named sites the app could not serve. Quoted string literals
        // only — prose in a comment is not a claim a running build can act on.
        val forbidden = listOf("\"manganato", "\"asuratoons")
        val offenders = productionSources(root).flatMap { file ->
            val text = file.readText()
            forbidden
                .filter { token -> text.contains(token) }
                .map { token -> "${file.relativeTo(root).invariantSeparatorsPath} contains `$token`" }
        }

        assertTrue(
            offenders.isEmpty(),
            "A hardcoded default domain is a hidden second owner of source existence (ADR-0012): " +
                "it made 'no sources configured' indistinguishable from 'these three', and named " +
                "sites the app could not serve:\n" + offenders.joinToString("\n"),
        )
    }

    private fun productionSources(root: File): List<File> =
        File(root, ".").walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .filter { file ->
                val rel = file.relativeTo(root).invariantSeparatorsPath
                rel.contains("src/main/") && !rel.contains("/build/")
            }
            .toList()

    private fun repositoryRoot(): File {
        var directory: File? = File(".").absoluteFile
        while (directory != null && !File(directory, "settings.gradle.kts").exists()) {
            directory = directory.parentFile
        }
        return requireNotNull(directory) { "Could not locate repository root from ${File(".").absolutePath}" }
    }
}
