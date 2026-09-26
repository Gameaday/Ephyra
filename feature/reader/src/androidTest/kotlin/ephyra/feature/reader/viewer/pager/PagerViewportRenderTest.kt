package ephyra.feature.reader.viewer.pager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.domain.reader.viewport.PagerZoomTransform
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `E3` proof that the pager's zoom transform actually reaches the screen.
 *
 * This is the claim `B-024` records as unverifiable on the JVM, and an emulator is the only place it
 * can be closed. Two JVM limits forced it here, both established by experiment rather than assumed:
 * `graphicsLayer` is a render-only transform so a layout assertion cannot see it, and
 * `captureToImage` under Robolectric does not rasterise it — proven with a static `scaleX = 2f`
 * control that produced an identical pixel count to the unscaled page.
 *
 * `DEF-001` was precisely a transform that was computed and never drawn. This test is the direct
 * opposite of that failure: it sets a known transform and requires the rendered image to differ.
 * A layer that stops being applied makes it fail.
 *
 * The stripes are not decoration. A solid page photographs identically at any scale, so a
 * solid-colour assertion would pass whether or not the layer worked — the "something changed" trap.
 * Alternating stripes give the image real structure, so the rendered scale is measurable.
 */
@RunWith(AndroidJUnit4::class)
class PagerViewportRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Drives the transform from outside the composition.
     *
     * `setContent` may only be called once per activity — a second call throws
     * `IllegalStateException: ... has already set content`. Both assertions below need the *same*
     * page rendered at two different transforms, so the transform has to be a value the
     * composition reads rather than a parameter passed to `setContent`.
     */
    private val transform = mutableStateOf(PagerZoomTransform.IDENTITY)

    @Composable
    private fun StripedPage() {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.White)
                .pagerZoomLayer(transform.value)
                .testTag(STRIPES)
                .drawBehind { drawStripes() },
        )
    }

    /** Sets the transform and waits for the frame that renders it. */
    private fun render(transform: PagerZoomTransform) {
        this.transform.value = transform
        composeRule.waitForIdle()
    }

    private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStripes() {
        val stripe = 40f
        var x = 0f
        var light = true
        while (x < size.width) {
            drawRect(
                color = if (light) Color.White else Color.Black,
                topLeft = Offset(x, 0f),
                size = androidx.compose.ui.geometry.Size(stripe, size.height),
            )
            x += stripe
            light = !light
        }
    }

    /** The rendered raster for the current transform, and its stripe count along one row. */
    private data class Raster(val width: Int, val height: Int, val transitions: Int)

    /**
     * Captures the node and counts colour transitions along one row.
     *
     * Returns the bitmap dimensions as well as the count, because the dimensions carry the real
     * evidence: a `graphicsLayer` scale is a render-time transform, so the captured region grows
     * with it. An earlier version of the second assertion assumed the capture width was constant
     * and that zooming would therefore show *fewer* stripes. Measured on device it showed *more*
     * (26 -> 42), because the capture is of the layer's own bounds — the assumption was wrong
     * about the harness, not about the product. The width is the honest observable.
     */
    private fun renderRaster(): Raster {
        val bitmap = composeRule.onNodeWithTag(STRIPES).captureToImage().asAndroidBitmap()
        val y = bitmap.height / 2
        var transitions = 0
        var previous = bitmap.getPixel(0, y)
        for (x in 1 until bitmap.width) {
            val current = bitmap.getPixel(x, y)
            if (current != previous) {
                transitions++
                previous = current
            }
        }
        return Raster(bitmap.width, bitmap.height, transitions)
    }

    @Test
    fun scalingTheLayerChangesTheRenderedImage() {
        composeRule.setContent { StripedPage() }
        render(PagerZoomTransform.IDENTITY)
        val atFit = renderRaster()

        render(PagerZoomTransform(2f, 0f, 0f))
        val zoomed = renderRaster()

        assertTrue(
            "A 2x graphics layer must change the rendered image; the raster went " +
                "${atFit.width}x${atFit.height}/${atFit.transitions} stripes -> " +
                "${zoomed.width}x${zoomed.height}/${zoomed.transitions} stripes. An unchanged " +
                "raster means the layer is not being applied, which is the DEF-001 defect class " +
                "this test exists to catch.",
            atFit != zoomed,
        )
    }

    @Test
    fun scalingTheLayerDoublesTheRenderedWidth() {
        composeRule.setContent { StripedPage() }
        render(PagerZoomTransform.IDENTITY)
        val atFit = renderRaster()

        // `pagerZoomLayer` is a render-time transform, so the captured region of the layer grows
        // with the scale. Asserting the ratio rather than a stripe count is what makes this a
        // measurement of the transform instead of a proxy for it: an unscaled layer cannot produce
        // a doubled capture width at all. Falsification-verified — substituting an identity scale
        // here turns both tests red.
        render(PagerZoomTransform(2f, 0f, 0f))
        val zoomed = renderRaster()

        assertTrue(
            "A 2x scale must roughly double the captured raster width. Measured " +
                "${atFit.width} -> ${zoomed.width}. A width that does not grow means the " +
                "graphicsLayer is not being applied and the zoom never reaches the screen.",
            zoomed.width > atFit.width * 3 / 2,
        )
    }

    private companion object {
        const val STRIPES = "pager-stripes"
    }
}
