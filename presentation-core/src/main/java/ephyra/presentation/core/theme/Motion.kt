package ephyra.presentation.core.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import ephyra.domain.navigation.motion.ContainerMotion
import ephyra.domain.navigation.motion.MotionPolicy

/**
 * Material 3 Expressive motion tokens for Ephyra.
 *
 * Provides consistent animation durations, easing curves, and physics-based spring specs
 * aligned with the latest Material 3 Expressive motion guidelines.
 *
 * ## Tokens here do not decide motion
 *
 * These are the *vocabulary*; [MotionPolicy] is the decision. A screen picks its tokens by asking
 * the policy what its route pair should do and then calling [containerEnter] / [containerExit] with
 * the answer. A screen that reaches for a specific transition by name has re-decided the policy
 * locally, which is how a rule that is asserted in `:core:domain` ends up unenforced in the layer
 * that actually moves things.
 */
object MotionTokens {

    /** Crossfade duration, owned by [MotionPolicy] so the two layers cannot disagree. */
    private val CROSSFADE_DURATION_MILLIS: Int = MotionPolicy.CROSSFADE_DURATION_MILLIS

    // --- Durations (Material 3 Expressive Scale) ---

    /** 50ms: Ultra-fast micro-interactions. */
    const val DURATION_SHORT_1 = 50

    /** 100ms: Swift fades and instant feedback. */
    const val DURATION_SHORT_2 = 100

    /** 150ms: Standard micro-interactions, ripples, and icon state changes. */
    const val DURATION_SHORT_3 = 150

    /** 200ms: Element exits and fast container collapses. */
    const val DURATION_SHORT_4 = 200

    /** 250ms: Simple element entries and tooltips. */
    const val DURATION_MEDIUM_1 = 250

    /** 300ms: Standard component transitions, tab switches, and dialogs. */
    const val DURATION_MEDIUM_2 = 300

    /** 350ms: Navigation exits and sheet dismissals. */
    const val DURATION_MEDIUM_3 = 350

    /** 400ms: Complex component expansions and bottom sheet entries. */
    const val DURATION_MEDIUM_4 = 400

    /** 450ms: Full-screen container transitions (enter). */
    const val DURATION_LONG_1 = 450

    /** 500ms: Large hero animations and shared element transforms. */
    const val DURATION_LONG_2 = 500

    /** 550ms: Multi-element choreographed arrivals. */
    const val DURATION_LONG_3 = 550

    /** 600ms: Immersive onboarding and celebration sequences. */
    const val DURATION_LONG_4 = 600

    /** 700ms - 1000ms: Extended expressive sequences. */
    const val DURATION_EXTRA_LONG_1 = 700
    const val DURATION_EXTRA_LONG_2 = 800
    const val DURATION_EXTRA_LONG_3 = 900
    const val DURATION_EXTRA_LONG_4 = 1000

    // Backward-compatible duration aliases
    const val DURATION_SHORT = DURATION_SHORT_3
    const val DURATION_MEDIUM = DURATION_MEDIUM_2
    const val DURATION_LONG = DURATION_LONG_2

    // --- Easing Curves (Material 3 Expressive) ---

    /** Standard easing for elements entering and exiting together. */
    val EasingStandard = FastOutSlowInEasing

    /** Emphasized easing: standard for prominent attention-drawing transitions. */
    val EasingEmphasized = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /** Emphasized decelerate: incoming elements entering the screen prominently. */
    val EasingEmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** Emphasized accelerate: outgoing elements leaving the screen prominently. */
    val EasingEmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    /** Standard decelerate for elements appearing on screen. */
    val EasingStandardDecelerate = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)

    /** Standard accelerate for elements leaving the screen. */
    val EasingStandardAccelerate = CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f)

    // Backward-compatible easing aliases
    val EasingDecelerate = EasingEmphasizedDecelerate
    val EasingAccelerate = EasingEmphasizedAccelerate

    // --- Physics-based Spring Specs ---

    /** Snappy spring: fast, clean feedback without oscillation (buttons, toggles, chips). */
    fun <T> springSnappy(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** Expressive spring: smooth, natural movement with gentle settling (cards, dialogs). */
    fun <T> springExpressive(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow,
    )

    /** Playful bouncy spring: micro-interactions, badge pops, and icon toggles. */
    fun <T> springBouncy(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    // --- Reusable Tween Animation Specs ---

    /** Quick tween for micro-interactions. */
    fun <T> tweenShort(): FiniteAnimationSpec<T> = tween(
        durationMillis = DURATION_SHORT_3,
        easing = EasingStandard,
    )

    /** Standard tween for component-level transitions. */
    fun <T> tweenMedium(): FiniteAnimationSpec<T> = tween(
        durationMillis = DURATION_MEDIUM_2,
        easing = EasingStandard,
    )

    /** Emphasized tween for attention-drawing transitions. */
    fun <T> tweenEmphasized(): FiniteAnimationSpec<T> = tween(
        durationMillis = DURATION_LONG_2,
        easing = EasingEmphasized,
    )

    /** Enter transition tween for elements appearing on screen. */
    fun <T> tweenEnter(): FiniteAnimationSpec<T> = tween(
        durationMillis = DURATION_MEDIUM_4,
        easing = EasingEmphasizedDecelerate,
    )

    /** Exit transition tween for elements leaving the screen. */
    fun <T> tweenExit(): FiniteAnimationSpec<T> = tween(
        durationMillis = DURATION_SHORT_4,
        easing = EasingEmphasizedAccelerate,
    )

    // --- Preset Navigation Transitions ---

    /**
     * Material 3 Shared Axis Z enter transition for forward screen navigation.
     * Scales up from 92% to 100% with subtle fade in.
     */
    fun m3SharedAxisZEnter(): EnterTransition =
        scaleIn(
            initialScale = 0.92f,
            animationSpec = tween(durationMillis = DURATION_LONG_1, easing = EasingEmphasizedDecelerate),
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = DURATION_MEDIUM_4,
                delayMillis = DURATION_SHORT_1,
                easing = EasingEmphasizedDecelerate,
            ),
        )

    /**
     * Material 3 Shared Axis Z exit transition for forward screen navigation.
     * Scales up slightly from 100% to 106% with swift fade out.
     */
    fun m3SharedAxisZExit(): ExitTransition =
        scaleOut(
            targetScale = 1.06f,
            animationSpec = tween(durationMillis = DURATION_MEDIUM_3, easing = EasingEmphasizedAccelerate),
        ) + fadeOut(
            animationSpec = tween(durationMillis = DURATION_SHORT_4, easing = EasingEmphasizedAccelerate),
        )

    /**
     * Material 3 Shared Axis Z pop enter transition for backward/up navigation.
     * Scales down from 106% to 100% with smooth fade in.
     */
    fun m3SharedAxisZPopEnter(): EnterTransition =
        scaleIn(
            initialScale = 1.06f,
            animationSpec = tween(durationMillis = DURATION_MEDIUM_4, easing = EasingEmphasizedDecelerate),
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = DURATION_MEDIUM_4,
                delayMillis = DURATION_SHORT_1,
                easing = EasingEmphasizedDecelerate,
            ),
        )

    /**
     * Material 3 Shared Axis Z pop exit transition for backward/up navigation.
     * Scales down from 100% to 92% with swift fade out.
     */
    fun m3SharedAxisZPopExit(): ExitTransition =
        scaleOut(
            targetScale = 0.92f,
            animationSpec = tween(durationMillis = DURATION_MEDIUM_2, easing = EasingEmphasizedAccelerate),
        ) + fadeOut(
            animationSpec = tween(durationMillis = DURATION_SHORT_4, easing = EasingEmphasizedAccelerate),
        )

    /**
     * Duration shared by both halves of a shared-element transition.
     *
     * Enter and exit must use one value. The previous pair was 300ms in and 200ms out, so the two
     * screens crossed opacity at different points and the cover competed with a container that was
     * still fading, which reads as a stutter rather than one continuous movement.
     */
    fun sharedElementContainerDuration(): Int = ephyra.domain.navigation.motion.MotionPolicy
        .SHARED_ELEMENT_DURATION_MILLIS

    /**
     * Container motion for a transition whose [ContainerMotion.NONE] plan is in force.
     *
     * A true no-op, not a fade. This is the change that makes the library <-> series transition read
     * as one movement: with the blurred cover backdrop gone (see `MangaInfoBox`), the cover is the
     * only thing that should change, so a crossfade of the two full screens underneath it only
     * competes with it. Both screens hold their own frame and the cover travels over a stable
     * background.
     *
     * Enter and exit are symmetric, so the two screens never cross opacity at different points and
     * the pair cannot read as a stutter.
     */
    fun containerEnter(motion: ContainerMotion, durationMillis: Int): EnterTransition =
        when (motion) {
            ContainerMotion.NONE -> EnterTransition.None
            ContainerMotion.CROSSFADE -> m3CrossfadeEnter(durationMillis)
            ContainerMotion.SHARED_AXIS -> m3SharedAxisZEnter()
        }

    /** Matching outgoing container motion for [containerEnter]. */
    fun containerExit(motion: ContainerMotion, durationMillis: Int): ExitTransition =
        when (motion) {
            ContainerMotion.NONE -> ExitTransition.None
            ContainerMotion.CROSSFADE -> m3CrossfadeExit(durationMillis)
            ContainerMotion.SHARED_AXIS -> m3SharedAxisZExit()
        }

    /**
     * Fallback crossfade used when a declared shared element turns out to be unusable.
     *
     * Slower than the previous 200ms on purpose. It is the *only* motion left when the cover cannot
     * be matched, so it has to carry the whole transition on its own; a quick dissolve reads as a
     * glitch between two unrelated screens rather than a deliberate change of place.
     */
    fun m3CrossfadeEnter(durationMillis: Int = CROSSFADE_DURATION_MILLIS): EnterTransition =
        if (durationMillis <= 0) {
            EnterTransition.None
        } else {
            fadeIn(
                animationSpec = tween(
                    durationMillis = durationMillis,
                    easing = EasingStandard,
                ),
            )
        }

    /** Matching outgoing half of [m3CrossfadeEnter]. */
    fun m3CrossfadeExit(durationMillis: Int = CROSSFADE_DURATION_MILLIS): ExitTransition =
        if (durationMillis <= 0) {
            ExitTransition.None
        } else {
            fadeOut(
                animationSpec = tween(
                    durationMillis = durationMillis,
                    easing = EasingStandard,
                ),
            )
        }

    /**
     * Material 3 shared-axis X enter transition for peer tab navigation.
     *
     * The five bottom-nav destinations are ordered peers, so a tab change is a horizontal move
     * between adjacent pages rather than an unrelated destination swap. Sliding in from the side
     * that matches the tab order makes the tab bar and the content read as one surface, and makes
     * returning to a previous tab a deliberate reverse move instead of a second fade.
     *
     * [forward] is true when the target tab sits to the right of the origin tab (higher index), so
     * the incoming page arrives from the right edge; false reverses it.
     *
     * Enter and exit share one duration and complementary easing so the two pages travel together
     * and settle at the same instant — the failure mode of the old fade-through was that the two
     * halves resolved at different points and read as discrete rather than cohesive.
     */
    fun m3TabSlideEnter(forward: Boolean): EnterTransition =
        slideInHorizontally(
            initialOffsetX = { width -> if (forward) width else -width },
            animationSpec = tween(
                durationMillis = DURATION_MEDIUM_2,
                easing = EasingEmphasizedDecelerate,
            ),
        )

    /** Matching outgoing half of [m3TabSlideEnter]; travels the opposite way across the viewport. */
    fun m3TabSlideExit(forward: Boolean): ExitTransition =
        slideOutHorizontally(
            targetOffsetX = { width -> if (forward) -width else width },
            animationSpec = tween(
                durationMillis = DURATION_MEDIUM_2,
                easing = EasingEmphasizedAccelerate,
            ),
        )

    /**
     * Material 3 Fade Through enter transition for peer navigation (e.g. bottom nav tabs).
     * Smoothly scales up from 96% with slight entry delay to let the departing screen clear.
     *
     * Retained as the fallback for route pairs that are not two peers on the tab axis (a nested
     * screen reached inside a tab), where a horizontal slide would imply an ordering that does not
     * exist.
     */
    fun m3FadeThroughEnter(): EnterTransition =
        fadeIn(
            animationSpec = tween(
                durationMillis = DURATION_MEDIUM_2,
                delayMillis = DURATION_SHORT_2,
                easing = EasingEmphasizedDecelerate,
            ),
        ) + scaleIn(
            initialScale = 0.96f,
            animationSpec = tween(
                durationMillis = DURATION_MEDIUM_2,
                delayMillis = DURATION_SHORT_2,
                easing = EasingEmphasizedDecelerate,
            ),
        )

    /**
     * Material 3 Fade Through exit transition for peer navigation.
     * Swiftly fades out so the arriving peer has a clean canvas.
     */
    fun m3FadeThroughExit(): ExitTransition =
        fadeOut(
            animationSpec = tween(durationMillis = DURATION_SHORT_2, easing = EasingEmphasizedAccelerate),
        )
}

/**
 * Convenience accessor for motion tokens from [MaterialTheme].
 */
val MaterialTheme.motion: MotionTokens
    @Composable
    @ReadOnlyComposable
    get() = MotionTokens
