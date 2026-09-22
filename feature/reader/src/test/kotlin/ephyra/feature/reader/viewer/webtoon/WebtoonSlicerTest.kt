package ephyra.feature.reader.viewer.webtoon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebtoonSlicerTest {

    @Test
    fun `short page is not sliced`() {
        assertFalse(WebtoonSlicer.needsSlicing(800, 1500, 1080, 2048))
        val slices = WebtoonSlicer.computeSlices(800, 1500, 1080, 2048)
        assertEquals(1, slices.size)
        assertEquals(0, slices[0].top)
        assertEquals(1500, slices[0].bottom)
    }

    @Test
    fun `long strip splits under texture ceiling`() {
        // 800x12000 at 1080w => 16200 display px tall; ceiling 2048 => 8 slices.
        assertTrue(WebtoonSlicer.needsSlicing(800, 12000, 1080, 2048))
        val slices = WebtoonSlicer.computeSlices(800, 12000, 1080, 2048)
        assertEquals(8, slices.size)
        // Full coverage: first starts at 0, last ends at srcHeight.
        assertEquals(0, slices.first().top)
        assertEquals(12000, slices.last().bottom)
        // No slice exceeds the ceiling once scaled to display pixels.
        val scale = 1080.0 / 800
        slices.forEach { assertTrue(it.srcHeight * scale <= 2048 + 1e-6) }
        // Exact partition: no overlap, no gaps — each row rendered exactly once.
        slices.zipWithNext().forEach { (a, b) -> assertEquals(a.bottom, b.top) }
        assertEquals(12000, slices.sumOf { it.srcHeight })
    }

    @Test
    fun `uneven heights distribute remainder without slivers`() {
        // 800x10003 forces a remainder: exact tiling must still partition fully.
        val slices = WebtoonSlicer.computeSlices(800, 10003, 1080, 2048)
        assertTrue(slices.size >= 2)
        assertEquals(0, slices.first().top)
        assertEquals(10003, slices.last().bottom)
        slices.zipWithNext().forEach { (a, b) -> assertEquals(a.bottom, b.top) }
        assertEquals(10003, slices.sumOf { it.srcHeight })
        val scale = 1080.0 / 800
        slices.forEach { assertTrue(it.srcHeight * scale <= 2048 + 1e-6) }
    }

    @Test
    fun `viewport cap tightens slices`() {
        // Viewport 1000px * 1.5 = 1500 cap beats the 4096 texture limit.
        assertEquals(1500, WebtoonSlicer.maxSliceDisplayPx(4096, viewportHeightPx = 1000))
        // No viewport => texture limit wins.
        assertEquals(4096, WebtoonSlicer.maxSliceDisplayPx(4096, null))
    }

    @Test
    fun `oversized strip beyond max slices returns empty`() {
        // Forced tiny ceiling => hundreds of slices => caller must use single-image fallback.
        val slices = WebtoonSlicer.computeSlices(800, 40000, 1080, 256)
        assertTrue(slices.isEmpty())
    }

    @Test
    fun `invalid dimensions never slice`() {
        assertFalse(WebtoonSlicer.needsSlicing(0, 1000, 1080, 2048))
        assertFalse(WebtoonSlicer.needsSlicing(800, 0, 1080, 2048))
    }

    @Test
    fun `random sizes always partition exactly under the ceiling`() {
        val random = java.util.Random(42)
        repeat(100) {
            val srcWidth = 200 + random.nextInt(2000)
            val srcHeight = 500 + random.nextInt(20000)
            val targetWidth = 360 + random.nextInt(1440)
            val ceiling = 512 + random.nextInt(3584)
            val slices = WebtoonSlicer.computeSlices(srcWidth, srcHeight, targetWidth, ceiling)
            if (slices.isEmpty()) return@repeat // over MAX_SLICES: caller falls back.
            val effectiveCeiling = maxOf(ceiling, WebtoonSlicer.MIN_SLICE_DISPLAY_PX)
            val scale = targetWidth.toDouble() / srcWidth
            assertEquals(0, slices.first().top)
            assertEquals(srcHeight, slices.last().bottom)
            slices.zipWithNext().forEach { (a, b) -> assertEquals(a.bottom, b.top) }
            assertEquals(srcHeight, slices.sumOf { it.srcHeight })
            slices.forEach {
                assertTrue(it.srcHeight * scale <= effectiveCeiling + 1e-6)
                assertTrue(it.srcHeight > 0)
            }
        }
    }
}
