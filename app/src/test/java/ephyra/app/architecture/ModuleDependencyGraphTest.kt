package ephyra.app.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Lists the files git tracks in this repository, as absolute [File]s.
 *
 * Repository-walking gates used to be built on `File.walkTopDown()` plus a hand-maintained list of
 * directories to exclude (`build/`, `.git/`, and an agent worktree directory). That list is a
 * correctness liability: a gate that must be told about every directory that can appear inside the
 * repository will eventually miss one, and it fails *open* — the unseen tree is simply not checked.
 * `SigningSecretTest` is the sharp case, because a keystore hidden in an unscanned tree is exactly
 * the leak it exists to catch.
 *
 * `git ls-files` cannot see untracked or ignored trees at all, so a stale worktree, a build output
 * directory, or a stray clone is excluded structurally rather than by a rule someone has to
 * remember. The cost is that the gate reads the *index*, which is the right thing to check: the
 * question these gates answer is "what is this repository committing", not "what exists on this
 * disk".
 *
 * **Failure behaviour is fail-open and is stated rather than hidden.** If git cannot be executed,
 * `list` returns empty, and both consumers assert "nothing offending was found" — so the gate passes
 * without having checked anything. That is the correct trade for a repository-shape gate, whose
 * real risk is noise from a 1200-file project, but it is a real limitation: on a machine with no git
 * on `PATH`, these two gates are inert. The mitigation is that the environment which runs them
 * (CI and every developer checkout) necessarily has git, so the case does not arise in practice. A
 * future change that makes a gate load-bearing for security should assert the file list is
 * non-empty rather than rely on this.
 */
object TrackedFiles {
    fun list(root: File): List<File> {
        val process =
            ProcessBuilder("git", "-C", root.absolutePath, "ls-files", "-z")
                .redirectErrorStream(false)
                .start()
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val exit = process.waitFor()
        if (exit != 0) return emptyList()
        return stdout
            .split('\u0000')
            .filter { it.isNotBlank() }
            .map { File(root, it) }
    }
}

/**
 * ARC-001 — enforce the target module dependency graph.
 *
 * The repository's confirmed debt (ledger row B-006) is that `presentation-core` exports
 * `core:data`, and that feature modules broadly depend on data. A cap on the raw number of
 * `projects.*` edges would only say that a total cannot grow unseen; it cannot say *which* edges are
 * wrong. A graph rule can, and it is the rule that eventually lets B-006 be closed rather than
 * merely counted.
 *
 * The rules encoded here are the structural ones that hold for a layered Android project and that
 * the reconstruction is moving toward. They are deliberately about graph shape, not about specific
 * modules, so that retiring a legacy edge satisfies the rule automatically instead of needing the
 * test rewritten. That is the deliberate difference between a gate and a metric: these rules do not
 * move when a number moves, and they cannot be satisfied by editing one.
 *
 * E2 by construction — parses the build scripts from disk; no device required.
 */
class ModuleDependencyGraphTest {

    @Test
    fun `core domain has no unacknowledged project dependencies`() {
        val edges = edgesFrom("core/domain")
        val undeclared = edges - DECLARED_CORE_DOMAIN_EDGES
        assertTrue(
            undeclared.isEmpty(),
            "core:domain is the pure contract layer and must depend on no other project " +
                "(ADR-0001: one owner per truth, and a pure core cannot be owned by an impure " +
                "module). These edges are not declared in DECLARED_CORE_DOMAIN_EDGES: $undeclared. " +
                "Either remove the dependency or declare it with a removal condition, the same way " +
                "SEC-003 requires of a manifest permission.",
        )
    }

    @Test
    fun `core domain declarations have not gone stale`() {
        val stale = DECLARED_CORE_DOMAIN_EDGES - edgesFrom("core/domain")
        assertTrue(
            stale.isEmpty(),
            "DECLARED_CORE_DOMAIN_EDGES still lists $stale, which no longer exists. Remove the " +
                "declaration so the exception list reflects reality — and treat removing the edge " +
                "as the prompt to tighten the layer.",
        )
    }

    @Test
    fun `core modules do not depend on feature modules`() {
        val offenders = allEdges()
            .filter { isLayer(it.from, "core", "source", "presentation") }
            .filter { isLayer(it.to, "feature") }
        assertTrue(
            offenders.isEmpty(),
            "A dependency from a core/presentation/source module into a feature module inverts the " +
                "layering. Offenders: $offenders",
        )
    }

    @Test
    fun `feature modules do not depend on each other`() {
        val offenders = allEdges()
            .filter { isLayer(it.from, "feature") && isLayer(it.to, "feature") }
            .filterNot { it in DECLARED_FEATURE_EDGES }
        assertTrue(
            offenders.isEmpty(),
            "Feature-to-feature edges let one product slice reach into another's internals and " +
                "makes features impossible to delete independently. These are not declared in " +
                "DECLARED_FEATURE_EDGES: $offenders. Feature-to-feature dependencies are the " +
                "confirmed debt in ledger row B-006; a new one must be routed through a public " +
                "navigation contract instead of a direct module edge.",
        )
    }

    @Test
    fun `feature declarations have not gone stale`() {
        val present =
            allEdges()
                .filter { isLayer(it.from, "feature") && isLayer(it.to, "feature") }
                .toSet()
        val stale = DECLARED_FEATURE_EDGES - present
        assertTrue(
            stale.isEmpty(),
            "DECLARED_FEATURE_EDGES still lists $stale, which no longer exists. Remove the " +
                "declaration so the exception list reflects reality — and treat removing the edge " +
                "as progress against B-006.",
        )
    }

    @Test
    fun `presentation modules do not depend on feature modules`() {
        val offenders = allEdges()
            .filter { isLayer(it.from, "presentation") && isLayer(it.to, "feature") }
        assertTrue(
            offenders.isEmpty(),
            "Presentation is a shared layer; a feature dependency would make it a feature in " +
                "disguise. Offenders: $offenders",
        )
    }

    /**
     * True when [module] is, or is nested under, any of [layers].
     *
     * Module coordinates are canonical `:a:b` strings, so a bare `startsWith("core")` never matches
     * `:core:domain` — the leading colon. Each layer must therefore be compared as a path segment
     * boundary. This is a second defect the falsification probe exposed: even with correct edge
     * extraction, every prefix test here would have compared a colon-prefixed string against a
     * colon-less literal and silently reported no offenders.
     */
    private fun isLayer(module: String, vararg layers: String): Boolean {
        val segments = module.split(':').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return false
        return layers.any { layer ->
            layer == segments.first() || segments.drop(1).contains(layer)
        }
    }

    /**
     * The extractor and the layer test are themselves asserted, because the failure they had was
     * silent: a gate that cannot fail looks identical to a gate that is passing. If either of these
     * two breaks, the graph rules go back to reporting "no offenders" for the wrong reason and
     * nothing else in the suite would notice.
     */
    @Test
    fun `project references are extracted from the accessor form used in this repository`() {
        val text = """
            dependencies {
                implementation(projects.core.data)
                implementation(projects.feature.reader)
                implementation(projects.core.domain)
                api(projects.core)
            }
        """.trimIndent()
        assertEquals(
            listOf(":core:data", ":feature:reader", ":core:domain", ":core"),
            referencedProjects(text),
        )
    }

    @Test
    fun `layer matching respects module path boundaries`() {
        assertTrue(isLayer(":core:domain", "core"), "top-level layer")
        assertTrue(isLayer(":feature:reader", "feature"), "nested under a layer")
        assertTrue(!isLayer(":core:domain", "feature"), "a different layer")
        assertTrue(!isLayer(":core:domain", "corex"), "prefix must not match a partial segment")
    }

    /**
     * Proves the graph rules are load-bearing by asserting on the real tree that edges are actually
     * being found. A non-zero count is the minimum: it distinguishes "the graph is clean" from
     * "the graph is empty because extraction is broken", which are indistinguishable from the
     * outside.
     */
    @Test
    fun `the dependency graph is non-empty, so a clean result is meaningful`() {
        val edges = allEdges()
        assertTrue(
            edges.isNotEmpty(),
            "No project dependency edges were discovered. Every rule above asserts that no " +
                "offending edge exists, which is vacuously true when extraction returns nothing. " +
                "This guard exists so a future regression in edge extraction cannot be mistaken for " +
                "a passing architecture.",
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
        return referencedProjects(buildFile.readText())
            .distinct()
            .map { Edge(from, it) }
            .toList()
    }

    /**
     * Extracts every project this build script depends on, in canonical `:a:b` form.
     *
     * The repository uses Gradle's type-safe accessor form exclusively — `projects.core.data`,
     * `projects.feature.reader` — verified by scanning every tracked `build.gradle.kts`. The
     * `project(":a:b")` form does not occur and is still accepted.
     *
     * **This previously did not work, and the gate was inert because of it.** The old extraction was
     * `projects\.([a-zA-Z][a-zA-Z0-9_]*)` followed by `filter { it.startsWith(":") }`. That regex
     * captures a single identifier segment, so `projects.feature.reader` yielded the string
     * `"feature"`, which does not start with a colon and was discarded. Since the accessor form never
     * contains a colon, the filter removed *every* real edge, `allEdges()` was always empty, and all
     * five graph rules asserted over an empty list and therefore passed unconditionally.
     *
     * That is the failure mode `REBUILD_EXECUTION_GUIDE.md` §7 names: "A test that passes because
     * behavior is skipped is not passing." These rules were reported as the structural backstop for
     * the layering architecture, and they could not have failed. It was found by staging a
     * deliberate `core -> feature` violation and observing the gate stay green, not by reading the
     * code — which is the argument for keeping a falsification probe rather than trusting a green run.
     */
    private fun referencedProjects(text: String): List<String> {
        val fromAccessors = ACCESSOR_REFERENCE.findAll(text).map { match ->
            // `projects.core.data` -> "core.data"; a single segment becomes just "core".
            match.groupValues[1].replace('.', ':')
        }
        val fromStrings = STRING_REFERENCE.findAll(text).map { it.groupValues[1] }
        return (fromAccessors + fromStrings)
            .filter { it.isNotBlank() }
            .map { if (it.startsWith(":")) it else ":$it" }
            .toList()
    }

    private fun buildFiles(): List<File> = TrackedFiles.list(repositoryRoot())
        .filter { it.isFile && it.name == "build.gradle.kts" }
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
        /**
         * The three project edges `core:domain` currently has.
         *
         * `core:domain` is meant to be the pure contract layer. These three edges predate that rule
         * and were invisible while the graph rules could not fail. They are recorded rather than
         * hidden: a new edge fails immediately, and a declaration that no longer matches reality
         * also fails, so the exception list cannot drift in either direction.
         *
         * Removing them is Phase 3 / `ARC-002` work — they are `B-006`-class debt with a named
         * replacement, not an accepted end state.
         */
        val DECLARED_CORE_DOMAIN_EDGES =
            setOf(
                Edge(":core:domain", ":coreMetadata"),
                Edge(":core:domain", ":core:common"),
                Edge(":core:domain", ":sourceApi"),
            )

        /**
         * The 24 feature-to-feature edges that exist today — the concrete form of ledger row
         * `B-006`, where sibling features reach into each other. They are the reason
         * `ROADMAP.md` rule 5 exists and why Phase 9 requires sibling-feature dependencies to
         * become an explicit allowlist of navigation contracts.
         *
         * Declaring them individually, rather than allowing a count, is the point: the count cannot
         * say *which* edges are wrong, and a new edge must fail even though twenty-four others are
         * tolerated. As each is replaced by a public contract, remove it here — the
         * `feature declarations have not gone stale` test makes the removal the prompt.
         */
        val DECLARED_FEATURE_EDGES =
            setOf(
                Edge(":feature:browse", ":feature:manga"),
                Edge(":feature:browse", ":feature:category"),
                Edge(":feature:browse", ":feature:migration"),
                Edge(":feature:history", ":feature:category"),
                Edge(":feature:history", ":feature:manga"),
                Edge(":feature:history", ":feature:migration"),
                Edge(":feature:history", ":feature:reader"),
                Edge(":feature:manga", ":feature:reader"),
                Edge(":feature:manga", ":feature:webview"),
                Edge(":feature:manga", ":feature:category"),
                Edge(":feature:manga", ":feature:settings"),
                Edge(":feature:manga", ":feature:migration"),
                Edge(":feature:more", ":feature:category"),
                Edge(":feature:more", ":feature:download"),
                Edge(":feature:more", ":feature:settings"),
                Edge(":feature:more", ":feature:stats"),
                Edge(":feature:more", ":feature:manga"),
                Edge(":feature:reader", ":feature:webview"),
                Edge(":feature:settings", ":feature:category"),
                Edge(":feature:upcoming", ":feature:manga"),
                Edge(":feature:updates", ":feature:download"),
                Edge(":feature:updates", ":feature:manga"),
                Edge(":feature:updates", ":feature:reader"),
                Edge(":feature:updates", ":feature:upcoming"),
            )

        /**
         * `projects.core.data`, `projects.feature.reader` — the full dotted accessor path, so a
         * nested module resolves to `core:data` rather than the bare first segment.
         */
        val ACCESSOR_REFERENCE = Regex("""projects\.((?:[a-zA-Z][a-zA-Z0-9_]*\.)*[a-zA-Z][a-zA-Z0-9_]*)""")

        /** `project(":core:data")` — not currently used in this repository, accepted anyway. */
        val STRING_REFERENCE = Regex("""project\("(:[a-zA-Z:]+)"\)""")
    }
}
