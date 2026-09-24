package ephyra.feature.reader.viewer.zoom

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Locks the policy that fixed the "pinch zoom does nothing but locks input" bug:
 * interaction must only be hijacked above a visibly-zoomed scale, so a sub-visual
 * pinch can never trap the reader until an "unpinch" releases it.
 */
class ZoomPolicyTest {

    @Test
    fun `fit is one`() {
        assertEquals(1f, ZoomPolicy.FIT)
    }

    @Test
    fun `interaction lock is above the old invisible 1_05 gate`() {
        assertTrue(ZoomPolicy.INTERACTION_LOCK > 1.05f)
    }

    @Test
    fun `locksInteraction is false in the invisible sub-zoom band`() {
        // The exact band that caused lock-in: clipped, sub-visual zoom that
        // looked like no zoom at all.
        assertFalse(ZoomPolicy.locksInteraction(1f))
        assertFalse(ZoomPolicy.locksInteraction(1.05f))
        assertFalse(ZoomPolicy.locksInteraction(1.09f))
        assertFalse(ZoomPolicy.locksInteraction(ZoomPolicy.INTERACTION_LOCK))
    }

    @Test
    fun `locksInteraction is true only for visibly zoomed scales`() {
        assertTrue(ZoomPolicy.locksInteraction(1.11f))
        assertTrue(ZoomPolicy.locksInteraction(2f))
        assertTrue(ZoomPolicy.locksInteraction(ZoomPolicy.INTERACTION_LOCK + 0.001f))
    }
}
