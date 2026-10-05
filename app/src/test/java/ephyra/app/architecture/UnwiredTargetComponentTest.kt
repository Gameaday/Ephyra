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
 * navigation path does not use, a source gateway nothing resolves through. Each is individually
 * reasonable and each is individually invisible, because "has a test suite" reads as progress while
 * the app is unchanged. The pile grows every phase and nothing reports it, which is how a
 * reconstruction programme becomes a proof factory.
 *
 * **Reachability, not reference counting — and that distinction was learned the hard way.** The
 * first version asked "is this type named by any other production file?". That reports a *cluster*
 * as wired: a coordinator that references a reducer, which references its own types, makes all of
 * them look used, when in truth the only thing referencing the coordinator is its test. Adding a
 * 15-type session layer therefore *lowered* the count by 12 while making the codebase no more
 * finished -- a metric that flatters you into thinking you have shipped something.
 *
 * So a type counts as wired only when it is reachable from a file that is itself reachable, with the
 * roots being every production file outside the target areas. A group that only refers to itself is
 * unreachable, which is exactly what it is.
 *
 * **Why a ceiling and not a list.** A named list needs editing every time something is wired, and
 * that edit is the thing that gets forgotten -- so the list rots into a second source of truth. A
 * ceiling is self-maintaining in the only direction that matters: wiring something lowers it, and
 * adding an unwired component fails the build. It measures *reachability*, not merit, so raising it
 * is a decision someone has to make out loud rather than an accident.
 */
class UnwiredTargetComponentTest {

    @Test
    fun `the unwired target-component backlog does not grow`() {
        val root = repositoryRoot()
        val unwired = unwiredTargetTypes(root)

        assertTrue(
            unwired.size <= MAX_UNWIRED,
            "The 2.0 target areas now hold ${unwired.size} types that no reachable production file " +
                "refers to (ceiling $MAX_UNWIRED). Each is built and tested but not reached by the " +
                "app, so the programme grows its own backlog while the app is unchanged.\n" +
                "Wire one and the count drops; add one without a consumer and this fails.\n" +
                unwired.take(12).joinToString("\n") { "  $it" } +
                if (unwired.size > 12) "\n  … and ${unwired.size - 12} more" else "",
        )
    }

    /**
     * The ceiling, measured with this file's own reachability rule on 2026-09-30.
     *
     * Lower it in the same commit that wires or deletes something. A ceiling that only ever rises is
     * a backlog with extra steps.
     */
    private companion object {
        const val MAX_UNWIRED = 99

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

        fun unwiredTargetTypes(root: File): List<String> {
            val production = root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { it.relativeTo(root).invariantSeparatorsPath.contains("/src/main/") }
                .toList()
            val texts = production.associate { it to it.readText() }
            val declarations = production.associateWith { file ->
                DECLARATION.matcher(texts.getValue(file))
                    .let { m -> buildList { while (m.find()) add(m.group(1)) } }
                    .toSet()
            }

            // `production` is walked from an absolute root, so its paths are absolute and have to
            // be made relative before comparing against the relative area list. Getting this wrong
            // leaves `target` empty, every file counts as wired, and the gate passes at any ceiling
            // -- which is what the ceiling-1 falsification probe caught.
            val target = production.filter { file ->
                val path = file.relativeTo(root).invariantSeparatorsPath
                TARGET_AREAS.any { path.startsWith("$it/") }
            }.toSet()

            // Reachability from every production file outside the target areas. Iterated to a
            // fixed point: a file joins the wired set as soon as any already-wired file names
            // something it declares.
            val wired = production.filter { it !in target }.toMutableSet()
            var changed = true
            while (changed) {
                changed = false
                for (file in production) {
                    if (file in wired) continue
                    val named = declarations.getValue(file).any { name ->
                        val word = Pattern.compile("\\b" + Pattern.quote(name) + "\\b")
                        texts.any { (other, otherText) -> other in wired && word.matcher(otherText).find() }
                    }
                    if (named) {
                        wired += file
                        changed = true
                    }
                }
            }

            return target.filter { it !in wired }
                .flatMap { file ->
                    declarations.getValue(file).map { name -> "${file.relativeTo(root)} :: $name" }
                }
                .sorted()
        }
    }
}
