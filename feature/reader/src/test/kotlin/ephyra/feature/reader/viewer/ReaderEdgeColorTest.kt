package ephyra.feature.reader.viewer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifies the arithmetic behind the "automatic" reader background.
 *
 * Only the [IntArray]-based core is exercised: it is the part that decides which pixels of
 * a page count and how they are averaged, and it is deliberately expressed without any
 * Android dependency so it can be checked on a plain JVM.
 */
class ReaderEdgeColorTest {

    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()

    private fun solid(width: Int, height: Int, color: Int) = IntArray(width * height) { color }

    @Test
    fun `uniform page averages to its own colour`() {
        val pixels = solid(width = 5, height = 5, color = 0xFF336699.toInt())

        assertEquals(0xFF336699.toInt(), averageEdgeColor(pixels, 5, 5, band = 1))
    }

    @Test
    fun `interior pixels are excluded from the average`() {
        // A 5x5 page with band=1 has a 3x3 interior that must not influence the result.
        val width = 5
        val height = 5
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            if (x in 1..3 && y in 1..3) blue else red
        }

        assertEquals(red, averageEdgeColor(pixels, width, height, band = 1))
    }

    @Test
    fun `narrow border bands still pick up a gradient edge`() {
        // Left column white, right column black, interior mid-grey. Only the border counts,
        // so the average sits halfway between the two edge colours.
        val width = 4
        val height = 6
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            when {
                y == 0 || y == height - 1 -> 0xFF808080.toInt()
                x == 0 -> 0xFFFFFFFF.toInt()
                x == width - 1 -> 0xFF000000.toInt()
                else -> 0xFF808080.toInt()
            }
        }

        // Perimeter: top+bottom rows are 4 grey each (8 grey), plus the left column and right
        // column across the 4 middle rows. Averages to a mid-tone that is neither full white
        // nor full black.
        val result = averageEdgeColor(pixels, width, height, band = 1)!!
        val r = (result shr 16) and 0xFF
        val g = (result shr 8) and 0xFF
        val b = result and 0xFF

        assertEquals(r, g)
        assertEquals(g, b)
        assertTrue(r in 1 until 255)
    }

    @Test
    fun `result is always opaque even when source pixels are transparent`() {
        val pixels = solid(width = 5, height = 5, color = 0x00336699)

        val result = averageEdgeColor(pixels, 5, 5, band = 1)!!

        assertEquals(0xFF, (result ushr 24) and 0xFF)
        assertEquals(0xFF336699.toInt(), result)
    }

    @Test
    fun `thumbnail smaller than the band averages the whole buffer without double counting`() {
        // 2x2 with band=2 trips the degenerate branch: the border would otherwise overlap
        // itself and skew the average.
        val pixels = solid(width = 2, height = 2, color = 0xFF204060.toInt())

        assertEquals(0xFF204060.toInt(), averageEdgeColor(pixels, 2, 2, band = 2))
    }

    @Test
    fun `degenerate dimensions return null`() {
        assertNull(averageEdgeColor(IntArray(0), 0, 0, band = 1))
        assertNull(averageEdgeColor(IntArray(10), 5, -1, band = 1))
    }

    @Test
    fun `buffer smaller than the declared page returns null`() {
        assertNull(averageEdgeColor(IntArray(10), 5, 5, band = 1))
    }

    @Test
    fun `sample size stays one when the page already fits the target`() {
        assertEquals(1, sampleSizeFor(width = 256, height = 256, target = 256))
        assertEquals(1, sampleSizeFor(width = 100, height = 400, target = 256))
    }

    @Test
    fun `sample size halves until both dimensions are within target`() {
        // 2048 -> 1024 -> 512 -> 256 all remain at or above the 256 target, so the loop
        // keeps halving until 2048/16 = 128 would fall below it, landing on 8.
        assertEquals(8, sampleSizeFor(width = 2048, height = 2048, target = 256))
        // 1200 -> 600 -> 300 stay above target; 1200/8 = 150 would not.
        assertEquals(4, sampleSizeFor(width = 1200, height = 900, target = 256))
    }

    @Test
    fun `sample size falls back to one for non-positive input`() {
        assertEquals(1, sampleSizeFor(width = 0, height = 100, target = 256))
        assertEquals(1, sampleSizeFor(width = 100, height = 0, target = 256))
        assertEquals(1, sampleSizeFor(width = 100, height = 100, target = 0))
    }
}
