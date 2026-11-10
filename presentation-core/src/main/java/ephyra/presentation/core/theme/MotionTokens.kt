package ephyra.presentation.core.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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

    /** 100ms: Swift fades and instant feedback. */
    const val DURATION_SHORT_2 = 100

    /** 150ms: Standard micro-interactions, ripples, and icon state changes. */
    const val DURATION_SHORT_3 = 150

    /** 200ms: Element exits and fast container collapses. */
    const val DURATION_SHORT_4 = 200

    /** 300ms: Standard component transitions, tab switches, and dialogs. */
    const val DURATION_MEDIUM_2 = 300

    /** 350ms: Navigation exits and sheet dismissals. */
    const val DURATION_MEDIUM_3 = 350

    /** 400ms: Complex component expansions and bottom sheet entries. */
    const val DURATION_MEDIUM_4 = 400

    /** 500ms: Large hero animations and shared element transforms. */
    const val DURATION_LONG_2 = 500

    /** 1000ms: Long-running expressive fades (the reader's in-page transitions). */
    const val DURATION_EXTRA_LONG_4 = 1000

    // Semantic duration aliases. These are not compat shims: they are the live vocabulary of the
    // banner, sheet, preference, search and adaptive-pane surfaces, kept as the names those
    // callers read. MotionVocabularyTest holds the rule for every member: referenced or deleted.
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

    // Easing aliases kept for the surfaces that historically used them; same rule as the
    // duration aliases above.
    val EasingDecelerate = EasingEmphasizedDecelerate
    val EasingAccelerate = EasingEmphasizedAccelerate

    // --- Reusable Tween Animation Specs ---
    //
    // This section used to also carry spring specs and quick/standard/emphasized tween presets
    // with no call sites anywhere in production code. They were deleted rather than kept
    // "in case": an unused motion primitive is not a future default, it is the wrong duration
    // waiting to be picked by someone who assumes this file is curated. MotionVocabularyTest
    // now fails the build if a member here loses its last consumer.

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

    // Shared-axis-Z (scale) tokens were removed: they had zero call sites, and a full-page scale is
    // the modal/hierarchy cue that `containerEnter(SHARED_AXIS)` deliberately stopped using for
    // ordinary navigation. A scale transition for a modal can be re-added when one exists to use it.

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
        }

    /** Matching outgoing container motion for [containerEnter]. */
    fun containerExit(motion: ContainerMotion, durationMillis: Int): ExitTransition =
        when (motion) {
            ContainerMotion.NONE -> ExitTransition.None
            ContainerMotion.CROSSFADE -> m3CrossfadeExit(durationMillis)
        }

    /**
     * The policy crossfade: the rendering of the HIERARCHICAL plan (since the owner's motion
     * audit of 2026-11-09 removed the shared-axis slide from ordinary navigation) and the
     * fallback when a declared shared element turns out to be unusable.
     *
     * Pure alpha at the policy's crossfade duration: the minimal, no-movement change that keeps
     * the transition legible as a deliberate change of place rather than a glitch.
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
     * Material 3 Fade Through enter transition for peer navigation (e.g. bottom nav tabs).
     * Smoothly scales up from 96% with slight entry delay to let the departing screen clear.
     *
     * Retained as the fallback for route pairs that are not two peers on the tab axis (a nested
     * screen reached inside a tab), where a horizontal slide would imply an ordering that does not
     * exist.
     */
    fun m3FadeThroughEnter(): EnterTransition =
        // Fade only. The scale-in this used to carry is an M2 leftover: scaling a whole page
        // reads as a zoom (a modal/hierarchy cue), which is wrong for an unordered destination
        // swap, and it forces a full-screen layer to be re-rasterised every frame it runs.
        fadeIn(
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
