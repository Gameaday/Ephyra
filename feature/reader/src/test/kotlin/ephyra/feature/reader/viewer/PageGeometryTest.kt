package ephyra.feature.reader.viewer

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The tall-page threshold that drives the paged reader's width-fill layout and cache key. */
class PageGeometryTest {

    @Test
    fun `an aspect below the threshold is tall`() {
        assertTrue(PageGeometry.isTall(0.4f))
        assertTrue(PageGeometry.isTall(0.1f))
        // Just under the 0.5 boundary is still tall.
        assertTrue(PageGeometry.isTall(0.49f))
    }

    @Test
    fun `an aspect at or above the threshold is not tall`() {
        assertFalse(PageGeometry.isTall(PageGeometry.TALL_ASPECT)) // exactly 0.5 -> not tall
        assertFalse(PageGeometry.isTall(0.6f))
        assertFalse(PageGeometry.isTall(1.0f)) // square
    }

    @Test
    fun `an unknown aspect is not tall`() {
        assertFalse(PageGeometry.isTall(null))
    }
}
