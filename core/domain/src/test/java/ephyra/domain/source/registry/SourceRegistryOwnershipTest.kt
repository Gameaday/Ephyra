package ephyra.domain.source.registry

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The single-owner gate `ADR-0012` requires, in the `SourceApiBoundaryTest` style.
 *
 * **Why a source scan rather than a compile error.** `profiled_domains_list` is a preference
 * *key* — the compiler cannot see who reads it, and neither the type system nor any IDE
 * inspection distinguishes one reader of the key from another. Before `SRC-011`, three components
 * answered "which sources exist" by reading this key independently, and their disagreement was
 * indistinguishable from corruption at the point of use. This test fails first, with a message
 * that says who is allowed and why, instead of failing later as a user-visible defect.
 *
 * Tests, not just production code, keep the gate honest: only `src/main` is scanned, because a
 * test may set up state through the preference in order to observe it.
 */
class SourceRegistryOwnershipTest {

    @Test
    fun `only the registry reads the profiled-domains preference`() {
        val root = repositoryRoot()
        val allowed = setOf(
            // The owner — the only production reader of the key.
            "core/domain/src/main/java/ephyra/domain/source/registry/SourceRegistry.kt",
            // The ADR-0017 deletion target. Its save/invalidate are the persistence the registry
            // reads through `SourceProfileStore`; it dies with D1, and the gate allows exactly
            // that file so removing it cannot silently widen the gate.
            "core/domain/src/main/java/ephyra/domain/content/source/SourceProfileCache.kt",
        )
        val offenders = productionSources(root)
            .filter { it.relativeTo(root).invariantSeparatorsPath !in allowed }
            .mapNotNull { file ->
                if (file.readText().contains("profiled_domains_list")) {
                    file.relativeTo(root).invariantSeparatorsPath
                } else {
                    null
                }
            }

        assertTrue(
            offenders.isEmpty(),
            "profiled_domains_list must have one owner (ADR-0012, SRC-011):\n" +
                offenders.joinToString("\n") { " - $it reads the key directly" } +
                "\nExistence and identity belong to SourceRegistry; read them from it.",
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
