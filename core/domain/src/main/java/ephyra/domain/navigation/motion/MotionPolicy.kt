package ephyra.domain.navigation.motion

/**
 * The motion assigned to a route pair.
 *
 * Motion is a property of the *pair*, not of a screen, because the same screen entered from two
 * places should not animate the same way. Assigning per screen is what makes transitions feel
 * arbitrary: a destination can be "the shared-cover destination" on one path and "some other
 * destination" on another, and the inconsistency is invisible in code review.
 */
enum class MotionRoutePair {
    /** Library grid to series detail. Cover is the only shared element. */
    LIBRARY_SERIES,

    /** A reader entry point that is not a cover, e.g. a continue-reading shortcut. */
    READER_ENTRY,

    /** Peer tab switch. */
    TAB_PEER,

    /** A dialog or sheet returning to its parent. */
    SHEET_PARENT,

    /** Anything with no declared motion rule. */
    UNDECLARED,
}

/** How the non-shared content of a transition behaves. */
enum class ContainerMotion {
    /** No movement at all; content simply changes. */
    NONE,

    /** Opacity change only. The contract's default for non-shared content. */
    CROSSFADE,

    /** Opacity plus a positional shift, for genuinely hierarchical pairs. */
    SHARED_AXIS,
}

/**
 * The resolved motion plan for one transition.
 *
 * [sharedElement] is deliberately nullable rather than a boolean. A transition either has exactly
 * one shared element with an identity, or has none. "Has a shared element" and "the shared element
 * resolved to valid bounds" are different questions, and conflating them is what makes a
 * transition look broken when the element scrolls out of view mid-transition.
 */
data class MotionPlan(
    val pair: MotionRoutePair,
    val sharedElementKey: String?,
    val containerMotion: ContainerMotion,
    val durationMillis: Int,
    /** True when the shared element resolved to usable bounds and the shared element may animate. */
    val sharedElementUsable: Boolean,
    /** True when the animation must be an instant state change with no interpolation. */
    val reducedMotion: Boolean,
) {
    init {
        require(durationMillis > 0) { "durationMillis must be positive" }
    }

    /**
     * The motion the container should actually run.
     *
     * Reduced motion and an unusable shared element both collapse to a crossfade rather than to
     * nothing: an instant cut loses the continuity that tells the user where they went, and a
     * crossfade is the cheapest way to keep it. This is the contract's stated fallback.
     */
    val effectiveContainerMotion: ContainerMotion
        get() = when {
            reducedMotion -> ContainerMotion.CROSSFADE
            sharedElementUsable && sharedElementKey != null -> ContainerMotion.NONE
            // A declared NONE only makes sense while the shared element carries the motion. Once
            // that element is unusable there is nothing moving the content, so it must crossfade
            // rather than cut to the new screen with no transition at all.
            containerMotion == ContainerMotion.NONE -> ContainerMotion.CROSSFADE
            else -> containerMotion
        }

    /**
     * Whether a shared element animation should be attempted.
     *
     * Under reduced motion the element still has to be found so the two screens do not briefly
     * render it twice, but it is not interpolated.
     */
    val shouldAnimateSharedElement: Boolean
        get() = sharedElementUsable && sharedElementKey != null

    /** Duration actually used, collapsed to instant when reduced motion is requested. */
    val effectiveDurationMillis: Int get() = if (reducedMotion) 0 else durationMillis
}

/**
 * Resolves motion for a route pair.
 *
 * Pure so the rules are testable without a running composition. The Android layer supplies the two
 * facts it cannot know: whether a shared element was found, and whether the user has asked for
 * reduced motion.
 */
object MotionPolicy {

    /**
     * M3 Expressive duration for a shared-element transition.
     *
     * Enter and exit deliberately share one duration. Asymmetric values (300ms in, 200ms out)
     * make the two screens cross at a different point, which reads as a stutter or a flash rather
     * than a single continuous movement.
     */
    const val SHARED_ELEMENT_DURATION_MILLIS: Int = 300

    /** Fallback duration for a crossfade with no shared element. */
    const val CROSSFADE_DURATION_MILLIS: Int = 200

    /**
     * Builds the plan for [pair].
     *
     * [sharedElementKey] is the key both screens use for the same element, or null when there is
     * none to match. [sharedElementFound] reports whether the element actually resolved to bounds;
     * when false the plan degrades to a crossfade instead of animating from nowhere.
     */
    fun plan(
        pair: MotionRoutePair,
        sharedElementKey: String? = null,
        sharedElementFound: Boolean = true,
        reducedMotion: Boolean = false,
    ): MotionPlan {
        val declaredSharedElement = sharedElementKey != null &&
            sharedElementFound &&
            pair == MotionRoutePair.LIBRARY_SERIES

        val container = when (pair) {
            MotionRoutePair.LIBRARY_SERIES -> ContainerMotion.NONE
            MotionRoutePair.TAB_PEER -> ContainerMotion.CROSSFADE
            MotionRoutePair.READER_ENTRY -> ContainerMotion.SHARED_AXIS
            MotionRoutePair.SHEET_PARENT -> ContainerMotion.CROSSFADE
            MotionRoutePair.UNDECLARED -> ContainerMotion.CROSSFADE
        }

        val duration = if (declaredSharedElement) {
            SHARED_ELEMENT_DURATION_MILLIS
        } else {
            CROSSFADE_DURATION_MILLIS
        }

        return MotionPlan(
            pair = pair,
            sharedElementKey = sharedElementKey,
            containerMotion = container,
            durationMillis = duration,
            sharedElementUsable = declaredSharedElement,
            reducedMotion = reducedMotion,
        )
    }
}
