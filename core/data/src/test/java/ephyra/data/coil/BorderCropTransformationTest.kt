package ephyra.data.coil

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.size.Size
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

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

    @Test
    fun `crop still works through real jpeg compression artifacts`() = runBlocking {
        // The synthetic bitmaps above are noise-free, so they do not represent a real page. A real
        // JPEG introduces ringing and blocking near the high-contrast border, which is exactly the
        // case the strict all-pixels-match rule rejected. This encodes to JPEG and decodes back so
        // the transform runs against genuinely compressed bytes.
        val source = Bitmap.createBitmap(160, 240, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(source)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { color = Color.rgb(70, 70, 70) }
        canvas.drawRect(16f, 16f, 144f, 224f, paint)
        // Checkerboard inside the artwork so compression has real high-frequency detail to ring on.
        for (y in 16 until 224 step 8) {
            for (x in 16 until 144 step 8) {
                if (((x + y) / 8) % 2 ==
                    0
                ) {
                    canvas.drawRect(x.toFloat(), y.toFloat(), (x + 8).toFloat(), (y + 8).toFloat(), paint)
                }
            }
        }

        val output = ByteArrayOutputStream()
        assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 85, output))
        val decoded = requireNotNull(
            BitmapFactory.decodeByteArray(output.toByteArray(), 0, output.size()),
        ) { "JPEG round-trip failed to decode" }

        val transformed = BorderCropTransformation().transform(decoded, Size.ORIGINAL)

        assertNotSame(decoded, transformed)
        assertTrue(
            "crop removed nothing: ${transformed.width}x${transformed.height} from ${decoded.width}x${decoded.height}",
            transformed.width < decoded.width && transformed.height < decoded.height,
        )
        // The crop must land close to the real 16px border, not nibble a pixel or swallow artwork.
        assertTrue(
            "unexpected cropped width ${transformed.width}, expected near ${decoded.width - 32}",
            transformed.width in 110..128,
        )
    }

    @Test
    fun `a real jpeg with no border is left alone`() = runBlocking {
        // Artwork running to every edge: the compression artifacts here must not be mistaken for
        // a border, which would trim real content.
        val source = Bitmap.createBitmap(120, 180, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(source)
        canvas.drawColor(Color.rgb(200, 40, 40))
        val paint = Paint().apply { color = Color.rgb(20, 40, 180) }
        canvas.drawRect(0f, 0f, 60f, 180f, paint)

        val output = ByteArrayOutputStream()
        assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 85, output))
        val decoded = requireNotNull(BitmapFactory.decodeByteArray(output.toByteArray(), 0, output.size())) {
            "JPEG round-trip failed to decode"
        }

        assertSame(decoded, BorderCropTransformation().transform(decoded, Size.ORIGINAL))
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
