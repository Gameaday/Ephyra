package ephyra.feature.reader.viewer

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChapterPositionTrackerTest {

    @Test
    fun `claims once per chapter`() {
        val tracker = ChapterPositionTracker()
        assertTrue(tracker.claimPosition(1L))
        assertFalse(tracker.claimPosition(1L))
        assertFalse(tracker.claimPosition(1L))
    }

    @Test
    fun `chapter change resets and allows repositioning`() {
        val tracker = ChapterPositionTracker()
        assertTrue(tracker.claimPosition(1L))
        assertFalse(tracker.claimPosition(1L))
        assertTrue(tracker.claimPosition(2L))
        assertFalse(tracker.claimPosition(2L))
        // Revisit of an earlier chapter must re-position too.
        assertTrue(tracker.claimPosition(1L))
    }

    @Test
    fun `not-ready claim does not consume the one-shot claim`() {
        val tracker = ChapterPositionTracker()
        // Pages unresolved: reset happens but no claim is consumed.
        assertFalse(tracker.claimPosition(1L, canPosition = false))
        assertFalse(tracker.claimPosition(1L, canPosition = false))
        // Once ready, the chapter still positions exactly once.
        assertTrue(tracker.claimPosition(1L, canPosition = true))
        assertFalse(tracker.claimPosition(1L, canPosition = true))
    }

    @Test
    fun `not-ready state on a new chapter still resets the marker`() {
        val tracker = ChapterPositionTracker()
        assertTrue(tracker.claimPosition(1L))
        // Switch to chapter 2 before its pages resolve: marker must drop so a later
        // revisit of chapter 1 (or chapter 2 becoming ready) can position.
        assertFalse(tracker.claimPosition(2L, canPosition = false))
        assertTrue(tracker.claimPosition(2L, canPosition = true))
    }
}
