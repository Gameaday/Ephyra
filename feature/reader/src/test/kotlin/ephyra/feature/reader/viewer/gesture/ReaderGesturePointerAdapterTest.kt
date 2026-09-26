package ephyra.feature.reader.viewer.gesture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.domain.reader.gesture.ReaderGestureConfig
import ephyra.domain.reader.gesture.ReaderGestureEffect
import ephyra.domain.reader.gesture.ReaderViewportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pointer-event coverage for the Android adapter in front of the pure reader gesture arbiter.
 *
 * `ReaderGestureArbiter` and `ReaderTapSequencer` are `CODE_COMPLETE` at `E2` in `core:domain`, and
 * `ReaderGesturePointerAdapter` compiles — but it had **no** coverage at all, so nothing had ever
 * confirmed that translating Compose pointer events into arbiter samples is correct. Pure tests
 * cannot see it: a wrong event pass, a consumed flag read at the wrong moment, or a mis-sequenced
 * up-event all yield a correct-looking arbiter driven by incorrect input.
 *
 * These drive real pointer input through the real adapter and assert the effects the arbiter must
 * produce. This guard has to exist *before* the adapter is given a production call site.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class ReaderGesturePointerAdapterTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val effects = mutableStateListOf<ReaderGestureEffect>()

    private fun host(meaningfullyZoomed: Boolean = false, touchSlop: Float = 20f) {
        val config = ReaderGestureConfig(
            viewportMode = ReaderViewportMode.PAGED,
            touchSlop = touchSlop,
            meaningfullyZoomed = meaningfullyZoomed,
        )
        composeRule.setContent {
            ReaderGestureSurface(effects = effects, config = config)
        }
    }

    @Composable
    private fun ReaderGestureSurface(
        effects: MutableList<ReaderGestureEffect>,
        config: ReaderGestureConfig,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Gray)
                .testTag(SURFACE)
                .pointerInput(Unit) {
                    detectReaderGestures(
                        documentRevision = { "page-1" },
                        config = { config },
                        onEffect = { effects += it },
                    )
                },
        )
    }

    @Test
    fun `a two-pointer pinch claims the transform and reports a real zoom factor`() {
        host()

        composeRule.onNodeWithTag(SURFACE).performTouchInput {
            pinch(
                start0 = center,
                end0 = center + Offset(80f, 80f),
                start1 = center,
                end1 = center - Offset(80f, 80f),
            )
        }

        val started = effects.filterIsInstance<ReaderGestureEffect.TransformStarted>()
        assertTrue(
            "A two-pointer pinch must claim the transform; effects were $effects. The arbiter " +
                "needs >= 2 pressed pointers to start one, so a failure here means the adapter is " +
                "not forwarding the pressed-pointer count.",
            started.isNotEmpty(),
        )
        // The first sample of a pinch is emitted when the second pointer lands, before the fingers
        // have separated, so `calculateZoom` is legitimately 1.0 there. The real factor arrives on
        // the update that follows, which is what the viewport actually drives its transform from.
        val updated = effects.filterIsInstance<ReaderGestureEffect.TransformUpdated>()
        assertTrue(
            "A spreading pinch must report a zoom factor greater than 1.0 so the viewport zooms " +
                "in rather than only panning; effects were $effects",
            updated.any { it.zoomChange > 1f },
        )
    }

    @Test
    fun `releasing a pinch commits the transform`() {
        host()

        composeRule.onNodeWithTag(SURFACE).performTouchInput {
            pinch(
                start0 = center,
                end0 = center + Offset(80f, 80f),
                start1 = center,
                end1 = center - Offset(80f, 80f),
            )
        }

        assertTrue(
            "Releasing every pointer after a transform must emit TransformCommitted so the " +
                "viewport can settle; effects were $effects",
            effects.any { it is ReaderGestureEffect.TransformCommitted },
        )
    }

    @Test
    fun `single-pointer pan at fit delegates scrolling instead of claiming the viewport`() {
        host(meaningfullyZoomed = false)

        composeRule.onNodeWithTag(SURFACE).performTouchInput {
            down(0, center)
            moveBy(Offset(120f, 0f))
            up(0)
        }

        assertTrue(
            "At fit scale a drag belongs to the page navigator, not the viewport; effects were $effects",
            effects.contains(ReaderGestureEffect.DelegateSingleScroll),
        )
        assertTrue(
            "A fit-scale drag must not claim a transform; effects were $effects",
            effects.none { it is ReaderGestureEffect.TransformStarted },
        )
    }

    @Test
    fun `single-pointer pan while meaningfully zoomed claims the viewport`() {
        host(meaningfullyZoomed = true)

        composeRule.onNodeWithTag(SURFACE).performTouchInput {
            down(0, center)
            moveBy(Offset(120f, 0f))
            up(0)
        }

        assertTrue(
            "When the page is genuinely zoomed the viewport owns the pan, or the user cannot move " +
                "the content they zoomed into; effects were $effects",
            effects.any { it is ReaderGestureEffect.TransformStarted },
        )
    }

    @Test
    fun `a drag smaller than touch slop stays a tap candidate`() {
        host()

        composeRule.onNodeWithTag(SURFACE).performTouchInput {
            down(0, center)
            moveBy(Offset(4f, 0f))
            up(0)
        }

        assertEquals(
            "Movement inside touch slop must remain a tap candidate so a sloppy tap is not " +
                "swallowed as a scroll or a pan; effects were $effects",
            emptyList<ReaderGestureEffect>(),
            effects.toList(),
        )
    }

    private companion object {
        const val SURFACE = "gesture-surface"
    }
}
