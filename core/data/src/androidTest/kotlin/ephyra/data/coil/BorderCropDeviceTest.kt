package ephyra.data.coil

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.size.Size
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `E3` proof for `DEF-008`, the border-crop fix.
 *
 * `BorderCropTransformation` trims a uniform scanner border from a page before display. The reported
 * defects were that it measured insets transposed, aborted on a single compression artifact, and
 * cropped contentless images.
 *
 * The `E2` suite already covers the decision logic, including a real JPEG encode/decode round-trip.
 * What it cannot cover is **the decoded pixels on the device's own decoder**. This suite asserts the
 * output bitmap directly: a page with a known uniform border must come back with that border gone
 * and the artwork intact, and a page that is entirely content must come back byte-identical.
 *
 * Both cases matter and they pull in opposite directions. A cropper that always returns its input
 * passes "not cropped" and fails the first; one that always crops fails the second. Together they
 * are what makes the assertion a test of the transformation rather than of the fixture.
 *
 * The border is drawn in a colour that JPEG would smear into its surroundings, and the artwork
 * carries structure, so a crop that ate into the image is visible as a dimension change rather than
 * having to be inferred.
 */
@RunWith(AndroidJUnit4::class)
class BorderCropDeviceTest {

    private val transformation = BorderCropTransformation()

    private fun borderedPage(
        width: Int,
        height: Int,
        border: Int,
        borderColor: Int = Color.rgb(250, 250, 248),
        artworkColor: Int = Color.rgb(20, 60, 140),
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // Border first, then an inset of artwork with its own internal structure, so an
        // over-crop is measurable rather than merely "a different number".
        for (y in 0 until height) {
            for (x in 0 until width) {
                val onBorder = x < border || y < border || x >= width - border || y >= height - border
                val striped = ((x / 8) + (y / 8)) % 2 == 0
                val colour = when {
                    onBorder -> borderColor
                    striped -> artworkColor
                    else -> Color.rgb(200, 40, 40)
                }
                bitmap.setPixel(x, y, colour)
            }
        }
        return bitmap
    }

    @Test
    fun uniformBorderIsRemovedFromADecodedPage() = runBlocking {
        val border = 24
        val input = borderedPage(width = 400, height = 560, border = border)

        val output = transformation.transform(input, Size.ORIGINAL)

        assertTrue(
            "A page with a ${border}px uniform border must be cropped; the output was " +
                "${output.width}x${output.height}. An unchanged size means the border was not " +
                "detected, which is the DEF-008 defect.",
            output.width < input.width || output.height < input.height,
        )
        assertEquals(
            "Cropping must be symmetric on both axes, so the output must be square-ish: input " +
                "400x560 minus a 24px border on each edge is 352x512, got " +
                "${output.width}x${output.height}.",
            input.width - 2 * border,
            output.width,
        )
        assertEquals(input.height - 2 * border, output.height)

        // The top-left pixel of the output must be artwork, not the border that used to be there.
        val corner = output.getPixel(0, 0)
        assertTrue(
            "The cropped corner must be artwork, but the pixel was ${Integer.toHexString(corner)}. " +
                "A border-coloured corner means the crop stopped short.",
            corner != Color.rgb(250, 250, 248),
        )
    }

    @Test
    fun aPageWithNoBorderIsLeftUntouched() = runBlocking {
        // Full-bleed artwork: no uniform margin anywhere, so there is nothing to trim.
        val input = Bitmap.createBitmap(320, 480, Bitmap.Config.ARGB_8888)
        for (y in 0 until 480) {
            for (x in 0 until 320) {
                val striped = ((x / 8) + (y / 8)) % 2 == 0
                input.setPixel(x, y, if (striped) Color.rgb(30, 90, 160) else Color.rgb(240, 220, 60))
            }
        }

        val output = transformation.transform(input, Size.ORIGINAL)

        assertEquals(
            "A page with no uniform border must not be cropped at all; got " +
                "${output.width}x${output.height} from 320x480. Cropping contentless or fully " +
                "artwork images is one of the reported DEF-008 symptoms.",
            320,
            output.width,
        )
        assertEquals(480, output.height)
    }

    @Test
    fun asymmetricBordersAreMeasuredPerEdgeOnDevice() = runBlocking {
        // Deliberately different insets on every edge. `DEF-008` was that insets were measured
        // *transposed*, and a symmetric fixture cannot detect transposition at all: swapping
        // left/top and right/bottom on a uniform border yields the same numbers. This is the case
        // that actually pins the defect down, and it is why the fixture here is lopsided.
        val left = 12
        val top = 40
        val right = 28
        val bottom = 8
        val width = 400
        val height = 560
        val input = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val borderColor = Color.rgb(250, 250, 248)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val onBorder = x < left || y < top || x >= width - right || y >= height - bottom
                val striped = ((x / 8) + (y / 8)) % 2 == 0
                input.setPixel(
                    x,
                    y,
                    if (onBorder) {
                        borderColor
                    } else if (striped) {
                        Color.rgb(20, 60, 140)
                    } else {
                        Color.rgb(200, 40, 40)
                    },
                )
            }
        }

        val output = transformation.transform(input, Size.ORIGINAL)

        assertEquals(
            "With asymmetric insets (l=$left t=$top r=$right b=$bottom) the crop must be measured " +
                "per edge. Width should be ${width - left - right} but was ${output.width}. A width " +
                "matching the vertical insets instead means the measurements were transposed, which " +
                "is the DEF-008 defect.",
            width - left - right,
            output.width,
        )
        assertEquals(
            "Height should be ${height - top - bottom} but was ${output.height}; a height built " +
                "from the horizontal insets means the measurements were transposed.",
            height - top - bottom,
            output.height,
        )
    }

    @Test
    fun aUniformSingleColourImageIsNeverCropped() = runBlocking {
        // A blank page has no content to preserve, so any crop is pure loss.
        val input = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.rgb(255, 255, 255))

        val output = transformation.transform(input, Size.ORIGINAL)

        assertEquals(
            "A uniform single-colour image must be left alone; got ${output.width}x${output.height} " +
                "from 200x300.",
            200,
            output.width,
        )
        assertEquals(300, output.height)
    }
}
