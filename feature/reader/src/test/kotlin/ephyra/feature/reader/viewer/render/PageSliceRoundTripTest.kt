package ephyra.feature.reader.viewer.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.domain.reader.media.AnimationKind
import ephyra.domain.reader.media.PageImageFormat
import ephyra.domain.reader.media.PageMetadata
import ephyra.domain.reader.media.PageRenderPath
import ephyra.domain.reader.media.PageSource
import ephyra.domain.reader.media.PixelRect
import ephyra.domain.reader.media.PixelSize
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/**
 * Round-trip proof for the slicing pipeline: encode a **coordinate-carrying** pattern into a PNG,
 * run it through the real plan and region decode, stitch the slices back together, and assert the
 * result is exactly one whole-image decode — same height, same pixels, **no doubling**.
 *
 * ### Why a coordinate-encoded pattern
 *
 * The failure this hunts is a scale or offset error in the slice stack: an overlapping slice, a
 * `weight`-based layout that ignores intrinsic size, or a display-height computation that counts a
 * band twice. Each produces a *plausible-looking* image, so a solid-colour fixture cannot see them.
 *
 * Instead every pixel encodes its own coordinates:
 *
 * ```
 * R =  sourceRow        and 0xFF
 * G = (sourceRow >> 8)  and 0xFF     // 16-bit row label: pages up to 65535 px tall
 * B =  sourceColumn     and 0xFF
 * ```
 *
 * A wrong row label is then directly readable, so the assertions can be exact rather than tolerant,
 * and a failure names *where* the stack went wrong instead of reporting "a blurry picture".
 *
 * ### Why PNG
 *
 * JPEG is lossy and would smear the row labels, forcing a fuzzy comparison that could hide a
 * one-row slip. PNG is lossless, so pixel-for-pixel equality is meaningful.
 *
 * ### What each assertion catches
 *
 * 1. **Partition exactness** — `sum(sliceHeight) == srcHeight`, `bottom(i) == top(i+1)`. Catches a
 *    slice that renders a band twice (overlap) or drops one (gap).
 * 2. **Stitched height equals one whole-image decode** — catches a 2x/4x scale error, the class of
 *    mistake that reads as "the strip is twice as long as it should be".
 * 3. **Pixel-for-pixel identity with a whole-image decode** — catches everything else, including a
 *    wrong sample size or a slice decoded from the wrong rectangle.
 * 4. **Coordinate readback** — localises an error to a specific source row.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class PageSliceRoundTripTest {

    /** A texture ceiling low enough to force slicing on the fixtures below. */
    private val textureLimit = 4096

    /** A lossless PNG whose pixels encode their own coordinates (see the class KDoc). */
    private fun coordinateEncodedPng(width: Int, height: Int): ByteArray {
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val red = y and 0xFF
            val green = (y ushr 8) and 0xFF
            for (x in 0 until width) {
                pixels[y * width + x] = Color.argb(255, red, green, x and 0xFF)
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        val output = ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
            "unable to encode the coordinate fixture ${width}x$height"
        }
        return output.toByteArray()
    }

    /** The real decision path — the same call the webtoon and paged hosts make. */
    private fun plan(
        bytes: ByteArray,
        srcWidth: Int,
        srcHeight: Int,
        targetWidthPx: Int,
    ): WebtoonSlicePlan = WebtoonSlicePlanner.plan(
        metadata = PageMetadata(
            intrinsicSize = PixelSize(srcWidth, srcHeight),
            animation = AnimationKind.STATIC,
            format = PageImageFormat.PNG,
            contentRect = null,
        ),
        source = PageSource.InMemory(bytes.size),
        renderPath = PageRenderPath.SLICED,
        targetWidthPx = targetWidthPx,
        viewportHeightPx = null,
        maxTextureSize = textureLimit,
    )

    /** One `BitmapFactory` decode of the whole page at [sampleSize] — the ground truth. */
    private fun decodeWhole(bytes: ByteArray, sampleSize: Int): Bitmap {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)) {
            "the fixture PNG did not decode"
        }
    }

    /** Draws [slices] top-to-bottom into one bitmap, as the slice `Column` lays them out. */
    private fun stitch(slices: List<Bitmap>): Bitmap {
        val width = slices.first().width
        val height = slices.sumOf { it.height }
        val stitched = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(stitched)
        var top = 0
        slices.forEach { slice ->
            canvas.drawBitmap(slice, 0f, top.toFloat(), null)
            top += slice.height
        }
        return stitched
    }

    /** Decodes [slices] through the real region decoder, failing loudly if it is unsupported. */
    private fun decodeSlices(
        bytes: ByteArray,
        srcWidth: Int,
        srcHeight: Int,
        rects: List<WebtoonSlicer.SliceRect>,
        sampleSize: ephyra.domain.reader.media.SampleSize,
    ): List<Bitmap> {
        val decoded = runBlocking {
            WebtoonRegionDecoder.decodeSlices(
                bytes = bytes,
                sliceRect = PixelRect(0, 0, srcWidth, srcHeight),
                slices = rects,
                sampleSize = sampleSize,
            )
        }
        assertNotNull("region decoding must be supported for the fixture PNG", decoded)
        val bitmaps = decoded!!
        assertTrue("no slice may fail to decode: $bitmaps", bitmaps.none { it == null })
        return bitmaps.filterNotNull()
    }

    /**
     * The core gate. For each fixture: slice it, decode it, stitch it, and prove the result is a
     * single whole-image decode rather than a multiple of one.
     */
    @Test
    fun `sliced decode round-trips to exactly one whole-image decode`() {
        // (srcWidth, srcHeight, targetWidthPx). The third case is the interesting one: a 2160-wide
        // source shown at 1080 forces sample size TWO, which is where a doubling bug would live.
        val cases = listOf(
            Triple(64, 9_000, 64),
            Triple(216, 12_000, 1_080),
            Triple(2_160, 12_000, 1_080),
        )

        cases.forEach { (srcWidth, srcHeight, targetWidthPx) ->
            val bytes = coordinateEncodedPng(srcWidth, srcHeight)
            val plan = plan(bytes, srcWidth, srcHeight, targetWidthPx)
            val rects = plan.slices

            assertNotNull(
                "expected a sliced plan for ${srcWidth}x$srcHeight shown at $targetWidthPx",
                rects,
            )
            val slices = rects!!
            assertTrue("expected more than one slice for ${srcWidth}x$srcHeight", slices.size > 1)

            // (1) The partition is exact: every source row is covered exactly once. If a slice
            //     overlapped its neighbour this sum would exceed srcHeight -- the arithmetic form
            //     of "the strip got longer than the page".
            assertEquals(
                "slices must partition the page, not overlap or gap it",
                srcHeight,
                slices.sumOf { it.srcHeight },
            )
            assertEquals("the first slice must start at row 0", 0, slices.first().top)
            assertEquals(
                "the last slice must end at the page bottom",
                srcHeight,
                slices.last().bottom,
            )
            slices.zipWithNext().forEach { (left, right) ->
                assertEquals(
                    "slice ${left.index} and ${right.index} must abut exactly",
                    left.bottom,
                    right.top,
                )
            }

            // (2) Decode through the real region decoder, then stitch.
            val bitmaps = decodeSlices(bytes, srcWidth, srcHeight, slices, plan.sampleSize)
            val stitched = stitch(bitmaps)
            val whole = decodeWhole(bytes, plan.sampleSize.inSampleSize)

            // (3) One whole-image decode tall, not two or four.
            assertEquals(
                "stitched width must match the whole-image decode width",
                whole.width,
                stitched.width,
            )
            assertEquals(
                "stitched height must equal ONE whole-image decode -- a multiple here is the " +
                    "'doubled strip' defect",
                whole.height,
                stitched.height,
            )

            // (4) Pixel-for-pixel identity.
            assertPicturesEqual(whole, stitched, "${srcWidth}x$srcHeight at $targetWidthPx")

            whole.recycle()
            stitched.recycle()
            bitmaps.forEach { it.recycle() }
        }
    }

    /**
     * The anti-doubling test in its strongest form: read the coordinate label back out of the
     * stitched strip and check it names the source row that row actually came from.
     *
     * A slice decoded from the wrong rect, or a stack that repeated a band, yields a label that is
     * off by that band's height — so a failure reports the size and direction of the error.
     */
    @Test
    fun `every stitched row decodes from its own source row`() {
        val srcWidth = 108
        val srcHeight = 9_000
        val bytes = coordinateEncodedPng(srcWidth, srcHeight)
        val plan = plan(bytes, srcWidth, srcHeight, srcWidth)
        val slices = requireNotNull(plan.slices) { "expected slices" }
        val bitmaps = decodeSlices(bytes, srcWidth, srcHeight, slices, plan.sampleSize)

        val sample = plan.sampleSize.inSampleSize
        var sourceRow = 0
        bitmaps.forEach { bitmap ->
            repeat(bitmap.height) { rowInSlice ->
                val pixel = bitmap.getPixel(0, rowInSlice)
                val label = (Color.red(pixel) or (Color.green(pixel) shl 8)) and 0xFFFF
                val expected = sourceRow + rowInSlice * sample
                assertTrue(
                    "stitched row ${sourceRow + rowInSlice} decodes source row $label, expected " +
                        "~$expected (sample $sample) -- the slice stack is offset or doubled",
                    kotlin.math.abs(label - expected) <= sample,
                )
            }
            sourceRow += bitmap.height * sample
        }
        assertEquals("the walk must account for every source row", srcHeight, sourceRow)

        bitmaps.forEach { it.recycle() }
    }

    /** Exact comparison — meaningful only because the fixture is lossless PNG. */
    private fun assertPicturesEqual(expected: Bitmap, actual: Bitmap, context: String) {
        assertEquals("$context: width", expected.width, actual.width)
        assertEquals("$context: height", expected.height, actual.height)
        var y = 0
        while (y < expected.height) {
            var x = 0
            while (x < expected.width) {
                val a = expected.getPixel(x, y)
                val b = actual.getPixel(x, y)
                if (a != b) {
                    val expectedRow = (Color.red(a) or (Color.green(a) shl 8)) and 0xFFFF
                    val actualRow = (Color.red(b) or (Color.green(b) shl 8)) and 0xFFFF
                    throw AssertionError(
                        "$context: pixel ($x,$y) differs -- expected ARGB $a (source row " +
                            "$expectedRow) but got ARGB $b (source row $actualRow)",
                    )
                }
                x++
            }
            y++
        }
    }
}
