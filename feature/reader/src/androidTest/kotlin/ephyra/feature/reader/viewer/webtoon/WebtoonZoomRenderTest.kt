package ephyra.feature.reader.viewer.webtoon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.domain.reader.viewport.WebtoonDocumentZoom
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `E3` proof for the continuous reader's zoom, the counterpart to `PagerViewportRenderTest`.
 *
 * Two reported defects are claimed fixed here, and this is where they can finally be checked on a
 * device rather than argued about:
 *
 *  - `DEF-002`, "webtoon pinch mainly widens content": the old per-item transform hardcoded
 *    `scaleY = 1f`, so the vertical axis was never scaled and a pinch could only widen the strip.
 *  - `DEF-003`, "sliced content overlaps": the transform lived on each `LazyColumn` item, so a
 *    scaled item painted outside the slot the list still measured, and neighbours collided.
 *
 * The second is why the transform is asserted **on the container**. An item-level transform can
 * satisfy a "does anything scale" check while still disagreeing with layout, so these tests
 * measure the container's raster — the only place a single document-space transform can live.
 *
 * The bands are not decoration. A solid page photographs identically at any scale, so a
 * "something changed" assertion would pass whether or not the layer worked. Horizontally banded
 * strips give the image measurable structure in *both* axes, which is what lets the vertical claim
 * be tested at all.
 */
@RunWith(AndroidJUnit4::class)
class WebtoonZoomRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zoom = mutableStateOf(WebtoonDocumentZoom.IDENTITY)

    @Composable
    private fun StripedStrips() {
        // Mirrors `ComposeWebtoonReader`: one `graphicsLayer` on the scroll container, with
        // `transformOrigin` at the top-start so the list's own offset stays meaningful in document
        // units. Strips are banded horizontally so that scaling Y is observable, not just X.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom.value.scale
                    scaleY = zoom.value.scale
                    translationX = zoom.value.offsetX
                    transformOrigin = TransformOrigin(0f, 0f)
                    clip = true
                }
                .testTag(STRIPS),
        ) {
            items(List(STRIP_COUNT) { it }) { index ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(if (index % 2 == 0) Color.White else Color.Black)
                        .drawBehind { drawBands(index) },
                )
            }
        }
    }

    private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBands(index: Int) {
        val band = 40f
        var y = 0f
        var light = index % 2 == 0
        while (y < size.height) {
            drawRect(
                color = if (light) Color.White else Color.Black,
                topLeft = Offset(0f, y),
                size = androidx.compose.ui.geometry.Size(size.width, band),
            )
            y += band
            light = !light
        }
    }

    /**
     * Strips of a fixed height, each a distinct flat colour.
     *
     * Two deliberate differences from [StripedStrips], both required by the measurement:
     *
     * - **Flat colour per strip**, not bands. The claim under test is *where the boundaries between
     *   slices land*, so each slice has to be one unambiguous colour and a boundary is a colour
     *   change. Banding would put a transition every few pixels and there would be nothing to
     *   locate.
     * - **Fixed height rather than `fillMaxSize`.** `fillMaxSize` makes each item a full viewport,
     *   so the only boundary is at the bottom edge — and at 2x that boundary moves to twice the
     *   viewport height, which is clipped away. The quantity under test would become unobservable
     *   exactly when it is most interesting. Fixed-height items keep the first boundary on screen
     *   at both scales.
     */
    @Composable
    private fun TintedStrips(stripHeightDp: Int) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom.value.scale
                    scaleY = zoom.value.scale
                    translationX = zoom.value.offsetX
                    transformOrigin = TransformOrigin(0f, 0f)
                    clip = true
                }
                .testTag(STRIPS),
        ) {
            items(List(STRIP_COUNT) { it }) { index ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(stripHeightDp.dp)
                        .background(TINT[index % TINT.size]),
                )
            }
        }
    }

    private data class Raster(val width: Int, val height: Int, val columnTransitions: Int)

    /** Captures the container and counts colour transitions down one column. */
    private fun renderRaster(): Raster {
        val bitmap = composeRule.onNodeWithTag(STRIPS).captureToImage().asAndroidBitmap()
        val x = bitmap.width / 2
        var transitions = 0
        var previous = bitmap.getPixel(x, 0)
        for (y in 1 until bitmap.height) {
            val current = bitmap.getPixel(x, y)
            if (current != previous) {
                transitions++
                previous = current
            }
        }
        return Raster(bitmap.width, bitmap.height, transitions)
    }

    private fun render(next: WebtoonDocumentZoom) {
        zoom.value = next
        composeRule.waitForIdle()
    }

    @Test
    fun scalingTheContainerChangesTheRenderedImage() {
        composeRule.setContent { StripedStrips() }
        render(WebtoonDocumentZoom.IDENTITY)
        val atFit = renderRaster()

        render(WebtoonDocumentZoom(scale = 2f, offsetX = 0f))
        val zoomed = renderRaster()

        assertTrue(
            "A 2x document transform must change the rendered image; the raster went " +
                "${atFit.width}x${atFit.height}/${atFit.columnTransitions} transitions -> " +
                "${zoomed.width}x${zoomed.height}/${zoomed.columnTransitions} transitions. An " +
                "unchanged raster means the container transform is not being applied.",
            atFit != zoomed,
        )
    }

    @Test
    fun scalingTheContainerDoublesBothDimensions() {
        composeRule.setContent { StripedStrips() }
        render(WebtoonDocumentZoom.IDENTITY)
        val atFit = renderRaster()

        // DEF-002 specifically: the old transform hardcoded `scaleY = 1f`, so a pinch could only
        // ever widen the strip. Asserting on *height* is what makes this a test of that claim
        // rather than of scaling in general — a transform with `scaleY = 1f` still satisfies any
        // assertion about width alone. Falsification-verified: an identity scale turns this red.
        render(WebtoonDocumentZoom(scale = 2f, offsetX = 0f))
        val zoomed = renderRaster()

        assertTrue(
            "A 2x document transform must scale the vertical axis too; height went " +
                "${atFit.height} -> ${zoomed.height}. A height that does not grow is exactly the " +
                "DEF-002 defect, where scaleY was pinned to 1f and a pinch only widened content.",
            zoomed.height > atFit.height * 3 / 2,
        )
    }

    @Test
    fun sliceBoundariesMoveWithTheDocumentTransform() {
        // `DEF-003`: the transform used to live on each `LazyColumn` item, so a scaled item painted
        // outside the slot the list still measured and neighbours collided. The distinguishing
        // measurement is *where the boundaries between slices land*: with one transform on the
        // container, every boundary is in document space and scales with it; with a per-item
        // transform, paint and layout disagree and the boundaries land somewhere else.
        //
        // Asserting only that "the image changed" cannot see this — a per-item transform changes the
        // image perfectly well. Asserting the count is unchanged is necessary but not sufficient,
        // because overlapping slices also preserve the count. The displacement ratio is the claim.
        composeRule.setContent { TintedStrips(stripHeightDp = STRIP_HEIGHT_DP) }
        render(WebtoonDocumentZoom.IDENTITY)
        val fitBitmap = composeRule.onNodeWithTag(STRIPS).captureToImage().asAndroidBitmap()
        val atFit = boundaries(fitBitmap)

        render(WebtoonDocumentZoom(scale = 2f, offsetX = 0f))
        val zoomBitmap = composeRule.onNodeWithTag(STRIPS).captureToImage().asAndroidBitmap()
        val zoomed = boundaries(zoomBitmap)

        assertTrue(
            "No slice boundary was visible at fit, so the measurement has nothing to track. " +
                "Strip height $STRIP_HEIGHT_DP produced a single flat column. " +
                "fit bitmap ${fitBitmap.width}x${fitBitmap.height}, boundaries $atFit.",
            atFit.isNotEmpty(),
        )
        assertTrue(
            "No slice boundary was visible at 2x. 2x bitmap ${zoomBitmap.width}x${zoomBitmap.height}, " +
                "boundaries $zoomed (fit bitmap ${fitBitmap.width}x${fitBitmap.height}, $atFit).",
            zoomed.isNotEmpty(),
        )

        val firstAtFit = atFit.first().toDouble() / fitBitmap.height
        val firstZoomed = zoomed.first().toDouble() / zoomBitmap.height

        // Compare *fractions of the raster*, not absolute pixels.
        //
        // `captureToImage` returns a bitmap sized to the node's rendered bounds, so a 2x layer
        // yields a bitmap twice as tall and every boundary inside it sits at twice the pixel
        // offset. Measuring raw pixels across two bitmaps of different sizes would compare 525
        // against 2100 and report a 4x ratio for a 2x transform -- a false `DEF-003`. The
        // measurement has to be scale-invariant, and the ratio of boundary position to raster
        // height is what carries the meaning.
        val expected = firstAtFit * 2
        val tolerance = 0.02

        assertTrue(
            "A slice boundary must move with the document transform. Normalised first boundary: " +
                "fit=$firstAtFit, 2x=$firstZoomed, expected about $expected (+/- $tolerance). " +
                "A boundary that does not scale with the document is the DEF-003 defect, where " +
                "each item was transformed independently of the slot the list measured for it. " +
                "fit raster ${fitBitmap.width}x${fitBitmap.height} boundaries $atFit; " +
                "2x raster ${zoomBitmap.width}x${zoomBitmap.height} boundaries $zoomed.",
            kotlin.math.abs(firstZoomed - expected) <= tolerance,
        )
    }

    private companion object {
        const val STRIPS = "webtoon-strips"
        const val STRIP_COUNT = 4

        /**
         * A quarter of a typical phone viewport, so the first boundary is well inside the screen at
         * both 1x and 2x. Large enough that raster rounding is negligible next to the displacement a
         * per-item transform would produce.
         */
        const val STRIP_HEIGHT_DP = 200

        /**
         * Flat, mutually distinguishable colours. Adjacent pairs must differ in every channel so a
         * boundary is never ambiguous: RED/GREEN, GREEN/BLUE, BLUE/YELLOW, YELLOW/RED.
         */
        val TINT = listOf(
            Color.Red,
            Color.Green,
            Color.Blue,
            Color.Yellow,
        )

        /**
         * y positions down the centre column at which the colour changes.
         *
         * Each entry is the first row of a new slice, so `[0]` is the first boundary after the
         * top edge. Empty when the whole column is one colour, which is itself a failure signal for
         * a test about boundaries.
         */
        fun boundaries(bitmap: android.graphics.Bitmap): List<Int> {
            val x = bitmap.width / 2
            val found = mutableListOf<Int>()
            var previous = bitmap.getPixel(x, 0)
            for (y in 1 until bitmap.height) {
                val current = bitmap.getPixel(x, y)
                if (current != previous) {
                    found.add(y)
                    previous = current
                }
            }
            return found
        }
    }
}
