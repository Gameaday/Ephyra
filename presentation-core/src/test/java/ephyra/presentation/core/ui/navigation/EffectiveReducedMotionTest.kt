package ephyra.presentation.core.ui.navigation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The merge rule between the OS animator scale and the in-app "Reduce motion" preference.
 *
 * Asserted directly because the two sources are easy to combine wrongly in a way that still looks
 * right: making the app preference *override* the OS setting would let a user who has disabled
 * animation system-wide re-enable it inside Ephyra, which is the opposite of an accessibility
 * guarantee. The OS setting is a floor, so the merge is an `or`.
 */
class EffectiveReducedMotionTest {

    @Test
    fun `either source alone reduces motion`() {
        assertTrue(effectiveReducedMotion(systemReducedMotion = true, appReduceMotion = false))
        assertTrue(effectiveReducedMotion(systemReducedMotion = false, appReduceMotion = true))
        assertTrue(effectiveReducedMotion(systemReducedMotion = true, appReduceMotion = true))
    }

    @Test
    fun `motion is full only when neither source asks to reduce it`() {
        assertFalse(effectiveReducedMotion(systemReducedMotion = false, appReduceMotion = false))
    }

    @Test
    fun `the app preference can never cancel the system setting`() {
        // The failure this guards: treating the preference as authoritative and letting
        // `appReduceMotion = false` override `systemReducedMotion = true`.
        assertTrue(effectiveReducedMotion(systemReducedMotion = true, appReduceMotion = false))
    }
}
