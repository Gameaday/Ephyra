package ephyra.feature.reader.viewer.webtoon

import ephyra.feature.reader.viewer.zoom.ZoomPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WebtoonZoomStateTest {

    @Test
    fun `zoom state starts at fit`() {
        val state = WebtoonZoomState(min = 0.5f, max = 4f)
        assertEquals(1f, state.scale)
        assertEquals(0f, state.offsetX)
    }

    @Test
    fun `zoom clamps and recenters on dock`() {
        val state = WebtoonZoomState(min = 0.5f, max = 4f)
        state.applyZoom(9f, 12f)
        assertEquals(4f, state.scale)
        state.applyZoom(1f, 0f)
        assertEquals(1f, state.scale)
        assertEquals(0f, state.offsetX)
    }

    @Test
    fun `toggle fit flips between 1x and 2x`() {
        val state = WebtoonZoomState(min = 0.5f, max = 4f)
        state.toggleFit()
        assertEquals(2f, state.scale)
        state.toggleFit()
        assertEquals(1f, state.scale)
        assertEquals(0f, state.offsetX)
    }

    @Test
    fun `toggle fit uses the interaction escape gate`() {
        val state = WebtoonZoomState(min = 0.5f, max = 4f)
        state.applyZoom(ZoomPolicy.ZOOM_GATE + 0.001f, 0f)

        state.toggleFit()

        assertEquals(ZoomPolicy.FIT, state.scale)
        assertEquals(0f, state.offsetX)
    }

    @Test
    fun `layout helpers derive aspect and reject unknowns`() {
        assertEquals(0.5f, webtoonAspectRatio(800 to 1600))
        assertNull(webtoonAspectRatio(null))
        assertNull(webtoonAspectRatio(0 to 1600))
    }
}
