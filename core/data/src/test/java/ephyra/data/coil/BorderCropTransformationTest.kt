package ephyra.data.coil

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class BorderCropTransformationTest {

    @Test
    fun `uniform border is trimmed conservatively`() {
        val bitmap = Bitmap.createBitmap(100, 80, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        for (y in 4 until 76) {
            for (x in 4 until 96) bitmap.setPixel(x, y, Color.rgb(64, 64, 64))
        }

        assertArrayEquals(
            intArrayOf(4, 4, 96, 76),
            BorderCropTransformation.findUniformBorderBounds(bitmap),
        )
    }

    @Test
    fun `artwork with mismatched corners is not cropped`() {
        val bitmap = Bitmap.createBitmap(100, 80, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        bitmap.setPixel(99, 79, Color.BLACK)

        assertNull(BorderCropTransformation.findUniformBorderBounds(bitmap))
    }

    @Test
    fun `a single artifact on one border still crops the page`() {
        val bitmap = uniformBorderPage()
        // One compression artifact inside the left border column (x = 0..3). It is 4x the colour
        // tolerance, so a genuine outlier rather than a near-match.
        bitmap.setPixel(2, 40, Color.rgb(240, 240, 240))

        assertArrayEquals(
            intArrayOf(4, 4, 96, 76),
            BorderCropTransformation.findUniformBorderBounds(bitmap),
        )
    }

    @Test
    fun `asymmetric borders are measured per edge`() {
        // A 3px left/right border and an 8px top/bottom border on a non-square page. Real pages
        // have borders of differing thickness per edge, and the x and y insets must be measured
        // independently rather than transposed.
        val bitmap = Bitmap.createBitmap(100, 80, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        for (y in 8 until 72) {
            for (x in 3 until 97) bitmap.setPixel(x, y, Color.rgb(64, 64, 64))
        }

        assertArrayEquals(
            intArrayOf(3, 8, 97, 72),
            BorderCropTransformation.findUniformBorderBounds(bitmap),
        )
    }

    @Test
    fun `an edge that is genuinely artwork is not treated as a border`() {
        val bitmap = Bitmap.createBitmap(100, 80, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        // Real content running the full height of the left border region.
        for (y in 0 until 80) bitmap.setPixel(1, y, Color.rgb(20, 20, 20))

        assertNull(BorderCropTransformation.findUniformBorderBounds(bitmap))
    }

    @Test
    fun `a uniform single colour image is never cropped`() {
        // Nothing to trim: every pixel already matches the border colour, so an unbounded
        // "uniform run" would happily eat into the middle of the image for no visual gain.
        val bitmap = Bitmap.createBitmap(100, 80, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)

        assertNull(BorderCropTransformation.findUniformBorderBounds(bitmap))
    }

    private fun uniformBorderPage(): Bitmap {
        val bitmap = Bitmap.createBitmap(100, 80, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        for (y in 4 until 76) {
            for (x in 4 until 96) bitmap.setPixel(x, y, Color.rgb(64, 64, 64))
        }
        return bitmap
    }
}
