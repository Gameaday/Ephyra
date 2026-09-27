package ephyra.feature.reader.viewer.pager

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ephyra.domain.reader.gesture.ReaderGestureEffect
import ephyra.domain.reader.viewport.PagerViewport
import ephyra.domain.reader.viewport.PagerViewportState
import ephyra.domain.reader.viewport.PagerZoomTransform
import ephyra.domain.reader.viewport.ViewportSize
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * `B-041` resolved, and the bridge from `E3` to an `E4-lab` record for `DEF-001`.
 *
 * # The tooling gap this closes
 *
 * `B-041` recorded that a transform gesture could not be driven on a device at all: `adb shell
 * input` takes one pointer per process invocation, so two taps cannot land inside the double-tap
 * window and no pinch can be produced. That capped `E4-lab` to non-transform evidence and left
 * `DEF-001` and `DEF-002` resting on their `E3` pointer tests.
 *
 * Compose's test API injects real `MotionEvent`s, so the gap was a tooling limit rather than a
 * platform one. This drives the same pointer stream the reader uses.
 *
 * # Why this is not `PagerViewportRenderTest` again
 *
 * That test sets a **static** transform and requires the raster to change. It proves the layer
 * renders but never drives a gesture. This injects a real two-finger pinch through the real
 * `pagerGestureStream`, the real arbiter and the real reducer, and requires the *rendered* raster
 * to change as a result. The transform is no longer supplied by the test; it is produced.
 *
 * # The frames are written to disk
 *
 * An `E4-lab` record has to cite something another person can re-derive, so both captures are
 * written under `app/build/e4lab/`. Re-running the test reproduces them, which is the difference
 * between evidence and a claim.
 */
@RunWith(AndroidJUnit4::class)
class PagerGestureInjectTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val effects = mutableListOf<ReaderGestureEffect>()

    private var latest: PagerViewportState = PagerViewportState()

    private val frameTick = mutableStateOf(0)

    @Composable
    private fun ZoomablePage() {
        var state by remember { mutableStateOf(PagerViewportState()) }
        var committed by remember { mutableStateOf(PagerZoomTransform.IDENTITY) }
        // Read so that `settleFrame()` can invalidate the tree and guarantee a frame for
        // `PixelCopy`; the value itself is deliberately unused.
        @Suppress("UNUSED_EXPRESSION")
        frameTick.value
        latest = state
        Box(
            Modifier
                .fillMaxSize()
                .onPagerViewportMeasured { size: ViewportSize ->
                    // `viewportSize` is what makes the viewport "measured", and
                    // `PagerViewport.onTransform` deliberately no-ops without it: a zero-sized
                    // viewport would divide by zero in the focal inversion. The first version of
                    // this host copied only the transform, leaving the viewport permanently
                    // unmeasured, so all 25 `TransformUpdated` effects correctly did nothing and
                    // the scale stayed at 1.0. The product was right; the harness was wrong.
                    state = state.copy(
                        viewportSize = size,
                        transform = PagerViewport.onViewportSizeChanged(state, size).transform,
                    )
                }
                .pagerGestureStream(
                    documentRevision = { "pager-gesture-inject" },
                    config = { pagerGestureConfig(state, TOUCH_SLOP, ZOOM_LOCK) },
                    onEffect = { effect ->
                        effects += effect
                        state = state.reduceEffect(effect, committed)
                        if (effect is ReaderGestureEffect.TransformCommitted) {
                            committed = state.transform
                        }
                    },
                )
                .background(Color.White)
                .pagerZoomLayer(state.transform)
                .testTag(PAGE)
                .drawBehind { drawStripes() },
        )
    }

    private fun DrawScope.drawStripes() {
        val stripe = 40f
        var x = 0f
        var light = true
        while (x < size.width) {
            drawRect(
                color = if (light) Color.White else Color.Black,
                topLeft = Offset(x, 0f),
                size = Size(stripe, size.height),
            )
            x += stripe
            light = !light
        }
    }

    private fun raster(): Bitmap =
        composeRule.onNodeWithTag(PAGE).captureToImage().asAndroidBitmap()

    /**
     * A capture that retries, for the PixelCopy timeout only.
     *
     * [settleFrame] guarantees there *is* a frame to copy, but on a software-rendered emulator
     * (`-gpu swiftshader_indirect`, which CI uses) the copy itself can exceed Compose's wait and
     * fail with "Failed waiting for PixelCopy!" even though the frame is there. That is an
     * infrastructure timeout, not a rendering fault: the four static capture tests in the sibling
     * suite pass on the same emulator, and only the one that captures *after* an injected gesture
     * hits it.
     *
     * Only the capture is retried. If the copy succeeds and the image is wrong, the assertions
     * below still fail exactly as before — a retry that could paper over a real defect would be
     * worse than the flake, so the failure message on exhaustion names the timeout explicitly
     * rather than letting a later assertion report a misleading value.
     */
    private fun rasterRetrying(attempts: Int = 4): Bitmap {
        var last: Throwable? = null
        repeat(attempts) { attempt ->
            try {
                return raster()
            } catch (e: Throwable) {
                if (!e.isPixelCopyTimeout()) throw e
                last = e
                settleFrame()
            }
        }
        throw AssertionError(
            "captureToImage failed with a PixelCopy timeout on all $attempts attempts. " +
                "This is a capture-timing failure on this renderer, not a rendering defect: the " +
                "assertions never ran.",
            last,
        )
    }

    private fun Throwable.isPixelCopyTimeout(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            val message = current.message
            if (message != null && message.contains("PixelCopy")) return true
            current = current.cause
        }
        return false
    }

    /**
     * Forces a fresh frame before a capture.
     *
     * `captureToImage` is implemented with `PixelCopy`, which waits for a *new* frame. Straight
     * after `performTouchInput` there may be nothing left to draw, so the copy times out with
     * "Failed waiting for PixelCopy". Reading [frameTick] in the composition and bumping it
     * invalidates the tree, guaranteeing a frame to copy without changing what is rendered.
     */
    private fun settleFrame() {
        composeRule.runOnIdle { frameTick.value = frameTick.value + 1 }
        composeRule.waitForIdle()
    }

    /** Colour transitions along one row. A solid page photographs identically at any scale. */
    private fun transitions(bitmap: Bitmap): Int {
        val y = bitmap.height / 2
        var count = 0
        var previous = bitmap.getPixel(0, y)
        for (x in 1 until bitmap.width) {
            val current = bitmap.getPixel(x, y)
            if (current != previous) {
                count++
                previous = current
            }
        }
        return count
    }

    /**
     * Writes a frame where the host can retrieve it.
     *
     * The test executes on the device, so a relative path resolves inside the device's own
     * working directory and `adb pull` never sees it. The first version of this test wrote to
     * `app/build/e4lab` and died with `FileNotFoundException` -- and because the write ran first
     * it masked the gesture assertions entirely, so a broken artifact path looked like a broken
     * gesture. Hence: external app storage, and the assertions run before anything is persisted.
     */
    private fun write(bitmap: Bitmap, name: String): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "e4lab").apply { mkdirs() }
        val file = File(dir, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    @Test
    fun aRealPinchChangesTheRenderedPage() {
        composeRule.setContent { ZoomablePage() }
        composeRule.waitForIdle()

        val before = rasterRetrying()
        val beforeScale = latest.transform.scale
        val beforeTransitions = transitions(before)

        // A genuine two-finger spread through the production pointer stream. `pinch` takes both
        // start and end positions, so this is the gesture `adb` cannot express at all.
        composeRule.onNodeWithTag(PAGE).performTouchInput {
            pinch(
                start0 = center * 0.4f,
                end0 = center * 1.4f,
                start1 = center,
                end1 = center,
            )
        }
        composeRule.waitForIdle()
        settleFrame()
        val after = rasterRetrying()
        val afterScale = latest.transform.scale
        val afterTransitions = transitions(after)

        assertTrue(
            "A real pinch must move the viewport out of fit scale. " +
                "scale $beforeScale -> $afterScale, effects=${effects.map { it::class.simpleName }}. " +
                "No transform means DEF-001 is back: the arbiter is unwired, or the pinch never " +
                "reached the pointer stream.",
            afterScale > beforeScale,
        )

        assertTrue(
            "The transform the gesture produced must reach the screen. Rendered raster " +
                "${before.width}x${before.height}/$beforeTransitions stripes -> " +
                "${after.width}x${after.height}/$afterTransitions stripes. A changed scale with an " +
                "unchanged raster is the exact DEF-001 defect: state moved and nothing drew.",
            before.width != after.width || beforeTransitions != afterTransitions,
        )

        // The gesture is asserted before anything is persisted. A capture path that breaks must
        // not be able to present itself as a broken gesture.
        //
        // The measurements are also logged, because that is the part that survives: Gradle
        // uninstalls both APKs when the run ends, taking the device-side frames with them. The
        // logcat file Gradle keeps, and the assertion messages, are the durable record.
        android.util.Log.i(
            TAG,
            "def-001 pinch: scale $beforeScale -> $afterScale; raster " +
                "${before.width}x${before.height}/$beforeTransitions stripes -> " +
                "${after.width}x${after.height}/$afterTransitions stripes; " +
                "effects=${effects.size}",
        )

        val beforeFile = write(before, "def-001-pinch-before")
        val afterFile = write(after, "def-001-pinch-after")
        assertTrue(
            "Both frames must be written so an E4-lab record can cite them. " +
                "before=$beforeFile after=$afterFile. Pull them with: adb pull <path>",
            beforeFile.isFile && afterFile.isFile &&
                beforeFile.length() > 0 && afterFile.length() > 0,
        )
    }

    private companion object {
        const val PAGE = "pager-gesture-page"
        const val TAG = "E4Lab"
        const val TOUCH_SLOP = 8f
        const val ZOOM_LOCK = 1.01f
    }
}
