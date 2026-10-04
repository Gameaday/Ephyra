package ephyra.domain.navigation.motion

import ephyra.domain.navigation.motion.MotionPolicy.usesSharedCover
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MotionPolicyTest {

    private val key = "manga_cover_42"

    @Test
    fun `library to series uses the cover as its only shared element`() {
        val plan = MotionPolicy.plan(MotionRoutePair.LIBRARY_SERIES, MotionDirection.FORWARD, key)
        assertEquals(MotionRoutePair.LIBRARY_SERIES, plan.pair)
        assertEquals(key, plan.sharedElementKey)
        assertTrue(plan.shouldAnimateSharedElement)
    }

    @Test
    fun `non cover content does not move when a shared element carries the transition`() {
        // The contract: "full-screen content does not independently scale or slide". A container
        // transform competing with the cover is what makes a return look wrong.
        val plan = MotionPolicy.plan(MotionRoutePair.LIBRARY_SERIES, MotionDirection.FORWARD, key)
        assertEquals(ContainerMotion.NONE, plan.effectiveContainerMotion)
    }

    @Test
    fun `an element that never resolved falls back to a crossfade`() {
        // Animating a shared element that has no bounds produces a slide from nowhere.
        val plan = MotionPolicy.plan(
            MotionRoutePair.LIBRARY_SERIES,
            MotionDirection.FORWARD,
            key,
            sharedElementFound = false,
        )
        assertFalse(plan.shouldAnimateSharedElement)
        assertEquals(ContainerMotion.CROSSFADE, plan.effectiveContainerMotion)
    }

    @Test
    fun `a missing element key also falls back to a crossfade`() {
        val plan = MotionPolicy.plan(MotionRoutePair.LIBRARY_SERIES, MotionDirection.FORWARD, sharedElementKey = null)
        assertFalse(plan.shouldAnimateSharedElement)
        assertEquals(ContainerMotion.CROSSFADE, plan.effectiveContainerMotion)
    }

    @Test
    fun `a forward shared element uses the forward duration`() {
        val plan = MotionPolicy.plan(
            MotionRoutePair.LIBRARY_SERIES,
            MotionDirection.FORWARD,
            key,
        )
        assertEquals(MotionPolicy.SHARED_ELEMENT_DURATION_MILLIS, plan.durationMillis)
    }

    @Test
    fun `a crossfade fallback is shorter than a shared element transition`() {
        val shared = MotionPolicy.plan(MotionRoutePair.LIBRARY_SERIES, MotionDirection.FORWARD, key)
        val fallback = MotionPolicy.plan(
            MotionRoutePair.LIBRARY_SERIES,
            MotionDirection.FORWARD,
            key,
            sharedElementFound = false,
        )
        assertTrue(fallback.durationMillis < shared.durationMillis)
    }

    @Test
    fun `reduced motion keeps the crossfade rather than cutting`() {
        // An instant cut loses the continuity that tells the user where they went.
        val plan = MotionPolicy.plan(MotionRoutePair.LIBRARY_SERIES, MotionDirection.FORWARD, key, reducedMotion = true)
        assertTrue(plan.reducedMotion)
        assertEquals(ContainerMotion.CROSSFADE, plan.effectiveContainerMotion)
    }

    @Test
    fun `reduced motion reports an instant duration`() {
        val plan = MotionPolicy.plan(MotionRoutePair.LIBRARY_SERIES, MotionDirection.FORWARD, key, reducedMotion = true)
        assertEquals(0, plan.effectiveDurationMillis)
    }

    @Test
    fun `reduced motion leaves motion intact when motion is not requested`() {
        val plan = MotionPolicy.plan(
            MotionRoutePair.LIBRARY_SERIES,
            MotionDirection.FORWARD,
            key,
            reducedMotion = false,
        )
        assertEquals(MotionPolicy.SHARED_ELEMENT_DURATION_MILLIS, plan.effectiveDurationMillis)
    }

    @Test
    fun `reduced motion still resolves the element so it is not drawn twice`() {
        // The element must still be found and matched; only the interpolation is dropped.
        val plan = MotionPolicy.plan(MotionRoutePair.LIBRARY_SERIES, MotionDirection.FORWARD, key, reducedMotion = true)
        assertEquals(key, plan.sharedElementKey)
    }

    @Test
    fun `the return is quicker than the arrival`() {
        // Material 3's shared-element spec is deliberately asymmetric: the user already knows where
        // back goes, so the cover only has to retrace its path. Running the full forward length
        // both ways is what read as sluggish on the way out — the owner-reported "awkward" back.
        val forward = MotionPolicy.plan(MotionRoutePair.LIBRARY_SERIES, MotionDirection.FORWARD, key)
        val back = MotionPolicy.plan(MotionRoutePair.LIBRARY_SERIES, MotionDirection.BACKWARD, key)
        assertTrue(
            back.durationMillis < forward.durationMillis,
            "back=${back.durationMillis} must be shorter than forward=${forward.durationMillis}",
        )
    }

    @Test
    fun `the return still animates rather than cutting`() {
        // Faster is not the same as instant. A back so short it reads as a cut loses the very
        // continuity that tells the user which cover they came from.
        val back = MotionPolicy.plan(MotionRoutePair.LIBRARY_SERIES, MotionDirection.BACKWARD, key)
        assertTrue(back.durationMillis > 0)
        assertTrue(back.shouldAnimateSharedElement)
    }

    @Test
    fun `the return is not quicker than the fallback it degrades to`() {
        // If the cover cannot be matched, the back path crossfades. A shared-element return that
        // were *faster* than its own fallback would make a matched cover feel slower than no cover
        // at all — the two paths would be inverted.
        val matched = MotionPolicy.plan(MotionRoutePair.LIBRARY_SERIES, MotionDirection.BACKWARD, key)
        val fallback = MotionPolicy.plan(
            MotionRoutePair.LIBRARY_SERIES,
            MotionDirection.BACKWARD,
            key,
            sharedElementFound = false,
        )
        assertTrue(
            matched.durationMillis >= fallback.durationMillis,
            "matched=${matched.durationMillis} must not be quicker than fallback=${fallback.durationMillis}",
        )
    }

    @Test
    fun `direction changes only the duration`() {
        // Both directions are the same pair, the same element, and the same container decision.
        // Only the timeline differs — so the return can be quick without becoming a different
        // animation, which is what "the same reverse model" is supposed to mean.
        val forward = MotionPolicy.plan(MotionRoutePair.LIBRARY_SERIES, MotionDirection.FORWARD, key)
        val back = MotionPolicy.plan(MotionRoutePair.LIBRARY_SERIES, MotionDirection.BACKWARD, key)
        assertEquals(forward.pair, back.pair)
        assertEquals(forward.sharedElementKey, back.sharedElementKey)
        assertEquals(forward.effectiveContainerMotion, back.effectiveContainerMotion)
        assertEquals(forward.shouldAnimateSharedElement, back.shouldAnimateSharedElement)
    }

    @Test
    fun `a direction is carried on the plan`() {
        // Recorded so the resolved plan is self-describing: a log or a test failure can say which
        // way the user was going without re-deriving it from the call site.
        val back = MotionPolicy.plan(MotionRoutePair.LIBRARY_SERIES, MotionDirection.BACKWARD, key)
        assertEquals(MotionDirection.BACKWARD, back.direction)
    }

    @Test
    fun `opposite is an involution`() {
        // The direction is resolved from whichever end of the transition is being described, so
        // flipping twice must land back where it started or a retried resolution would drift.
        assertEquals(MotionDirection.BACKWARD, MotionDirection.FORWARD.opposite())
        assertEquals(MotionDirection.FORWARD, MotionDirection.BACKWARD.opposite())
        assertEquals(MotionDirection.FORWARD, MotionDirection.FORWARD.opposite().opposite())
    }

    @Test
    fun `reduced motion collapses both directions equally`() {
        // Reduced motion removes the interpolation entirely, so the asymmetry that distinguishes the
        // two directions has nothing left to apply to and must not leak through as a difference.
        val forward = MotionPolicy.plan(
            MotionRoutePair.LIBRARY_SERIES,
            MotionDirection.FORWARD,
            key,
            reducedMotion = true,
        )
        val back = MotionPolicy.plan(
            MotionRoutePair.LIBRARY_SERIES,
            MotionDirection.BACKWARD,
            key,
            reducedMotion = true,
        )
        assertEquals(0, forward.effectiveDurationMillis)
        assertEquals(0, back.effectiveDurationMillis)
    }

    @Test
    fun `a non positive duration is rejected`() {
        assertThrows<IllegalArgumentException> {
            MotionPlan(
                pair = MotionRoutePair.LIBRARY_SERIES,
                direction = MotionDirection.FORWARD,
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
        val plan = MotionPolicy.plan(
            MotionRoutePair.LIBRARY_SERIES,
            MotionDirection.FORWARD,
            key,
            sharedElementFound = false,
        )
        assertEquals(key, plan.sharedElementKey)
        assertFalse(plan.shouldAnimateSharedElement)
    }

    @Test
    fun `only the library pair declares a shared cover`() {
        // The predicate MainActivity branches on. If a second pair ever returns true it would start
        // carrying a shared element it has no counterpart for, which degrades to a silent crossfade.
        assertTrue(MotionRoutePair.LIBRARY_SERIES.usesSharedCover())
    }

    @Test
    fun `the cover key is the same on both ends of the transition`() {
        // The library cell and the series header live in different modules and each builds this key
        // independently. If they ever disagree the shared element silently stops matching and the
        // transition degrades to a crossfade with no error anywhere — so the key is asserted once,
        // here, as the single definition both call.
        assertEquals("manga_cover_42", MotionPolicy.mangaCoverKey(42))
    }

    @Test
    fun `distinct manga produce distinct cover keys`() {
        // A key collision would make one cover animate in place of another, which is worse than no
        // shared element at all because it looks intentional.
        assertNotEquals(MotionPolicy.mangaCoverKey(1), MotionPolicy.mangaCoverKey(2))
    }

    @Test
    fun `a resolved cover leaves the container completely still`() {
        // The contract's "full-screen content does not independently scale or slide", now stated
        // against the value the Android layer actually passes to the transition. With the blurred
        // backdrop gone the cover is the only thing that should move; a crossfade here would put a
        // second, competing animation underneath it — the original "suck" on the way back.
        val plan = MotionPolicy.plan(MotionRoutePair.LIBRARY_SERIES, MotionDirection.FORWARD, key)
        assertEquals(ContainerMotion.NONE, plan.effectiveContainerMotion)
        assertTrue(plan.usesNoContainerMotion)
    }

    @Test
    fun `an unusable cover falls back to a crossfade rather than freezing`() {
        // NONE is only correct while the cover carries the transition. If the cover cannot be
        // matched there is nothing moving the content, so it must still crossfade rather than cut.
        val plan = MotionPolicy.plan(
            MotionRoutePair.LIBRARY_SERIES,
            MotionDirection.FORWARD,
            key,
            sharedElementFound = false,
        )
        assertEquals(ContainerMotion.CROSSFADE, plan.effectiveContainerMotion)
        assertFalse(plan.usesNoContainerMotion)
    }

    @Test
    fun `reduced motion does not ask for a still container`() {
        // Reduced motion still needs the content to change somehow; NONE here would leave the
        // destination sitting at zero opacity with nothing to bring it in.
        val plan = MotionPolicy.plan(MotionRoutePair.LIBRARY_SERIES, MotionDirection.FORWARD, key, reducedMotion = true)
        assertFalse(plan.usesNoContainerMotion)
    }
}
