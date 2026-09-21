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
        // Adjacent slices overlap (no hairline seams).
        slices.zipWithNext().forEach { (a, b) -> assertTrue(b.top < a.bottom) }
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
}
