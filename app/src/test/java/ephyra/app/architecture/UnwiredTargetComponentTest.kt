package ephyra.app.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.regex.Pattern

/**
 * Makes the "built but never wired" backlog a number that can only go down.
 *
 * **The failure this exists to prevent.** The 2.0 programme's target components are built, tested,
 * and then not called: a reader session reducer with no consumer, a chapter-window policy the
 * navigation path does not use, a source gateway nothing resolves through. Each one is individually
 * reasonable and each is individually invisible, because "has a test suite" reads as progress while
 * the app is unchanged. The pile grows every phase and nothing reports it, which is how a
 * reconstruction programme becomes a proof factory.
 *
 * **Why a count and not a list.** A named list would need editing every time something is wired, and
 * the edit is the thing that gets forgotten -- so the list rots into a second source of truth. A
 * ceiling is self-maintaining in the only direction that matters: wiring something can only lower
 * it, and adding an unwired component fails the build.
 *
 * **What it does not claim.** A type with no production caller may be perfectly justified: a
 * platform primitive awaiting its first user, or a value type only its own file needs. The gate
 * measures *reachability*, not merit, and it is a ratchet rather than a judgement. Raising
 * [MAX_UNWIRED] is a decision someone has to make out loud, which is the whole point.
 */
class UnwiredTargetComponentTest {

    @Test
    fun `the unwired target-component backlog does not grow`() {
        val root = repositoryRoot()
        val unwired = unwiredTargetTypes(root)

        assertTrue(
            unwired.size <= MAX_UNWIRED,
            "The 2.0 target areas now hold ${unwired.size} types with no production caller " +
                "(ceiling $MAX_UNWIRED). Each is built and tested but not reached by the app, so the " +
                "programme grows its own backlog while the app is unchanged.\n" +
                "Wire one and the count drops; add one without a consumer and this fails.\n" +
                unwired.take(15).joinToString("\n") { "  $it" } +
                if (unwired.size > 15) "\n  … and ${unwired.size - 15} more" else "",
        )
    }

    /**
     * The ceiling, as measured on 2026-09-30 after the first retirement
     * (`ContentCatalogueSource` / `ContentSourceAdapter`, one implementor and zero consumers) and
     * two wirings (`PageDecodeWidth`; `RenderPathPolicy` / `AnimationVerdict` / `PageRenderPath`).
     *
     * Lowered from 66 to 64 by those wirings, in the same commit as the wiring, which is the only
     * way this number stays trustworthy.
     *
     * Lower it in the same commit that wires or deletes something. A ceiling that only ever rises is
     * a backlog with extra steps.
     */
    private companion object {
        const val MAX_UNWIRED = 64

        /** Directories holding 2.0 target components, per `REBUILD_PROGRAM.md` §3. */
        val TARGET_AREAS = listOf(
            "core/domain/src/main/java/ephyra/domain/reader",
            "core/domain/src/main/java/ephyra/domain/content/source",
            "source-api/src/main/kotlin/ephyra/source/api",
            "feature/reader/src/main/kotlin/ephyra/feature/reader",
        )

        val DECLARATION: Pattern = Pattern.compile(
            "^(?:@\\w+\\s+)*(?:public |internal |private |abstract |open |sealed |data |value |enum |fun )*" +
                "(?:class|interface|object)\\s+(\\w+)",
            Pattern.MULTILINE,
        )

        fun repositoryRoot(): File {
            var directory: File? = File(".").absoluteFile
            while (directory != null && !File(directory, "settings.gradle.kts").exists()) {
                directory = directory.parentFile
            }
            return requireNotNull(directory) { "Could not locate repository root" }
        }

        /** Every production Kotlin file in the tree — the caller's universe. */
        fun productionSources(root: File): List<File> =
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { it.relativeTo(root).invariantSeparatorsPath.contains("/src/main/") }
                .toList()

        fun unwiredTargetTypes(root: File): List<String> {
            val production = productionSources(root)
            val texts = production.associateWith { it.readText() }
            val unwired = mutableListOf<String>()

            for (area in TARGET_AREAS) {
                val dir = File(root, area)
                if (!dir.isDirectory) {
                    unwired += "$area (directory missing -- target area not present?)"
                    continue
                }
                for (file in dir.walkTopDown().filter { it.isFile && it.extension == "kt" }) {
                    val text = texts[file] ?: continue
                    val names = DECLARATION.matcher(text)
                        .let { m -> buildList { while (m.find()) add(m.group(1)) } }
                        .toSet()
                    for (name in names) {
                        val word = Pattern.compile("\\b" + Pattern.quote(name) + "\\b")
                        val called = texts.any { (other, otherText) ->
                            other != file && word.matcher(otherText).find()
                        }
                        if (!called) unwired += "${file.relativeTo(root).invariantSeparatorsPath} :: $name"
                    }
                }
            }
            return unwired.sorted()
        }
    }
}
