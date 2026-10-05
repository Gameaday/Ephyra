package ephyra.presentation.core.ui.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Coverage for the one piece of [PredictiveBack.kt] that can be asserted without a gesture.
 *
 * The composables themselves need a real `OnBackInvokedDispatcher` and a system gesture to
 * exercise, which is device territory. `backGestureOffset` is the mapping they both depend on, and
 * it is pure arithmetic — so it is tested directly rather than left to be discovered by a user
 * swiping.
 *
 * The failure this guards is specifically an **inverted** mapping. The back gesture travels inward
 * from the screen edge while the dismissed surface travels outward, so the offset is
 * `start * (1 - progress)`. Writing `start * progress` instead produces a surface that slides *in*
 * as the user swipes back and then vanishes — which reads as "the gesture is broken" rather than
 * "the sign is wrong", and is easy to ship because nothing crashes.
 */
class PredictiveBackTest {

    @Test
    fun `no progress leaves the surface where it started`() {
        assertEquals(400f, backGestureOffset(400f, 0f))
    }

    @Test
    fun `full progress fully dismisses the surface`() {
        assertEquals(0f, backGestureOffset(400f, 1f))
    }

    @Test
    fun `the surface moves outward as the gesture advances`() {
        // progress 0 -> surface fully visible, progress 1 -> fully dismissed, and it must be
        // monotonic in between. An inverted sign fails the first assertion; a non-monotonic
        // mapping fails this one.
        val start = 400f
        val offsets = listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map { backGestureOffset(start, it) }

        assertEquals(listOf(400f, 300f, 200f, 100f, 0f), offsets)
        assertTrue(
            offsets.zipWithNext().all { (a, b) -> b < a },
            "offset must decrease monotonically as the gesture advances, got $offsets",
        )
    }

    /**
     * Regression: the handler contract requires the progress flow to be collected on EVERY path.
     *
     * The early return for a fully settled surface (offset == 0) originally skipped collection,
     * which crashed any back swipe begun over a settled sheet with "You must collect the
     * progress flow" (user report: series page -> chapter filter/sort sheet -> swipe back).
     * This is a source-structure gate, following MotionConsistencyTest's pattern: the failure
     * only reproduces with a real edge gesture, which a unit test cannot drive.
     */
    @Test
    fun `every path through the handler collects the progress flow`() {
        var dir = java.io.File(".").absoluteFile
        var source: java.io.File? = null
        while (dir != null) {
            val candidate = java.io.File(
                dir,
                "presentation-core/src/main/java/ephyra/presentation/core/ui/navigation/PredictiveBack.kt",
            )
            if (candidate.exists()) {
                source = candidate
                break
            }
            dir = dir.parentFile
        }
        checkNotNull(source) { "PredictiveBack.kt not found" }
        val handler = source.readText()
            .substringAfter("PredictiveBackHandler(enabled = enabled) { flow ->")
            .substringBefore("    }
}")
        assertTrue(
            handler.contains("flow.collect { }") &&
                handler.indexOf("flow.collect { }") < handler.indexOf("return@PredictiveBackHandler"),
            "The zero-offset early return must drain the flow (flow.collect { }) before " +
                "returning — PredictiveBackHandler throws 'You must collect the progress flow' " +
                "when any path skips collection.",
        )
    }

    /**
     * A surface already partly displaced must have its gesture seeded from where it actually is.
     *
     * This is why [PredictiveBackDraggableProgress] reads `state.offset` rather than assuming a
     * resting position. If it assumed the sheet was fully open, a back gesture begun while the user
     * was already dragging it would snap the sheet to a hardcoded offset at the first progress
     * event — a visible jump on the exact surface the gesture is meant to feel continuous.
     */
    @Test
    fun `a partially displaced surface is measured from its own position`() {
        val half = backGestureOffset(400f, 0.5f)
        assertEquals(200f, half)
        assertEquals(100f, backGestureOffset(half, 0.5f))
    }

    @Test
    fun `an offset of zero is not inverted into a negative position`() {
        // Guards the degenerate case rather than the arithmetic: with startOffset 0 the result is
        // 0 at every progress, so no caller can produce a negative translation that would fling a
        // surface off-screen on the opposite side.
        listOf(0f, 0.5f, 1f).forEach { progress ->
            assertEquals(0f, backGestureOffset(0f, progress))
        }
    }

    @Test
    fun `progress outside zero to one extrapolates rather than clamping silently`() {
        // The dispatcher is documented to emit 0..1, but a value outside that range must not throw
        // or produce NaN -- a surface that flickers to NaN translation renders as nothing at all.
        val over = backGestureOffset(400f, 1.5f)
        assertEquals(-200f, over, "extrapolation is arithmetic, not clamping; no NaN, no throw")
        assertTrue(over.isFinite())
    }
}
