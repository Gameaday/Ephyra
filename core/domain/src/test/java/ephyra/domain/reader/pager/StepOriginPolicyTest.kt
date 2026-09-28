package ephyra.domain.reader.pager

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the recovery rule that stopped the pager drifting permanently out of sync with the visible
 * page.
 *
 * The defect: `pendingTargetIndex` was cleared only when `onPageSelected` settled on exactly that
 * index, or on a chapter change. A step that never landed left a value nothing would clear, and every
 * later step advanced from that phantom index until the chapter changed.
 */
class StepOriginPolicyTest {

    @Test
    fun `with no pending request the step moves from where the pager actually is`() {
        val result = StepOriginPolicy.resolve(settledIndex = 7, pendingIndex = null, itemCount = 20)
        assertEquals(7, result.origin)
        assertFalse(result.clearPending, "there was nothing pending to clear")
    }

    @Test
    fun `a pending request ahead of the pager is obeyed`() {
        // The user pressed next twice quickly and the pager has only settled once. The second
        // request is still in flight and must not be discarded.
        val result = StepOriginPolicy.resolve(settledIndex = 7, pendingIndex = 9, itemCount = 20)
        assertEquals(9, result.origin)
        assertFalse(result.clearPending, "an in-flight request must be kept")
    }

    @Test
    fun `a pending request the pager has already reached is discarded`() {
        // The drift case. The pager settled at 7 and a stale 5 remained from a request that was
        // superseded. Obeying 5 would move the user backwards onto a page they had already passed.
        val result = StepOriginPolicy.resolve(settledIndex = 7, pendingIndex = 5, itemCount = 20)
        assertEquals(7, result.origin)
        assertTrue(result.clearPending, "a superseded request must be dropped, not left to drift")
    }

    @Test
    fun `a pending request equal to the settled index is discarded`() {
        // The ordinary success path: the request landed, but `onPageSelected` compared against a
        // different value and left it behind. Treating it as still-pending would make the next step
        // a no-op.
        val result = StepOriginPolicy.resolve(settledIndex = 7, pendingIndex = 7, itemCount = 20)
        assertEquals(7, result.origin)
        assertTrue(result.clearPending)
    }

    @Test
    fun `a pending index outside the current items is discarded and clamped`() {
        // A chapter was replaced by a shorter one while a request was outstanding. Obeying index 40
        // in a 10-item pager would step past the end and trigger a spurious chapter transition.
        val result = StepOriginPolicy.resolve(settledIndex = 3, pendingIndex = 40, itemCount = 10)
        assertEquals(3, result.origin)
        assertTrue(result.clearPending)
    }

    @Test
    fun `an empty pager does not produce a negative origin`() {
        // `coerceIn(0, 0)` is the only safe answer here; returning -1 would make a caller compute
        // index 0 and then call into a pager with nothing in it.
        val result = StepOriginPolicy.resolve(settledIndex = 0, pendingIndex = null, itemCount = 0)
        assertEquals(0, result.origin)
    }

    @Test
    fun `repeated stepping cannot walk backwards`() {
        // The property the defect actually broke, exercised as a sequence rather than a single call:
        // whatever the pending value, the origin is never behind the settled index.
        val settled = 10
        val pendingCandidates = listOf(null, 0, 3, 9, 10, 11, 12, 40, -1)
        pendingCandidates.forEach { pending ->
            val origin = StepOriginPolicy.resolve(settled, pending, itemCount = 20).origin
            assertTrue(
                origin >= settled,
                "origin $origin fell behind settled $settled for pending=$pending; the pager would " +
                    "move the user backwards",
            )
        }
    }

    @Test
    fun `the origin always addresses a real item`() {
        val itemCounts = listOf(0, 1, 2, 5, 20)
        itemCounts.forEach { count ->
            val pendingCandidates = listOf(null, -5, 0, count, count + 10)
            pendingCandidates.forEach { pending ->
                val result = StepOriginPolicy.resolve(settledIndex = 0, pendingIndex = pending, itemCount = count)
                if (count > 0) {
                    assertTrue(
                        result.origin in 0 until count,
                        "origin ${result.origin} is not a valid index for count=$count pending=$pending",
                    )
                } else {
                    assertEquals(0, result.origin)
                }
            }
        }
    }
}
