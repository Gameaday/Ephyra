package ephyra.app.architecture

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Structural gate for the reader's chapter-navigation controls (app health & design audit
 * finding F1, 2026-11-12).
 *
 * The navigator pinned its disabled container colour to the enabled colour, so a disabled
 * next/previous control rendered identically to an enabled one. Combined with a silent
 * no-op handler in the view model, the reader's only signal that "next chapter does
 * nothing" was nothing. The handler now reports (see the view model's classified
 * no-next-chapter event) and the colour override is dropped so Material's default disabled
 * treatment renders a distinct state - this gate keeps it dropped, because a one-line
 * "harmless" re-pin would reintroduce the defect with no compile error.
 *
 * This reads the source, following the pattern established by `MotionConsistencyTest`: a
 * behavioural test would have to capture the rendered disabled colour of a Compose button,
 * which is fragile across theme and density configurations, while the defect itself is a
 * one-line declaration.
 */
class ReaderDisabledControlStateTest {

    private fun sourceFile(relativePath: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relativePath)
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        throw AssertionError("$relativePath not found")
    }

    /** Source with prose removed, so a comment describing a mechanism cannot satisfy a rule. */
    private fun codeOf(relativePath: String): String = sourceFile(relativePath)
        .readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("//.*"), "")

    private val chapterNavigator =
        "feature/reader/src/main/kotlin/ephyra/presentation/reader/components/ChapterNavigator.kt"

    /**
     * The navigator's next/previous controls must not pin their disabled container colour to
     * the enabled container colour.
     */
    @Test
    fun `the chapter navigator does not pin its disabled container colour to the enabled colour`() {
        val code = codeOf(chapterNavigator)
        assertTrue(
            code.contains("filledIconButtonColors("),
            "Sanity: this gate reads the navigator's button colour declaration; if the " +
                "declaration moved or was renamed, update this gate rather than letting it " +
                "pass vacuously.",
        )
        assertFalse(
            code.contains("disabledContainerColor = backgroundColor"),
            "ChapterNavigator must not pin disabledContainerColor to the enabled container " +
                "colour: a disabled next/previous control that renders identically to an " +
                "enabled one is the F1 defect - a press that does nothing, with no signal " +
                "why. Let IconButtonDefaults' disabled treatment render, or choose a " +
                "visibly distinct disabled colour deliberately.",
        )
    }
}
