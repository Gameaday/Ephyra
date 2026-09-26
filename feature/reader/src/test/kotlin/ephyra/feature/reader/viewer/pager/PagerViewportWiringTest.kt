package ephyra.feature.reader.viewer.pager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.domain.reader.gesture.ReaderGestureEffect
import ephyra.domain.reader.viewport.PagerViewport
import ephyra.domain.reader.viewport.PagerViewportState
import ephyra.domain.reader.viewport.PagerZoomTransform
import ephyra.domain.reader.viewport.ViewportSize
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The `RDR-004` Compose adapter, at the level this harness can actually verify.
 *
 * ## What this cannot prove, and why that matters
 *
 * The obvious test for `DEF-001` is "pinch, then assert the page got bigger on screen". That test was
 * written here first and **failed**, and the reason is recorded rather than hidden.
 *
 * Two things make it impossible here:
 *
 * 1. `graphicsLayer` is a render-only transform. It does not change layout, so `SemanticsNode.size`
 *    correctly stays constant whether or not the layer is applied. A layout assertion cannot observe
 *    a graphics transform at all.
 * 2. `captureToImage` under Robolectric does not rasterise the `graphicsLayer` transform. This was
 *    proven, not assumed: a control test applying a **static** `scaleX = 2f`, with no gesture
 *    involved, produced an identical pixel count to the unscaled page. If the static layer does not
 *    alter the capture, no pixel assertion here measures the transform.
 *
 * So "the computed transform reaches a modifier" is a claim this environment **cannot falsify**. Under
 * `adr/0009` that makes it a blocker named as such, never a pass.
 *
 * ## What this does prove
 *
 * That the wiring is correct end to end *up to* the modifier: real pointer events reach the real
 * adapter, reach the real arbiter, return as effects, and reduce into a viewport transform that
 * differs from the one the gesture started from. The one unverified link — `graphicsLayer`
 * rasterisation — is exactly the link that needs `E3` instrumentation.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h800dp-xhdpi")
class PagerViewportWiringTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Composable
    private fun ZoomableSurface(
        report: (PagerViewportState) -> Unit,
        effectSink: MutableList<ReaderGestureEffect>,
    ) {
        var state by remember { mutableStateOf(PagerViewportState()) }
        var committed by remember { mutableStateOf(PagerZoomTransform.IDENTITY) }
        report(state)
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    val measured = ViewportSize(size.width.toFloat(), size.height.toFloat())
                    val action = PagerViewport.onViewportSizeChanged(state, measured)
                    state = state.copy(transform = action.transform, viewportSize = measured)
                }
                .pagerGestureStream(
                    documentRevision = { "page-1" },
                    config = { pagerGestureConfig(state, touchSlop = 20f, zoomLockThreshold = 1.1f) },
                    onEffect = { effect ->
                        effectSink += effect
                        if (effect is ReaderGestureEffect.TransformCommitted) {
                            committed = state.transform
                        }
                        state = state.reduceEffect(effect, committed)
                    },
                )
                .testTag(SURFACE),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Gray)
                    .pagerZoomLayer(state.transform)
                    .testTag(PAGE),
            )
        }
    }

    private fun pinchOut() {
        composeRule.onNodeWithTag(SURFACE).performTouchInput {
            pinch(
                start0 = center,
                end0 = center + Offset(120f, 120f),
                start1 = center,
                end1 = center - Offset(120f, 120f),
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `a real pinch drives the state owner to a different transform`() {
        var state = PagerViewportState()
        composeRule.setContent {
            ZoomableSurface(report = { state = it }, effectSink = mutableListOf())
        }
        composeRule.waitForIdle()
        assertTrue(
            "Precondition: the surface must be measured for zoom to apply",
            state.isMeasured,
        )

        val atFit = state.transform
        pinchOut()

        assertTrue(
            "A pinch through the real adapter and arbiter must change the transform the viewport " +
                "holds; scale went ${atFit.scale} -> ${state.transform.scale}. The gesture path is " +
                "not reaching the state owner.",
            state.transform.scale > atFit.scale,
        )
    }

    @Test
    fun `the transform is bounded by the policy maximum`() {
        var state = PagerViewportState()
        composeRule.setContent {
            ZoomableSurface(report = { state = it }, effectSink = mutableListOf())
        }
        composeRule.waitForIdle()

        repeat(4) { pinchOut() }

        assertTrue(
            "Repeated pinches must stop at the policy ceiling of 5x rather than running away; scale " +
                "is ${state.transform.scale}. An unbounded transform is an unbounded decode and " +
                "layout cost, and is what a max-scale clamp exists to prevent.",
            state.transform.scale <= 5.001f,
        )
    }

    @Test
    fun `the gesture config reflects the rendered scale so the pager cannot drift`() {
        val fit = PagerViewportState()
        val zoomed = PagerViewportState(transform = PagerZoomTransform(3f, 0f, 0f))

        val claimsAtFit = pagerGestureConfig(fit, 20f, 1.1f).meaningfullyZoomed
        val claimsZoomed = pagerGestureConfig(zoomed, 20f, 1.1f).meaningfullyZoomed

        // JUnit4's assertTrue/assertFalse take the message first, so the condition is negated into
        // the assertion rather than written as `!x` in the condition position.
        assertFalse(
            "At fit scale the pager owns the drag, so the viewport must not claim the pan",
            claimsAtFit,
        )
        assertTrue(
            "When the page is visibly zoomed the viewport must claim the pan, or the user cannot " +
                "move the content they zoomed into",
            claimsZoomed,
        )
    }

    @Test
    fun `delegation does not discard a zoom the user already has`() {
        val zoomed = PagerViewportState(
            transform = PagerZoomTransform(2.5f, 40f, 10f),
            viewportSize = ViewportSize(1080f, 1920f),
        )
        assertTrue(
            "A delegated scroll must not reset the zoom; a user who is mid-zoom and swipes would " +
                "otherwise lose their place",
            zoomed.reduceEffect(ReaderGestureEffect.DelegateSingleScroll).transform == zoomed.transform,
        )
    }

    @Test
    fun `page-level effects leave the viewport transform untouched`() {
        val zoomed = PagerViewportState(transform = PagerZoomTransform(2f, 15f, 5f))
        val effects = listOf(
            ReaderGestureEffect.SingleTap(10f, 10f),
            ReaderGestureEffect.TapCandidate(10f, 10f),
            ReaderGestureEffect.LongPress,
        )
        for (effect in effects) {
            assertTrue(
                "The viewport owns transform state, not workflow commands; $effect must not move it",
                zoomed.reduceEffect(effect).transform == zoomed.transform,
            )
        }
    }

    private companion object {
        const val SURFACE = "pager-surface"
        const val PAGE = "pager-page"
    }
}
