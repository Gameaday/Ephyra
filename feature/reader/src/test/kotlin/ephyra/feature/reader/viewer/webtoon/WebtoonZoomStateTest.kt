package ephyra.feature.reader.viewer.webtoon

import ephyra.feature.reader.viewer.zoom.ZoomPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebtoonZoomStateTest {

    private fun state(min: Float = 0.5f, max: Float = 4f) = WebtoonZoomState(min = min, max = max)

    @Test
    fun `zoom state starts at fit`() {
        val state = state()
        assertEquals(1f, state.scale)
        assertEquals(0f, state.offsetX)
    }

    @Test
    fun `zoom clamps and recenters on dock`() {
        val state = state()
        state.setViewportWidth(100f)
        state.applyZoom(9f, 12f, 50f, 500f)
        assertEquals(4f, state.scale)
        state.applyZoom(1f, 0f, 50f, 500f)
        assertEquals(1f, state.scale)
        assertEquals(0f, state.offsetX)
    }

    @Test
    fun `toggle fit flips between 1x and 2x`() {
        val state = state()
        state.toggleFit()
        assertEquals(2f, state.scale)
        state.toggleFit()
        assertEquals(1f, state.scale)
        assertEquals(0f, state.offsetX)
    }

    @Test
    fun `toggle fit uses the interaction escape gate`() {
        val state = state()
        state.setViewportWidth(100f)
        state.applyZoom(ZoomPolicy.ZOOM_GATE + 0.001f, 0f, 50f, 500f)

        state.toggleFit()

        assertEquals(ZoomPolicy.FIT, state.scale)
        assertEquals(0f, state.offsetX)
    }

    @Test
    fun `zoom pan is bounded by viewport overflow`() {
        val state = state(min = 1f)
        state.setViewportWidth(100f)
        state.applyZoom(2f, 10_000f, 50f, 500f)
        assertEquals(50f, state.offsetX)
        state.applyZoom(1f, 0f, 50f, 500f)
        assertEquals(0f, state.offsetX)
    }

    @Test
    fun `an off centre focal produces a scroll correction`() {
        // The list is laid out in document units, so a focal zoom needs the list to move or the
        // strip jumps under the fingers even though the scale is right.
        val state = state(min = 1f)
        state.setViewportWidth(1000f)
        val correction = state.applyZoom(2f, 0f, 250f, 500f).scrollCorrection
        assertTrue(
            correction != 0f,
            "expected a focal correction for an off-centre zoom, was $correction",
        )
    }

    @Test
    fun `a focal at the top of the viewport needs no scroll correction`() {
        // The correction is vertical. A focal on the top edge is the one case that must not move
        // the list, regardless of where the pinch is horizontally.
        val state = state(min = 1f)
        state.setViewportWidth(1000f)
        assertEquals(0f, state.applyZoom(2f, 0f, 500f, 0f).scrollCorrection, 1e-3f)
    }

    @Test
    fun `a focal lower in the viewport moves the list`() {
        val state = state(min = 1f)
        state.setViewportWidth(1000f)
        val correction = state.applyZoom(2f, 0f, 500f, 800f).scrollCorrection
        assertTrue(correction > 0f, "expected a downward list move, was $correction")
    }

    @Test
    fun `a zero viewport falls back to a centred focal rather than dividing by zero`() {
        val state = state(min = 1f)
        state.applyZoom(2f, 0f, 300f, 500f)
        assertTrue(state.scale.isFinite() && state.scale > 0f)
    }

    @Test
    fun `layout helpers derive aspect and reject unknowns`() {
        assertEquals(0.5f, webtoonAspectRatio(800 to 1600))
        assertNull(webtoonAspectRatio(null))
        assertNull(webtoonAspectRatio(0 to 1600))
    }
}
