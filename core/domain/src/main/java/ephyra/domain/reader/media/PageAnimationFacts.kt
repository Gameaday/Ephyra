package ephyra.domain.reader.media

/**
 * What is known about a page's animation and its format, kept as two separate facts.
 *
 * **Why these were folded into one boolean, and why that was wrong.** The production check read
 * `isAnimatedAndSupported(bytes) || imageType(bytes) == JXL` and called the result "animated". That
 * conflation has two consequences, both of which `RenderPathPolicy` was written to prevent:
 *
 *  - a **static** JXL page was reported as animated, so it was blocked from slicing with an
 *    animation reason, which hides a genuinely static page behind a label that does not apply to it;
 *  - a **detection failure** was coerced to "static" by `getOrDefault(false)`, so an animated page
 *    whose bytes could not be classified would be sliced and display only its first frame -- which
 *    is worse than the single-image fallback, because it looks like a loading bug rather than a
 *    classification one.
 *
 * Neither is a theoretical concern: both were live in the webtoon reader.
 */
data class PageAnimationFacts(
    /** Whether the page holds animation, and how confident that answer is. */
    val verdict: AnimationVerdict,

    /**
     * Whether the format supports region decoding at all.
     *
     * Distinct from [verdict] because a progressive JPEG and a JXL file are both "not sliceable"
     * for entirely different reasons, and only one of them is about animation.
     */
    val regionDecodable: Boolean,
) {
    /** True only when the page is known to be static *and* sliceable. */
    val sliceable: Boolean
        get() = verdict == AnimationVerdict.Detected(false) && regionDecodable

    companion object {
        /** A page known to be static, in a region-decodable format. */
        fun staticRegionDecodable(): PageAnimationFacts =
            PageAnimationFacts(AnimationVerdict.Detected(false), regionDecodable = true)

        /**
         * A page whose animation could not be determined.
         *
         * Treated as blocking, never as static. [regionDecodable] is left `true` so the *format*
         * remains unblocked and the reason reported is the unknown one, which is the actionable
         * half.
         */
        fun indeterminate(): PageAnimationFacts =
            PageAnimationFacts(AnimationVerdict.Indeterminate, regionDecodable = true)
    }
}

/**
 * Turns raw format probes into [PageAnimationFacts].
 *
 * The probes are passed as functions rather than called directly so the classification is a pure
 * function of *what the probes reported* -- including that one of them threw -- and can therefore be
 * tested exhaustively. The production call site supplies lambdas that touch the image decoder.
 */
object PageAnimationClassifier {

    /**
     * @param isAnimatedAndSupported reports whether the bytes hold animation. Throwing means
     *   "could not tell", which is not the same answer as `false`.
     * @param regionDecodable reports whether the format supports region decoding.
     */
    fun classify(
        isAnimatedAndSupported: () -> Boolean,
        regionDecodable: () -> Boolean,
    ): PageAnimationFacts = try {
        PageAnimationFacts(
            verdict = AnimationVerdict.Detected(isAnimatedAndSupported()),
            regionDecodable = regionDecodable(),
        )
    } catch (_: Throwable) {
        // A probe that threw has told us nothing, and "nothing" must not become "static".
        PageAnimationFacts.indeterminate()
    }
}
