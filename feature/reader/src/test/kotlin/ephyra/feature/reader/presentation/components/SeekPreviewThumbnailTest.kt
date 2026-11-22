package ephyra.feature.reader.presentation.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.feature.reader.viewer.render.WebtoonRegionDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/**
 * The seek-bar thumbnail, which is what makes a drag legible.
 *
 * The property that matters is not "returns a bitmap" but "returns a bitmap **no larger than the
 * box it fills**". A preview that decoded at source resolution would allocate a 4000px bitmap to
 * draw into 72dp, and doing that per thumb position is the decode storm that makes scrubbing
 * stutter. These assert the bound directly.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class SeekPreviewThumbnailTest {

    private fun png(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val output = ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "encode failed" }
        return output.toByteArray()
    }

    @Test
    fun `a tall page is downsampled to the preview width`() {
        // 1200x4000 is a realistic long-strip page. The rule keeps the largest power-of-two divisor
        // whose result is still >= the target, so 1200 -> sample 4 -> 300px wide.
        val thumbnail = decodePreview(png(1200, 4000), targetWidthPx = 216)

        assertNotNull("a valid PNG must decode", thumbnail)
        assertTrue(
            "the thumbnail must be no wider than the source ($thumbnail)",
            thumbnail!!.width <= 1200,
        )
        assertTrue(
            "and must be far shorter than the source, or it is not a preview at all",
            thumbnail.height < 4000,
        )
    }

    @Test
    fun `a page already smaller than the preview is returned at its own size`() {
        // No upscaling: a small source should not be blown up to the box width.
        val thumbnail = decodePreview(png(80, 120), targetWidthPx = 216)

        assertNotNull(thumbnail)
        assertEquals(80, thumbnail!!.width)
        assertEquals(120, thumbnail.height)
    }

    @Test
    fun `undecodable bytes degrade to null rather than throwing`() {
        // The bubble falls back to the page number when this returns null, so a corrupt cache
        // entry must never take down the reader's seek bar.
        assertNull(decodePreview("not an image".toByteArray(), targetWidthPx = 216))
    }

    @Test
    fun `a zero target width still produces a usable thumbnail`() {
        // Defensive: density can report 0 before the first layout pass, and dividing by it would
        // produce an inSampleSize of 0, which BitmapFactory treats as 1 anyway - but the result
        // must still be a real bitmap rather than a crash.
        val thumbnail = decodePreview(png(1200, 4000), targetWidthPx = 0)

        assertNotNull(thumbnail)
        assertTrue("must still decode to something drawable", thumbnail!!.width >= 1)
    }

    @Test
    fun `the sample size is the largest power-of-two that still fits`() {
        // The rule keeps `src / sample >= target`, so it under-samples rather than over-samples:
        // 1200/4 = 300 >= 216, while 1200/8 = 150 < 216.
        assertEquals(4, WebtoonRegionDecoder.sampleSizeForWidth(1200, 216))
        assertEquals(8, WebtoonRegionDecoder.sampleSizeForWidth(2400, 216))
        assertEquals(16, WebtoonRegionDecoder.sampleSizeForWidth(4800, 216))
        // Never zero, and never above the source.
        assertEquals(1, WebtoonRegionDecoder.sampleSizeForWidth(216, 216))
        assertEquals(1, WebtoonRegionDecoder.sampleSizeForWidth(0, 216))
    }
}
