package ephyra.domain.navigation.motion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MotionPolicyTest {

    private val key = "manga_cover_42"

    @Test
    fun `library to series uses the cover as its only shared element`() {
        val plan = MotionPolicy.plan(MotionRoutePair.LIBRARY_SERIES, key)
        assertEquals(MotionRoutePair.LIBRARY_SERIES, plan.pair)
        assertEquals(key, plan.sharedElementKey)
        assertTrue(plan.shouldAnimateSharedElement)
    }

    @Test
    fun `non cover content does not move when a shared element carries the transition`() {
        // The contract: "full-screen content does not independently scale or slide". A container
        // transform competing with the cover is what makes a return look wrong.
        val plan = MotionPolicy.plan(MotionRoutePair.LIBRARY_SERIES, key)
        assertEquals(ContainerMotion.NONE, plan.effectiveContainerMotion)
    }

    @Test
    fun `an element that never resolved falls back to a crossfade`() {
        // Animating a shared element that has no bounds produces a slide from nowhere.
        val plan = MotionPolicy.plan(MotionRoutePair.LIBRARY_SERIES, key, sharedElementFound = false)
        assertFalse(plan.shouldAnimateSharedElement)
        assertEquals(ContainerMotion.CROSSFADE, plan.effectiveContainerMotion)
    }

    @Test
    fun `a missing element key also falls back to a crossfade`() {
        val plan = MotionPolicy.plan(MotionRoutePair.LIBRARY_SERIES, sharedElementKey = null)
        assertFalse(plan.shouldAnimateSharedElement)
        assertEquals(ContainerMotion.CROSSFADE, plan.effectiveContainerMotion)
    }

    @Test
    fun `enter and exit share one duration`() {
        // Asymmetric in/out values make the two screens cross at different points, which reads as
        // a stutter or a flash rather than one continuous movement.
        val plan = MotionPolicy.plan(MotionRoutePair.LIBRARY_SERIES, key)
        assertEquals(MotionPolicy.SHARED_ELEMENT_DURATION_MILLIS, plan.durationMillis)
    }

    @Test
    fun `a crossfade fallback is shorter than a shared element transition`() {
        val shared = MotionPolicy.plan(MotionRoutePair.LIBRARY_SERIES, key)
        val fallback = MotionPolicy.plan(MotionRoutePair.LIBRARY_SERIES, key, sharedElementFound = false)
        assertTrue(fallback.durationMillis < shared.durationMillis)
    }

    @Test
    fun `reduced motion keeps the crossfade rather than cutting`() {
        // An instant cut loses the continuity that tells the user where they went.
        val plan = MotionPolicy.plan(MotionRoutePair.LIBRARY_SERIES, key, reducedMotion = true)
        assertTrue(plan.reducedMotion)
        assertEquals(ContainerMotion.CROSSFADE, plan.effectiveContainerMotion)
    }

    @Test
    fun `reduced motion reports an instant duration`() {
        val plan = MotionPolicy.plan(MotionRoutePair.LIBRARY_SERIES, key, reducedMotion = true)
        assertEquals(0, plan.effectiveDurationMillis)
    }

    @Test
    fun `reduced motion leaves motion intact when motion is not requested`() {
        val plan = MotionPolicy.plan(MotionRoutePair.LIBRARY_SERIES, key, reducedMotion = false)
        assertEquals(MotionPolicy.SHARED_ELEMENT_DURATION_MILLIS, plan.effectiveDurationMillis)
    }

    @Test
    fun `reduced motion still resolves the element so it is not drawn twice`() {
        // The element must still be found and matched; only the interpolation is dropped.
        val plan = MotionPolicy.plan(MotionRoutePair.LIBRARY_SERIES, key, reducedMotion = true)
        assertEquals(key, plan.sharedElementKey)
    }

    @Test
    fun `only the library pair may carry a shared element`() {
        listOf(
            MotionRoutePair.TAB_PEER,
            MotionRoutePair.READER_ENTRY,
            MotionRoutePair.SHEET_PARENT,
            MotionRoutePair.UNDECLARED,
        ).forEach { pair ->
            val plan = MotionPolicy.plan(pair, key)
            assertFalse(plan.shouldAnimateSharedElement, "pair=$pair must not animate a shared element")
        }
    }

    @Test
    fun `a tab peer crossfades`() {
        val plan = MotionPolicy.plan(MotionRoutePair.TAB_PEER)
        assertEquals(ContainerMotion.CROSSFADE, plan.effectiveContainerMotion)
    }

    @Test
    fun `a reader entry uses a shared axis`() {
        val plan = MotionPolicy.plan(MotionRoutePair.READER_ENTRY)
        assertEquals(ContainerMotion.SHARED_AXIS, plan.effectiveContainerMotion)
    }

    @Test
    fun `an undeclared pair falls back to a crossfade rather than nothing`() {
        val plan = MotionPolicy.plan(MotionRoutePair.UNDECLARED)
        assertEquals(ContainerMotion.CROSSFADE, plan.effectiveContainerMotion)
    }

    @Test
    fun `the forward and reverse plans are identical`() {
        // Predictive back reuses the same model, so it must not diverge from the push.
        val forward = MotionPolicy.plan(MotionRoutePair.LIBRARY_SERIES, key)
        val reverse = MotionPolicy.plan(MotionRoutePair.LIBRARY_SERIES, key)
        assertEquals(forward.durationMillis, reverse.durationMillis)
        assertEquals(forward.effectiveContainerMotion, reverse.effectiveContainerMotion)
        assertEquals(forward.shouldAnimateSharedElement, reverse.shouldAnimateSharedElement)
    }

    @Test
    fun `a non positive duration is rejected`() {
        assertThrows<IllegalArgumentException> {
            MotionPlan(
                pair = MotionRoutePair.LIBRARY_SERIES,
                sharedElementKey = null,
                containerMotion = ContainerMotion.CROSSFADE,
                durationMillis = 0,
                sharedElementUsable = false,
                reducedMotion = true,
            )
        }
    }

    @Test
    fun `an element key survives when the element did not resolve`() {
        // The key is kept so both screens still agree which element was meant, and so the element
        // is not drawn twice; only the interpolation is skipped.
        val plan = MotionPolicy.plan(MotionRoutePair.LIBRARY_SERIES, key, sharedElementFound = false)
        assertEquals(key, plan.sharedElementKey)
        assertFalse(plan.shouldAnimateSharedElement)
    }
}
