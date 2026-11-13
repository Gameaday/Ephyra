package ephyra.app.architecture

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Structural gate for the image-cache memory bounds (app health audit P1/P3, 2026-11-13).
 *
 * The bounds are one-line declarations: the background-retention fraction, the hard cap, and
 * the trim floor. A behavioural test would have to build a real loader and inspect Coil's
 * private cache sizing; the defect class is a one-line revert, so the gate reads the source,
 * following the pattern established by `MotionConsistencyTest` and
 * `ReaderDisabledControlStateTest`.
 */
class ImageCacheBoundsGateTest {

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

    private val imageLoaderFactory =
        "app/src/main/java/ephyra/app/image/EphyraImageLoaderFactory.kt"
    private val memoryPolicy =
        "app/src/main/java/ephyra/app/image/ImageCacheMemoryPolicy.kt"

    /**
     * The old 10% background fraction dropped nearly the whole working set on every
     * background trip, which is the delayed-cover report's re-decode-on-return half (P1).
     */
    @Test
    fun `the background retention fraction stays raised above the eviction-happy 10 percent`() {
        val code = codeOf(imageLoaderFactory)
        assertTrue(
            code.contains("memoryCacheMaxSizePercentWhileInBackground("),
            "Sanity: this gate reads the loader's background-retention declaration; if the " +
                "declaration moved or was renamed, update this gate rather than letting it " +
                "pass vacuously.",
        )
        assertFalse(
            code.contains("memoryCacheMaxSizePercentWhileInBackground(0.1)"),
            "memoryCacheMaxSizePercentWhileInBackground(0.1) evicts 90% of the decoded-image " +
                "cache on every background trip, so returning to the app re-decodes every " +
                "visible cover from disk (audit P1). Raise the fraction deliberately, with " +
                "the hard cap in ImageCacheMemoryPolicy bounding the retained floor.",
        )
        assertTrue(
            code.contains("memoryCacheMaxSizePercentWhileInBackground(0.25)"),
            "The background retention fraction must stay pinned at 0.25; if it moves, " +
                "update this gate with the new reviewed value.",
        )
    }

    /** The decoded-image cache must run under a hard byte cap, not only a heap fraction. */
    @Test
    fun `the memory cache is clamped to a hard byte cap at baseline capture`() {
        val code = codeOf(memoryPolicy)
        assertTrue(
            code.contains("MEMORY_CACHE_HARD_CAP_BYTES"),
            "Sanity: this gate reads the policy's cap declaration; if the cap moved or was " +
                "renamed, update this gate rather than letting it pass vacuously.",
        )
        assertTrue(
            code.contains("coerceAtMost(MEMORY_CACHE_HARD_CAP_BYTES)"),
            "The memory cache must be clamped to MEMORY_CACHE_HARD_CAP_BYTES when the " +
                "baseline is captured: percent-of-heap sizing on a largeHeap manifest " +
                "otherwise reaches ~150 MiB of decoded bitmaps on big-heap devices (audit " +
                "P3). Remove the cap only with a reviewed replacement bound.",
        )
    }

    /** The pressure-trim floor pins roughly a screenful of covers; it is reviewed, not tuned. */
    @Test
    fun `the pressure trim floor stays pinned`() {
        val code = codeOf(memoryPolicy)
        assertTrue(
            code.contains("TRIM_FLOOR_BYTES = 16L * 1024 * 1024"),
            "The 16 MiB trim floor was reviewed against the audit (P1) and kept: raising it " +
                "neuters the trim on LOW-tier devices whose baseline sits near the floor. " +
                "If it moves, update this gate with the new reviewed value.",
        )
    }
}
