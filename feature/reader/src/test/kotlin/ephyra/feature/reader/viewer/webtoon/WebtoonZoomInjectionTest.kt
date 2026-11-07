package ephyra.feature.reader.viewer.webtoon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.domain.reader.viewport.WebtoonZoomPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The `DEF-002` hostable-path guard (A4, `RDR-005`).
 *
 * The continuous reader's zoom path used to be wired inline in `ComposeWebtoonReader`, which the
 * Phase-7 entry recorded as having no hostable path for gesture injection: the `E3` device test
 * could not be re-driven, and nothing below device level could prove the pointer stream actually
 * reached the transform. `B-022` established that a real two-pointer gesture is assertable at
 * `E2` under Robolectric, so with the wiring extracted to `WebtoonZoomGestureHost` and the
 * production `webtoonGestureStream` modifier, the full path — pointer events, arbiter, reducer,
 * transform state, scroll-owner correction — is now guarded on every CI run.
 *
 * What this deliberately does **not** claim: a raster capture. Pixel geometry of the scaled
 * document stays an `E3`/`E4-lab` device leg (`B-050` names the channel; this environment has
 * no emulator), so these assertions are on the transform state and the routing, never on rendered
 * pixels and never as a device pass.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class WebtoonZoomInjectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var zoomState: WebtoonZoomState
    private val corrections = mutableStateListOf<Float>()
    private var viewportWidth = 0f

    private fun host(enabled: Boolean = true) {
        zoomState = WebtoonZoomState(min = 1f, max = 4f)
        corrections.clear()
        val zoomHost = WebtoonZoomGestureHost(zoomState) { correction -> corrections += correction }
        composeRule.setContent {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Gray)
                    .testTag(SURFACE)
                    .onGloballyPositioned { coordinates ->
                        viewportWidth = coordinates.size.width.toFloat()
                        zoomState.setViewportWidth(viewportWidth)
                    }
                    .webtoonGestureStream(
                        enabled = enabled,
                        documentRevision = { "chapter-1" },
                        getScale = { zoomState.scale },
                        onEffect = { effect ->
                            zoomHost.handleEffect(
                                effect = effect,
                                itemSize = { IntSize(100, 400) },
                                onSingleTap = { _, _ -> },
                                onLongTap = {},
                            )
                        },
                    ),
            )
        }
    }

    private fun spreadPinch() {
        composeRule.onNodeWithTag(SURFACE).performTouchInput {
            pinch(
                start0 = center,
                end0 = center + Offset(80f, 0f),
                start1 = center,
                end1 = center - Offset(80f, 0f),
            )
        }
    }

    @Test
    fun `a real two-pointer pinch zooms the document and hands corrections to the scroll owner`() {
        host()

        spreadPinch()

        assertTrue(
            "a spreading pinch must zoom the document past 1.5x; scale was ${zoomState.scale}",
            zoomState.scale > 1.5f,
        )
        assertTrue(
            "the zoom must respect the configured maximum of 4; scale was ${zoomState.scale}",
            zoomState.scale <= 4.0001f,
        )
        assertTrue(
            "each transform change must hand its focal correction to the list owner; got $corrections",
            corrections.isNotEmpty(),
        )
    }

    @Test
    fun `a pinch on a disabled stream moves nothing`() {
        host(enabled = false)

        spreadPinch()

        assertEquals(
            "with double-tap-zoom off the pinch must be ignored entirely; scale was ${zoomState.scale}",
            1f,
            zoomState.scale,
            0.0001f,
        )
        assertTrue("a disabled stream must not emit corrections; got $corrections", corrections.isEmpty())
    }

    @Test
    fun `a vertical drag at fit delegates to the list rather than starting a transform`() {
        host()

        composeRule.onNodeWithTag(SURFACE).performTouchInput {
            down(0, center)
            moveBy(Offset(0f, 150f))
            up(0)
        }

        assertEquals(
            "a continuous vertical drag belongs to the LazyColumn, not the viewport",
            1f,
            zoomState.scale,
            0.0001f,
        )
        assertTrue("a delegated drag must not produce focal corrections; got $corrections", corrections.isEmpty())
    }

    @Test
    fun `a horizontal drag while zoomed pans within the policy clamp`() {
        host()
        spreadPinch()
        val scaleAfterPinch = zoomState.scale
        assertTrue("pinch precondition failed; scale was $scaleAfterPinch", scaleAfterPinch > 1f)

        composeRule.onNodeWithTag(SURFACE).performTouchInput {
            down(0, center)
            moveBy(Offset(-140f, 0f))
            up(0)
        }

        val maxOffset = WebtoonZoomPolicy(minScale = 1f, maxScale = 4f)
            .maxOffsetX(zoomState.scale, viewportWidth)
        assertTrue(
            "a horizontal pan while zoomed must move the document horizontally; offset was " +
                "${zoomState.offsetX}",
            zoomState.offsetX != 0f,
        )
        assertTrue(
            "the horizontal pan must respect maxOffsetX(scale=${zoomState.scale}, " +
                "width=$viewportWidth); offset was ${zoomState.offsetX}",
            abs(zoomState.offsetX) <= maxOffset + 0.001f,
        )
        assertEquals(
            "a horizontal pan must not change the scale",
            scaleAfterPinch,
            zoomState.scale,
            0.0001f,
        )
    }

    private companion object {
        const val SURFACE = "webtoon-zoom-surface"
    }
}
