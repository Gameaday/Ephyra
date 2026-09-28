package ephyra.app.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * One gesture arbiter per viewport — the rule `RDR-003` exists to enforce.
 *
 * ## The defect this prevents
 *
 * The paged reader was migrated to [ephyra.domain.reader.gesture.ReaderGestureArbiter] in `RDR-004`
 * stage 2, which deleted its hand-rolled `detectPagerGestures`. The continuous reader kept its own
 * `detectWebtoonGestures`, reimplementing tap sequencing, long-press timing, pan/zoom arbitration
 * and the delegate-to-parent rule inline. Two active gesture architectures in one reader is exactly
 * what `ROADMAP.md` non-negotiable rule 7 forbids, and it is not a style preference: the two copies
 * disagreed about who owns a gesture, and the continuous copy could not report the arbiter's only
 * *negative* decision.
 *
 * ## Why this is structural
 *
 * A behavioural test cannot detect a second architecture. Both detectors would pass their own tests
 * and the reader would still work; what fails is the architectural property that only one component
 * decides gesture ownership. And the specific harm here — `DelegateSingleScroll` never reaching the
 * continuous viewport — is invisible in review because the effect simply does not appear in any
 * trace.
 */
class ReaderGestureOwnershipTest {

    private fun sourceFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.isAbsolute) return direct
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relativePath)
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        return File(".", relativePath).absoluteFile
    }

    /** Source lines with comments stripped, so prose cannot satisfy a rule about code. */
    private fun codeOf(relativePath: String): List<String> =
        sourceFile(relativePath)
            .readText()
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .map { it.substringBefore("//").trim() }
            .filter { it.isNotEmpty() }

    private fun readerMainSources(): List<File> {
        val root = sourceFile("feature/reader/src/main")
        assertTrue(root.exists(), "feature/reader/src/main not found")
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /**
     * No reader surface may define its own `detect*Gestures` pointer loop.
     *
     * The one permitted implementation is the shared adapter. Anything else with this shape is a
     * second architecture, regardless of how well it works — which is the point, because a working
     * second copy is how the first one survived this long.
     */
    @Test
    fun `no reader surface defines its own pointer gesture detector`() {
        val offenders = mutableListOf<String>()
        for (file in readerMainSources()) {
            if (file.name == "ReaderGesturePointerAdapter.kt") continue
            for (line in codeOf(file.absolutePath)) {
                if (Regex("""fun\s+(PointerInputScope\.)?detect\w*Gestures\s*\(""").containsMatchIn(line)) {
                    offenders += "${file.name}: $line"
                }
            }
        }
        assertTrue(
            offenders.isEmpty(),
            "the reader must route pointer input through the shared arbiter adapter " +
                "(`detectReaderGestures`). A per-surface detector is a second gesture architecture, " +
                "which ROADMAP.md non-negotiable rule 7 forbids:\n  " + offenders.joinToString("\n  "),
        )
    }

    /**
     * The adapter itself is the single implementation.
     *
     * Asserted positively as well as negatively: a gate that only forbade new detectors would also
     * pass if the adapter were deleted and nothing replaced it, leaving the reader with no gesture
     * handling at all and every test green.
     */
    @Test
    fun `the shared adapter still exists and is the arbiter's only entry point`() {
        val adapter =
            codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/viewer/gesture/ReaderGesturePointerAdapter.kt")

        assertTrue(
            adapter.any { it.contains("ReaderGestureArbiter.down(") },
            "the adapter must drive the arbiter; a detector that does not consult it is a second " +
                "architecture by another name",
        )
        assertTrue(
            adapter.any { it.contains("ReaderGestureArbiter.sample(") },
            "the adapter must feed pointer samples to the arbiter",
        )
        assertTrue(
            adapter.any { it.contains("ReaderGestureEffect.DelegateSingleScroll") },
            "the adapter must forward DelegateSingleScroll. B-023 found it swallowing that effect, " +
                "which made the arbiter's only negative decision invisible to its consumer.",
        )
    }

    /**
     * Both readers must actually consume the shared adapter.
     *
     * The deletion above removes a second architecture, but nothing forces the replacement to be
     * *used* — and an unused, correct adapter is precisely the `B-032` unwired-contract failure
     * this programme has repeatedly found. Asserting the two call sites exist is what makes the
     * migration real rather than merely tidy.
     */
    @Test
    fun `both readers consume the shared adapter`() {
        val pager =
            codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/viewer/pager/ZoomableMangaPage.kt")
        val webtoon =
            codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/viewer/webtoon/ComposeWebtoonReader.kt")

        assertTrue(
            pager.any { it.contains("pagerGestureStream(") },
            "the paged reader must drive its pointer stream from the arbiter adapter",
        )
        assertTrue(
            webtoon.any { it.contains("webtoonGestureStream(") },
            "the continuous reader must drive its pointer stream from the arbiter adapter. RDR-005 " +
                "was IN_PROGRESS precisely because this call site did not exist.",
        )
    }

    /**
     * The continuous reader must reduce effects through the shared reducer, not inline a `when`.
     *
     * An inline `when` at the call site is how effects get silently dropped, which is the failure
     * mode `PagerViewportState.reduceEffect` was written to make impossible. Requiring the named
     * reducer keeps the mapping in one testable place.
     */
    @Test
    fun `the continuous reader reduces effects through the shared reducer`() {
        val webtoon =
            codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/viewer/webtoon/ComposeWebtoonReader.kt")

        assertTrue(
            webtoon.any { it.contains("reduceWebtoonEffect(") },
            "the continuous reader must reduce arbiter effects through `reduceWebtoonEffect` so " +
                "every effect has a handled branch in one testable place",
        )
        assertTrue(
            webtoon.any { it.contains("WebtoonGestureOutcome.DelegatedToParent") },
            "the continuous reader must handle the delegated-to-parent outcome explicitly. That " +
                "branch is unreachable unless the arbiter runs here, so its absence is the RDR-005 " +
                "defect in its final form.",
        )
    }

    /**
     * A user setting the reader reads must stay read.
     *
     * This exists because it nearly did not. The `RDR-005` swap removed the
     * `if (!zoomEnabled) detectTapGestures(...)` branch on the reasonable reading that the arbiter
     * subsumed gesture handling -- and `doubleTapZoom` became an unused parameter, i.e. a settings
     * toggle that silently stopped doing anything. Nothing failed: the reader built, every test
     * passed, and the only signal was a warning on an unused parameter.
     *
     * A toggle that no longer controls anything is a `ROADMAP.md` rule-3 defect in the opposite
     * direction to the one that rule describes: not preserving an option for its own sake, but
     * losing one because a refactor stopped reading it. `ReaderPreferenceConsumptionTest` already
     * asserts the preference is read *somewhere*; this asserts the continuous reader's zoom path
     * still honours it.
     */
    @Test
    fun `the continuous reader still honours the double tap zoom setting`() {
        val webtoon =
            codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/viewer/webtoon/ComposeWebtoonReader.kt")

        assertTrue(
            webtoon.any { it.contains("enabled = zoomEnabled") },
            "the continuous reader must gate the arbiter on `zoomEnabled`. Without it the " +
                "double-tap-zoom setting is read by nobody on this surface and pinch zoom cannot be " +
                "turned off.",
        )
        assertTrue(
            webtoon.any { it.contains("detectTapGestures(") },
            "with zoom disabled the surface must fall back to tap-only handling, which is what the " +
                "removed `if (!zoomEnabled)` branch did",
        )
    }

    /**
     * The webtoon adapter must ask for CONTINUOUS mode.
     *
     * A copy-paste from the pager adapter would pass every structural rule above while inverting the
     * one rule that matters: in PAGED mode `viewportOwnsPan` returns `true` unconditionally, so the
     * viewport would claim *vertical* pans and the `LazyColumn` could not be scrolled at all. That is
     * a total loss of the surface's primary function, and it is invisible without this assertion.
     */
    @Test
    fun `the continuous adapter requests CONTINUOUS mode`() {
        val adapter =
            codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/viewer/webtoon/WebtoonGestureAdapter.kt")

        assertTrue(
            adapter.any { it.contains("ReaderViewportMode.CONTINUOUS") },
            "the continuous adapter must use ReaderViewportMode.CONTINUOUS. PAGED would make the " +
                "viewport claim vertical pans, so the LazyColumn could never be scrolled.",
        )

        val pager =
            codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/viewer/pager/PagerViewportAdapter.kt")
        assertTrue(
            pager.any { it.contains("ReaderViewportMode.PAGED") },
            "the pager adapter must keep using PAGED; the two adapters must not be merged",
        )
    }
}
