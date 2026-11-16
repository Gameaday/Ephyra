package ephyra.app.architecture

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Structural gate for the webtoon slice renderer's seam layout (the ~1px hairline-seam
 * fix, 2026-11).
 *
 * The renderer stacked its slices with a Compose `Column` of `Image`s, each given a
 * `weight` share of the item box and drawn with `ContentScale.FillWidth`. The height a
 * slice was allocated (a rounded share of the box, from source slice heights) and the
 * height its bitmap needed at display width (from the decoded bitmap) were two independent
 * roundings that could disagree by a device pixel at every joint — the reported ~1px gap.
 * The renderer now draws all slices on ONE canvas at their cumulative decoded-bitmap
 * offsets under one continuous scale ([WebtoonSeamLayout]), so adjacent slices share one
 * exact boundary and no rounding can open a gap, duplicate a row, or drop one.
 *
 * This reads the source, following the pattern established by `MotionConsistencyTest` and
 * `ReaderDisabledControlStateTest`: a behavioural test would have to capture rendered
 * pixels across densities and zoom factors, while the defect is a one-line declaration —
 * a "harmless" reintroduction of the weight/FillWidth layout would restore the seam with
 * no compile error.
 */
class WebtoonSeamRenderGateTest {

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

    private val slicedWebtoonImage =
        "feature/reader/src/main/kotlin/ephyra/feature/reader/viewer/webtoon/SlicedWebtoonImage.kt"

    @Test
    fun `the slice renderer lays slices out through the seam layout on one canvas`() {
        val code = codeOf(slicedWebtoonImage)
        assertTrue(
            code.contains("WebtoonSeamLayout.place("),
            "Sanity: this gate reads the slice renderer's layout call; if the layout moved " +
                "or was renamed, update this gate rather than letting it pass vacuously.",
        )
        assertTrue(
            code.contains("drawImage("),
            "Slices must be drawn on one canvas (drawImage at cumulative offsets) so " +
                "adjacent slices share one exact boundary.",
        )
    }

    @Test
    fun `the slice renderer never returns to the weight-plus-FillWidth layout`() {
        val code = codeOf(slicedWebtoonImage)
        assertFalse(
            code.contains(".weight("),
            "SliceColumn must not allocate slice heights with weight(): the allocated " +
                "height and the bitmap's display height are two independent roundings that " +
                "disagree by up to a device pixel per joint — the ~1px seam this gate keeps " +
                "dead. Layout must come from WebtoonSeamLayout over the decoded bitmaps.",
        )
        assertFalse(
            code.contains("ContentScale"),
            "SliceColumn must not draw slices with ContentScale.FillWidth: each Image's " +
                "scaled height then depends on its own bitmap rounding instead of sharing " +
                "one exact boundary with its neighbour — the ~1px seam this gate keeps dead.",
        )
    }
}
