package ephyra.app.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Structural gates for predictive back.
 *
 * ## The defect these prevent
 *
 * The app set `android:enableOnBackInvokedCallback="true"` in its manifest — opting in to the
 * system back-to-home and cross-activity animations — while having **no `PredictiveBackHandler`
 * anywhere** and eleven `BackHandler` call sites. That combination is the worst of both worlds:
 * opting in means the system animates *its* transitions, and an in-app surface that intercepts back
 * without participating in the gesture gets no preview at all. The user swipes, nothing moves, and
 * the surface disappears at the end. That reads as a broken gesture rather than a missing one.
 *
 * ## Why a structural gate rather than a behavioural test
 *
 * A behavioural test of a back gesture needs a real `OnBackInvokedDispatcher` and a system gesture,
 * so it can only assert "back still dismisses the sheet" — which passed before this change too,
 * because `BackHandler` does dismiss it. What regressed, and what no behavioural test can see, is
 * the *absence of progress participation*. So the rule is structural: a surface with an animation
 * to preview must opt into the gesture, and must do it through the shared helper rather than by
 * hand-rolling a second implementation.
 */
class PredictiveBackGateTest {

    /**
     * Resolves a repository-relative path, and passes an absolute one straight through.
     *
     * The absolute case is load-bearing rather than defensive: the tree-walking test hands these
     * paths to [codeOf] as `File.absolutePath`, and the first version walked *up* from the working
     * directory appending an already-absolute path. On Windows that produces a path with a
     * doubled drive prefix, which fails as `Invalid file path` rather than as a clear "not found"
     * — so the failure pointed at the filesystem instead of at the helper.
     */
    private fun sourceFile(path: String): File {
        val direct = File(path)
        if (direct.isAbsolute) return direct
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, path)
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        return File(".", path).absoluteFile
    }

    private fun textOf(relativePath: String): String {
        val file = sourceFile(relativePath)
        assertTrue(file.exists(), "$relativePath not found")
        return file.readText()
    }

    /**
     * Source lines with comments stripped, so prose describing the mechanism cannot satisfy — or
     * trip — a rule about the mechanism itself.
     *
     * Takes an absolute path because two callers walk the tree and get absolute paths back, while
     * the fixed-path callers pass repository-relative ones; [sourceFile] resolves both.
     */
    private fun codeOf(path: String): List<String> =
        sourceFile(path)
            .readText()
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .map { it.substringBefore("//").trim() }
            .filter { it.isNotEmpty() }

    /**
     * Returns the parenthesised argument text of the first call to [callee] in [code].
     *
     * Throws when [callee] is absent, so "the call was not found" can never be reported as "the
     * call was wrong" — or, worse, as a pass. Bracket counting is used rather than a regex
     * because a call's arguments routinely span several lines and contain their own parentheses,
     * which is precisely what a line-anchored or non-greedy pattern gets wrong.
     */
    private fun argumentListOf(code: List<String>, callee: String): String {
        val joined = code.joinToString("\n")
        val start = joined.indexOf(callee)
        assertTrue(start >= 0, "no call to `$callee`; the gate would be asserting nothing")

        var depth = 0
        var i = start + callee.length - 1
        while (i < joined.length) {
            when (joined[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return joined.substring(start + callee.length, i)
                }
            }
            i++
        }
        throw AssertionError("call to `$callee` has unbalanced parentheses")
    }

    /**
     * The app has opted in, so at least one surface must actually participate.
     *
     * Asserted as a count-free structural fact: the helpers exist, are used, and are used by
     * surfaces that have an animation to preview. A bare "there is at least one
     * `PredictiveBackHandler` somewhere" would pass on a file that is never composed.
     */
    @Test
    fun `predictive back is wired to surfaces that have an animation to preview`() {
        val sheet = codeOf("presentation-core/src/main/java/ephyra/presentation/core/components/AdaptiveSheet.kt")
        val pane =
            codeOf(
                "presentation-core/src/main/java/ephyra/presentation/core/components/adaptive/ListDetailPaneScaffold.kt",
            )

        assertTrue(
            sheet.any { it.contains("PredictiveBackDraggableProgress(") },
            "AdaptiveSheet must participate in the back gesture. It is a dismissible sheet with a " +
                "slide animation, so a bare BackHandler gives the user a swipe that moves nothing.",
        )
        assertTrue(
            sheet.any { it.contains("PredictiveBackProgress(") },
            "AdaptiveSheet's centred variant must participate too. It fades the scrim, which is " +
                "exactly the kind of animation a user expects to preview.",
        )
        assertTrue(
            pane.any { it.contains("PredictiveBackProgress(") },
            "The compact list-detail pane must participate: it has a directional slide, and " +
                "MOTION_NAVIGATION_CONTRACT requires predictive back to map to the same visual " +
                "model as completed back.",
        )
    }

    /**
     * The gesture must drive the surface's *own* state, not an animation of a private copy.
     *
     * The sheet is the case that matters: it already holds an `AnchoredDraggableState`, so the
     * gesture writing that same state keeps one owner per axis. A parallel animated value would be
     * the `B-025` failure (two owners of one position) in miniature — they diverge the moment a
     * drag and a back gesture overlap.
     */
    @Test
    fun `the sheet drives its existing drag state rather than a parallel animation`() {
        val sheet = codeOf("presentation-core/src/main/java/ephyra/presentation/core/components/AdaptiveSheet.kt")

        // Extracted by paren matching, not by regex over the whole file. The first version used a
        // regex assuming the call's arguments were on one line; it went **green** against a probe
        // that passed a freshly constructed `AnchoredDraggableState(1)` instead of the sheet's own,
        // which is exactly the two-owners-one-axis defect the rule exists to catch. That is the
        // ninth blind gate in this programme's history, and the third written by the same author in
        // one session. Paren matching cannot silently fail to match, because an unbalanced call is
        // a compile error rather than a passing test — the same reasoning that fixed
        // `ViewportPinWiringTest` and `TabNavControllerLifetimeTest`.
        val call = argumentListOf(sheet, "PredictiveBackDraggableProgress(")
        assertTrue(
            call.contains("anchoredDraggableState"),
            "The compact sheet must pass its own `anchoredDraggableState` to the gesture handler, " +
                "so the back gesture and the drag write one state rather than two. Call was:\n$call",
        )
        assertTrue(
            !call.contains("AnchoredDraggableState("),
            "The gesture handler was given a newly constructed drag state instead of the sheet's " +
                "own. That creates a second owner of the sheet's position, which diverges from the " +
                "real one the moment a drag and a back gesture overlap — the B-025 conflict in " +
                "miniature. Call was:\n$call",
        )
    }

    /**
     * Every handler must restore on cancellation, or a cancelled swipe leaves a stranded surface.
     *
     * A cancelled gesture arrives as a `CancellationException`, not as a final progress event, so
     * a handler that only collects progress leaves the surface at the offset the finger last
     * reached. This is the single most common predictive-back bug and it is invisible until a user
     * aborts a swipe halfway.
     */
    @Test
    fun `every progress handler has a cancellation path`() {
        val helper = codeOf("presentation-core/src/main/java/ephyra/presentation/core/ui/navigation/PredictiveBack.kt")

        assertTrue(
            helper.any { it.contains("catch (cancellation: CancellationException)") },
            "The shared helper must handle CancellationException, which is how an abandoned " +
                "gesture arrives.",
        )
        assertTrue(
            helper.any { it.contains("throw cancellation") },
            "The cancellation must be rethrown after the restore. Swallowing it leaves the " +
                "dispatcher believing this handler still consumes the gesture, so the next back " +
                "press is routed here instead of to the handler that took over -- the same defect " +
                "class as B-023, where the arbiter's only negative decision never reached its " +
                "consumer.",
        )
        assertTrue(
            helper.any { it.contains("onCancelled") },
            "The helper must expose a cancellation path to callers, so a caller can restore its " +
                "own state rather than being left with a half-applied gesture.",
        )
    }

    /**
     * Progress must be mapped to surface offset in the right direction.
     *
     * The back gesture travels inward from the screen edge while the dismissed surface travels
     * outward, so the offset is `start * (1 - progress)`. Getting the sign wrong produces a
     * surface that slides *in* as the user swipes back — which reads as an inverted gesture and
     * ships easily because nothing crashes. The arithmetic is asserted in `PredictiveBackTest`;
     * this asserts the *form* at the call site, so the two cannot drift apart.
     */
    @Test
    fun `progress maps to a diminishing offset, not an increasing one`() {
        val helper = textOf("presentation-core/src/main/java/ephyra/presentation/core/ui/navigation/PredictiveBack.kt")

        assertTrue(
            helper.contains("startOffset * (1f - progress)"),
            "backGestureOffset must be startOffset * (1 - progress): the gesture travels inward " +
                "while the surface travels outward.",
        )
        assertTrue(
            !helper.contains("startOffset * progress"),
            "backGestureOffset is inverted — a surface would slide in as the user swipes back.",
        )
    }

    /**
     * The manifest must keep opting in.
     *
     * Setting `enableOnBackInvokedCallback="false"` is the documented opt-out and would silently
     * disable every system predictive animation regardless of what the app code does, so it is
     * asserted rather than left to a reader's judgement.
     */
    @Test
    fun `the app still opts in to predictive back`() {
        val manifest = textOf("app/src/main/AndroidManifest.xml")

        assertTrue(
            manifest.contains("android:enableOnBackInvokedCallback=\"true\""),
            "The app must keep enableOnBackInvokedCallback=true, or the system back-to-home and " +
                "cross-activity animations are disabled and the in-app handlers have no gesture to " +
                "participate in.",
        )
    }

    /**
     * The shared helper must stay the only implementation.
     *
     * Two progress handlers means two places to fix the cancellation bug and two places to keep the
     * offset mapping consistent — which is how they drift. This asserts no module hand-rolls a
     * `PredictiveBackHandler` instead of calling the helper.
     */
    @Test
    fun `the shared helper is the only predictive back implementation`() {
        val offenders = mutableListOf<String>()
        for (path in presentationCoreSources()) {
            if (path.name == "PredictiveBack.kt") continue
            for (line in codeOf(path.absolutePath)) {
                if (line.contains("PredictiveBackHandler(") && !line.contains("import ")) {
                    offenders += "${path.name}: $line"
                }
            }
        }
        assertEquals(
            emptyList<String>(),
            offenders,
            "call PredictiveBackProgress / PredictiveBackDraggableProgress instead of a bare " +
                "PredictiveBackHandler, so the cancellation path and the offset mapping exist once",
        )
    }

    private fun presentationCoreSources(): List<File> {
        val root = sourceFile("presentation-core/src/main")
        assertTrue(root.exists(), "presentation-core/src/main not found")
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }
}
