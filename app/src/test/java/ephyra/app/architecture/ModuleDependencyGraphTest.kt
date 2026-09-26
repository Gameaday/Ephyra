package ephyra.app.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * ARC-001 — enforce the target module dependency graph.
 *
 * The repository's confirmed debt (ledger row B-006) is that `presentation-core` exports
 * `core:data`, and that feature modules broadly depend on data. The health ratchet already caps the
 * raw number of `projects.*` edges so the total cannot grow without being seen, but a cap alone
 * cannot say *which* edges are wrong. A graph rule can, and it is the rule that eventually lets
 * B-006 be closed rather than merely counted.
 *
 * The rules encoded here are the structural ones that hold for a layered Android project and that
 * the reconstruction is moving toward. They are deliberately about graph shape, not about specific
 * modules, so that retiring a legacy edge satisfies the rule automatically instead of needing the
 * test rewritten.
 *
 * E2 by construction — parses the build scripts from disk; no device required.
 */
class ModuleDependencyGraphTest {

    @Test
    fun `core domain has no project dependencies`() {
        val edges = edgesFrom("core/domain")
        assertTrue(
            edges.isEmpty(),
            "core:domain is the pure contract layer and must depend on no other project " +
                "(ADR-0001: one owner per truth, and a pure core cannot be owned by an impure " +
                "module). Found: $edges",
        )
    }

    @Test
    fun `core modules do not depend on feature modules`() {
        val offenders = allEdges()
            .filter {
                it.from.startsWith("core") || it.from.startsWith("source-") || it.from.startsWith("presentation-")
            }
            .filter { it.to.startsWith("feature") }
        assertTrue(
            offenders.isEmpty(),
            "A dependency from a core/presentation/source module into a feature module inverts the " +
                "layering. Offenders: $offenders",
        )
    }

    @Test
    fun `feature modules do not depend on each other`() {
        val offenders = allEdges()
            .filter { it.from.startsWith("feature") && it.to.startsWith("feature") }
        assertTrue(
            offenders.isEmpty(),
            "Feature-to-feature edges let one product slice reach into another's internals and " +
                "makes features impossible to delete independently. Offenders: $offenders",
        )
    }

    @Test
    fun `presentation modules do not depend on feature modules`() {
        val offenders = allEdges()
            .filter { it.from.startsWith("presentation") && it.to.startsWith("feature") }
        assertTrue(
            offenders.isEmpty(),
            "Presentation is a shared layer; a feature dependency would make it a feature in " +
                "disguise. Offenders: $offenders",
        )
    }

    @Test
    fun `no module depends on the application module`() {
        val offenders = allEdges().filter { it.to == ":app" && it.from != ":app" }
        assertTrue(
            offenders.isEmpty(),
            "Only the application module may sit above the graph; nothing may depend on it. " +
                "Offenders: $offenders",
        )
    }

    @Test
    fun `the graph contains no self edges`() {
        val offenders = allEdges().filter { it.from == it.to }
        assertTrue(offenders.isEmpty(), "Self-referential project dependency: $offenders")
    }

    private fun allEdges(): List<Edge> {
        val root = repositoryRoot()
        return buildFiles().flatMap { file ->
            val modulePath = file.parentFile.relativeTo(root).path
            edgesFrom(modulePath)
        }
    }

    private fun edgesFrom(modulePath: String): List<Edge> {
        val buildFile = File(File(repositoryRoot(), modulePath), "build.gradle.kts")
        if (!buildFile.isFile) return emptyList()
        val from = ":" + modulePath.replace('\\', '/').replace('/', ':')
        return PROJECT_REFERENCE.findAll(buildFile.readText())
            .map { it.groupValues[1] }
            .filter { it.startsWith(":") }
            .distinct()
            .map { Edge(from, it) }
            .toList()
    }

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

    private data class Edge(val from: String, val to: String) {
        override fun toString(): String = "$from -> $to"
    }

    private companion object {
        val PROJECT_REFERENCE = Regex("""projects\.([a-zA-Z][a-zA-Z0-9_]*)""")
    }
}
