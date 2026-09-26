package ephyra.presentation.core.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Material 3 Expressive motion tokens for Ephyra.
 *
 * Provides consistent animation durations, easing curves, and physics-based spring specs
 * aligned with the latest Material 3 Expressive motion guidelines.
 */
object MotionTokens {

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
     * Container motion for destinations that share a hero element. The non-shared content
     * crossfades on the same long-form timeline as the shared element, avoiding a short
     * scale/fade that makes the remainder of the screen disappear before the cover lands.
     */
    fun m3SharedElementContainerEnter(reducedMotion: Boolean = false): EnterTransition {
        if (reducedMotion) return EnterTransition.None
        return fadeIn(
            animationSpec = tween(
                durationMillis = sharedElementContainerDuration(),
                easing = LinearEasing,
            ),
        )
    }

    /** Matching outgoing container motion for a shared-element destination. */
    fun m3SharedElementContainerExit(reducedMotion: Boolean = false): ExitTransition {
        if (reducedMotion) return ExitTransition.None
        return fadeOut(
            animationSpec = tween(
                durationMillis = sharedElementContainerDuration(),
                easing = LinearEasing,
            ),
        )
    }

    /**
     * Duration shared by both halves of a shared-element transition.
     *
     * Enter and exit must use one value. The previous pair was 300ms in and 200ms out, so the two
     * screens crossed opacity at different points and the cover competed with a container that was
     * still fading, which reads as a stutter rather than one continuous movement. A linear easing is
     * used deliberately: an eased container over an eased cover gives two curves competing for the
     * same visual element.
     */
    fun sharedElementContainerDuration(): Int = ephyra.domain.navigation.motion.MotionPolicy
        .SHARED_ELEMENT_DURATION_MILLIS

    /**
     * Material 3 Fade Through enter transition for peer navigation (e.g. bottom nav tabs).
     * Smoothly scales up from 96% with slight entry delay to let the departing screen clear.
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
