package ephyra.feature.reader.viewer.pager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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

    @Composable
    private fun StripedPage(transform: PagerZoomTransform) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.White)
                .pagerZoomLayer(transform)
                .testTag(STRIPES)
                .drawBehind { drawStripes() },
        )
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

    /** Count colour transitions along one row: the number of stripes actually rendered. */
    private fun renderedStripes(): Int {
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
        return transitions
    }

    @Test
    fun scalingTheLayerChangesTheRenderedImage() {
        composeRule.setContent { StripedPage(PagerZoomTransform.IDENTITY) }
        composeRule.waitForIdle()
        val atFit = renderedStripes()

        composeRule.setContent { StripedPage(PagerZoomTransform(2f, 0f, 0f)) }
        composeRule.waitForIdle()
        val zoomed = renderedStripes()

        assertTrue(
            "A 2x graphics layer must change the rendered image; stripes went $atFit -> $zoomed. " +
                "An unchanged image means the layer is not being applied, which is the DEF-001 " +
                "defect class this test exists to catch.",
            atFit != zoomed,
        )
    }

    @Test
    fun zoomingInShowsFewerStripesAcrossTheViewport() {
        composeRule.setContent { StripedPage(PagerZoomTransform.IDENTITY) }
        composeRule.waitForIdle()
        val atFit = renderedStripes()

        composeRule.setContent { StripedPage(PagerZoomTransform(2f, 0f, 0f)) }
        composeRule.waitForIdle()
        val zoomed = renderedStripes()

        assertTrue(
            "Zooming in magnifies the page so fewer stripes fit across the same width. Count went " +
                "$atFit -> $zoomed; a count that does not fall means the raster is not scaling.",
            zoomed < atFit,
        )
    }

    private companion object {
        const val STRIPES = "pager-stripes"
    }
}
