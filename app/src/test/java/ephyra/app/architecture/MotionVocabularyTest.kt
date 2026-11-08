package ephyra.app.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The motion vocabulary gate — audit F4's exit condition, made permanent (Motion v2, 2026-11-08).
 *
 * `MotionTokens` shipped an M3-template vocabulary that no production code ever called: three spring
 * specs, three tween presets, a shared-element duration helper, six duration constants, two easings
 * and a `MaterialTheme` accessor. All were deleted rather than kept "in case", because an unused
 * motion primitive is not a future default — it is the wrong duration waiting to be picked by
 * someone who assumes this file is curated.
 *
 * This test holds that line: every public member of `MotionTokens` must be referenced either by
 * production code in another file, or transitively by a live member of the same file (an alias, or
 * a transition built from a duration). A member that loses its last consumer fails the build with
 * "add a consumer or delete it".
 *
 * It reads source rather than compiling, because the question is not "does it compile" but "does
 * anything execute this" — a public object full of dead members compiles perfectly while telling
 * every future reader that these are the choices this app made.
 */
class MotionVocabularyTest {

    private fun sourceFile(relativePath: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relativePath)
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        throw AssertionError("$relativePath not found")
    }

    /** Source with prose removed, so a comment naming a member cannot satisfy the gate. */
    private fun codeOf(relativePath: String): String = sourceFile(relativePath)
        .readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("//.*"), "")

    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile
        }
        throw AssertionError("repository root (settings.gradle.kts) not found")
    }

    private val motion = "presentation-core/src/main/java/ephyra/presentation/core/theme/MotionTokens.kt"

    /**
     * Every public member declaration in MotionTokens.kt with the source up to the next declaration
     * as its body, so a member that references another member (an alias, or a transition naming a
     * duration) keeps that member alive.
     */
    private fun membersOf(motionCode: String): Map<String, String> {
        val lines = motionCode.lines()
        val constVal = Regex("""^\s*const val ([A-Za-z][A-Za-z0-9_]*)\s*=""")
        val plainVal = Regex("""^\s*val ([A-Za-z][A-Za-z0-9_.]*)\s*[:=]""")
        val funDecl = Regex("""^\s*fun (?:<T>\s*)?([A-Za-z][A-Za-z0-9_]*)\(""")
        data class Declaration(val line: Int, val name: String)

        val declarations = lines.mapIndexedNotNull { index, line ->
            if (line.contains("private")) return@mapIndexedNotNull null
            val name = constVal.find(line)?.groupValues?.get(1)
                ?: plainVal.find(line)?.groupValues?.get(1)
                ?: funDecl.find(line)?.groupValues?.get(1)
            if (name != null) Declaration(index, name) else null
        }

        val members = LinkedHashMap<String, String>()
        declarations.forEachIndexed { i, declaration ->
            val end = if (i + 1 < declarations.size) declarations[i + 1].line else lines.size
            members[declaration.name] = lines.subList(declaration.line, end).joinToString("\n")
        }
        return members
    }

    private fun referencePattern(name: String): Regex = Regex("\\b" + Regex.escape(name) + "\\b")

    @Test
    fun `every motion token has a consumer or is deleted`() {
        val members = membersOf(codeOf(motion))
        assertTrue(members.isNotEmpty(), "no members parsed from MotionTokens.kt — the gate is vacuous")

        val root = repoRoot()
        val consumerFiles = root.walkTopDown()
            .onEnter { dir -> dir.name !in setOf(".git", "build", ".gradle") }
            .filter { file ->
                val path = file.relativeTo(root).invariantSeparatorsPath
                file.isFile &&
                    file.extension == "kt" &&
                    path.contains("src/main/") &&
                    path != motion
            }
            .toList()

        val live = mutableSetOf<String>()
        for (file in consumerFiles) {
            if (live.size == members.size) break
            val code = file.readText()
                .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
                .replace(Regex("//.*"), "")
            for (name in members.keys) {
                if (name !in live && referencePattern(name).containsMatchIn(code)) live += name
            }
        }

        // Transitive liveness: a member is kept alive when a live member of the same file *uses* it —
        // an alias resolving to a constant, or a container transition naming its shared-axis legs.
        // The direction is the point: "built from live parts" would instead keep a dead alias alive
        // because its body names a live constant. The question is always who is used.
        var changed = true
        while (changed) {
            changed = false
            for (name in members.keys) {
                if (name in live) continue
                val usedByLiveMember = members.entries.any { (other, otherBody) ->
                    other != name && other in live && referencePattern(name).containsMatchIn(otherBody)
                }
                if (usedByLiveMember) {
                    live += name
                    changed = true
                }
            }
        }

        val dead = members.keys.filter { it !in live }
        assertTrue(
            dead.isEmpty(),
            "MotionTokens.kt declares motion vocabulary with no production consumer: " +
                "${dead.joinToString(", ")}. Add a consumer or delete it. This gate exists because " +
                "the members pruned on 2026-11-08 were exactly this: M3 template text that had " +
                "never been chosen for this app, kept as if it were a decision.",
        )
    }
}
