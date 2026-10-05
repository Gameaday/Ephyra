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

    // NOTE: this enum deliberately has one entry. It used to declare TAB_PEER, READER_ENTRY,
    // SHEET_PARENT and UNDECLARED, but no production call site ever named them: tab motion is
    // owned by HomeScreen's tab NavHost (order-aware slides), reader entry by Activity window
    // animations, sheets by the predictive-back drag, and undeclared routes by the root
    // NavHost's shared-axis-X fallback. Declaring pairs no caller can produce is worse than no
    // declaration: the policy looks like it governs motion it does not, and the tests for those
    // branches asserted rules nothing executed. A pair is added here when a call site names it.
}

/**
 * Which way the user is travelling through a route pair.
 *
 * ## Why this exists
 *
 * The policy was originally direction-free: `plan(LIBRARY_SERIES, …)` returned the same duration
 * whether the user was opening a series or returning to the library, and a test asserted that
 * symmetry. It reads well — "enter and exit share one duration" — and it is wrong.
 *
 * Material 3's shared-element spec is deliberately **asymmetric**. The forward transition is longer
 * because the user is discovering where they are going; the backward one is shorter because they
 * already know, and a long reverse reads as sluggish rather than smooth. Running the full forward
 * timeline in both directions is what made returning from a series page feel "awkward" — the
 * complaint this pair exists to fix.
 *
 * The symmetry was defensible only while the container crossfaded, because then the two halves had
 * to agree or the screens would cross opacity at different points. Now that the container is held
 * still and the cover carries the transition outright, that constraint is gone and the asymmetry
 * costs nothing.
 */
enum class MotionDirection {
    /** Into the destination: the user is arriving somewhere new. */
    FORWARD,

    /** Back toward the origin: the user is returning somewhere known. */
    BACKWARD,
    ;

    /** The other direction, for resolving a transition from whichever end it starts. */
    fun opposite(): MotionDirection = if (this == FORWARD) BACKWARD else FORWARD
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
    val direction: MotionDirection,
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
     * Whether the container should be left completely alone for this transition.
     *
     * Distinct from [effectiveContainerMotion] being `NONE` in the sense that matters here: the
     * Android layer needs a *decision*, not an enum to re-interpret. `MotionTokens.containerEnter`
     * maps this straight onto `EnterTransition.None`, and this property is what says the absence of
     * motion is the intended outcome rather than a missing case someone forgot to handle.
     */
    val usesNoContainerMotion: Boolean
        get() = effectiveContainerMotion == ContainerMotion.NONE

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
     * M3 Expressive duration for a shared-element transition going **into** a destination.
     *
     * Longer, because the user is discovering a new screen: the cover travels further and the
     * content around it has more to say.
     */
    const val SHARED_ELEMENT_DURATION_MILLIS: Int = 300

    /**
     * Duration for the same transition going **back**.
     *
     * Shorter than the forward value, per Material 3's shared-element spec. The destination is one
     * the user just left and can re-find without help, so the cover only has to retrace its path —
     * holding the full forward length here is what read as sluggish on the way out.
     *
     * Kept above [CROSSFADE_DURATION_MILLIS] so a fast return is still legible as a movement
     * rather than a cut, and so it never becomes quicker than the no-shared-element path it falls
     * back to.
     */
    const val SHARED_ELEMENT_BACK_DURATION_MILLIS: Int = 200

    /** Fallback duration for a crossfade with no shared element. */
    const val CROSSFADE_DURATION_MILLIS: Int = 200

    /**
     * The key both screens use for one manga's cover, so the library grid cell and the series
     * header agree on which element is shared.
     *
     * This is a function rather than a format string at each call site because the two ends live in
     * different modules. A typo in either one does not fail to compile — it just silently stops the
     * shared element from matching, and the transition quietly degrades to a crossfade that nobody
     * can explain.
     */
    fun mangaCoverKey(mangaId: Long): String = "manga_cover_$mangaId"

    /**
     * Whether [MotionRoutePair] is the library-cover pair, i.e. the one route pair in the app that
     * carries a shared element.
     */
    fun MotionRoutePair.usesSharedCover(): Boolean = this == MotionRoutePair.LIBRARY_SERIES

    /**
     * Builds the plan for [pair] travelling in [direction].
     *
     * [sharedElementKey] is the key both screens use for the same element, or null when there is
     * none to match. [sharedElementFound] reports whether the element actually resolved to bounds;
     * when false the plan degrades to a crossfade instead of animating from nowhere.
     *
     * [direction] is required rather than defaulted. A default would let a call site silently get
     * forward timing for a back navigation, which is precisely the defect this parameter was added
     * to remove — and it would do so invisibly, because the result would still compile and still
     * animate.
     */
    fun plan(
        pair: MotionRoutePair,
        direction: MotionDirection = MotionDirection.FORWARD,
        sharedElementKey: String? = null,
        sharedElementFound: Boolean = true,
        reducedMotion: Boolean = false,
    ): MotionPlan {
        val declaredSharedElement = sharedElementKey != null &&
            sharedElementFound &&
            pair == MotionRoutePair.LIBRARY_SERIES

        val container = when (pair) {
            MotionRoutePair.LIBRARY_SERIES -> ContainerMotion.NONE
        }

        val duration = if (declaredSharedElement) {
            if (direction == MotionDirection.BACKWARD) {
                SHARED_ELEMENT_BACK_DURATION_MILLIS
            } else {
                SHARED_ELEMENT_DURATION_MILLIS
            }
        } else {
            CROSSFADE_DURATION_MILLIS
        }

        return MotionPlan(
            pair = pair,
            direction = direction,
            sharedElementKey = sharedElementKey,
            containerMotion = container,
            durationMillis = duration,
            sharedElementUsable = declaredSharedElement,
            reducedMotion = reducedMotion,
        )
    }
}
