package ephyra.feature.reader.viewer.webtoon

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The webtoon item's Ready branch must render every [PageRenderPath] the policy can return.
 *
 * **The defect this prevents.** `RenderPathPolicy.choose` is total — every Ready page resolves
 * to SLICED, MERGED_BITMAP, or WHOLE_IMAGE — but the Ready branch in `WebtoonPageItem` only
 * rendered the first two: the chain fell from the SLICED branch straight into the still-reading
 * spinner. With crop borders enabled, production never measures a crop rect, so `blockerFor`
 * always reports `CROP_UNDECIDED` and every page takes WHOLE_IMAGE — crop borders therefore
 * turned every webtoon page into an infinite spinner (owner report, 2026-11-13). Animated pages
 * and non-region-decodable formats took the same silent path even with crop off.
 *
 * A behavioural test would need a live chapter load and a decoded strip, so the gate reads the
 * source, following the `ViewportPinWiringTest` pattern: brace-matched extraction so an
 * unbalanced source file is a compile-visible failure rather than a vacuous pass. The limit is
 * stated rather than implied: this proves the path is *named and rendered*, not that the cropped
 * result looks right on a device (E1 — device confirmation pending, per the evidence ladder).
 */
class WebtoonWholeImagePathTest {

    private fun sourceFile(relativePath: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relativePath)
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        return File(".", relativePath).absoluteFile
    }

    /** Source with prose removed, so a comment describing a mechanism cannot satisfy a rule. */
    private fun codeOf(file: File): String = file.readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("//.*"), "")

    private fun functionBody(source: String, signature: String): String {
        val start = source.indexOf(signature)
        assertTrue("no declaration of \`$signature\`", start >= 0)
        val open = source.indexOf('{', start)
        assertTrue("`$signature` has no body", open >= 0)
        var depth = 0
        var i = open
        while (i < source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(open, i + 1)
                }
            }
            i++
        }
        throw AssertionError("`$signature` has unbalanced braces; the source is not parseable")
    }

    private val composeWebtoonReader =
        "feature/reader/src/main/kotlin/ephyra/feature/reader/viewer/webtoon/ComposeWebtoonReader.kt"

    @Test
    fun `the ready branch names the whole-image path and renders it`() {
        val body = functionBody(codeOf(sourceFile(composeWebtoonReader)), "fun WebtoonPageItem(")

        val branchStart = body.indexOf("PageRenderPath.WHOLE_IMAGE")
        assertTrue(
            "WebtoonPageItem's Ready branch must name PageRenderPath.WHOLE_IMAGE: a policy " +
                "path with no consumer falls into the still-reading spinner, which is how " +
                "crop borders turned every webtoon page into an infinite load (2026-11-13).",
            branchStart >= 0,
        )

        val nextLabel = body.indexOf("readyBytesReadFailed", branchStart)
        assertTrue(
            "The WHOLE_IMAGE branch must be followed by the read-failure branch; source changed " +
                "shape — update this gate rather than letting it pass vacuously.",
            nextLabel > branchStart,
        )
        val branch = body.substring(branchStart, nextLabel)
        assertTrue(
            "The WHOLE_IMAGE branch must render SingleWebtoonImage, not fall through to the " +
                "spinner. Branch was:\n$branch",
            branch.contains("SingleWebtoonImage("),
        )
    }
}
