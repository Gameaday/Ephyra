package ephyra.domain.reader.media

/**
 * Whether a format can be region-decoded at all.
 *
 * Kept separate from [AnimationKind] because the two properties are independent: a progressive
 * JPEG is static and not region-decodable, an animated WebP is region-decodable in principle but
 * cannot be sliced anyway. Conflating them produces a false reason for a genuine fallback.
 *
 * The table is deliberately conservative. Anything not named here is treated as not region
 * decodable, so an unrecognised format takes the whole-image path rather than attempting a decode
 * that returns only the first frame.
 */
enum class RegionDecodeSupport {
    /** The codec can decode an arbitrary sub-rectangle at a chosen sample size. */
    SUPPORTED,

    /**
     * The codec is static but cannot decode a region.
     *
     * Progressive JPEG is the motivating case: the decoder must walk the scan stream, so a region
     * request is refused outright.
     */
    STATIC_BUT_NOT_REGION_DECODABLE,

    /** The codec holds multiple frames. Slicing would require re-encoding each one. */
    ANIMATED,

    /** No decoder exists for this format in the project. */
    NO_DECODER,
}

/**
 * Animation detection policy.
 *
 * Detection inspects bytes without decoding pixels, so it is cheap but fallible: a truncated
 * buffer, a format the sniffer does not recognise, or an I/O error can all leave it undecided.
 * The policy exists to make the undecided case explicit, because the failure it prevents is
 * silent — slicing an animated page yields a still frame of it, which looks like a rendering bug
 * rather than a detection failure.
 */
object AnimationPolicy {

    /**
     * Classifies a format for slicing purposes.
     *
     * [detectedAnimated] is the result of inspecting the bytes, or null when detection did not
     * complete. Passing the answer in rather than reading it here keeps this pure and testable
     * without fixtures for every codec.
     */
    fun classify(
        format: PageImageFormat,
        regionDecodable: Boolean,
        detectedAnimated: Boolean?,
    ): SlicingBlocker = when {
        detectedAnimated == true -> SlicingBlocker.ANIMATED_CONTENT
        detectedAnimated == null -> SlicingBlocker.ANIMATION_DETECTION_FAILED
        !regionDecodable -> SlicingBlocker.FORMAT_NOT_REGION_DECODABLE
        else -> SlicingBlocker.NONE
    }

    /**
     * Converts this outcome into the verdict the render policy consumes.
     *
     * A blocker that exists only because detection failed maps back to [AnimationVerdict.Indeterminate]
     * so a retry can re-detect, rather than being cached as a permanent "not animated" decision.
     */
    fun verdictFor(blocker: SlicingBlocker, formatSupportsAnimation: Boolean): AnimationVerdict = when {
        blocker == SlicingBlocker.ANIMATION_DETECTION_FAILED -> AnimationVerdict.Indeterminate
        blocker == SlicingBlocker.ANIMATED_CONTENT -> AnimationVerdict.Detected(true)
        formatSupportsAnimation -> AnimationVerdict.Indeterminate
        else -> AnimationVerdict.Detected(false)
    }

    /**
     * The verdict implied by a byte probe, or by the absence of one.
     *
     * Three outcomes, and the middle one is the whole point:
     *
     *  - the probe answered, so that is the verdict;
     *  - the probe did not answer *and the format cannot hold animation*, so the page is
     *    statically known to be static. That is an **answer**, not an absence of one, and it is
     *    why this is not simply `detected?.let { Detected(it) } ?: Indeterminate` -- which would
     *    refuse to slice every PNG and JPEG on the grounds that nobody checked.
     *  - the probe did not answer and the format could have held animation, which is the only
     *    genuinely unknown case, and the one that must block.
     *
     * Coercing that last case to "static" is what sliced an animated page down to its first
     * frame: the strip looked like a loading bug rather than a classification failure.
     *
     * Distinct from [verdictFor], which starts from a [SlicingBlocker] rather than a probe. That
     * one reports `Indeterminate` for a blocker-free page whose format could animate, which is the
     * right answer when the blocker is the only evidence available and the wrong one here, where
     * the probe has already spoken.
     */
    fun verdictFromProbe(detectedAnimated: Boolean?, format: PageImageFormat): AnimationVerdict = when {
        detectedAnimated != null -> AnimationVerdict.Detected(detectedAnimated)
        !canHoldAnimation(format) -> AnimationVerdict.Detected(false)
        else -> AnimationVerdict.Indeterminate
    }

    /**
     * Whether a format is even capable of holding animation.
     *
     * A format that cannot animate is statically known to be static, so it does not need byte
     * inspection. This is what lets a PNG take the sliced path without a detection pass.
     */
    fun canHoldAnimation(format: PageImageFormat): Boolean = when (format) {
        PageImageFormat.WEBP,
        PageImageFormat.GIF,
        PageImageFormat.ANIMATED,
        PageImageFormat.UNSUPPORTED,
        -> true

        PageImageFormat.JPEG,
        PageImageFormat.PNG,
        PageImageFormat.JXL,
        -> false
    }
}

/**
 * Whether a format supports region decoding.
 *
 * Beside [AnimationPolicy.canHoldAnimation] because it is the same question asked of the other
 * axis: a format can be static and still not region-decodable (progressive JPEG), animated and
 * region-decodable in principle (WebP), or neither (JXL). The reader needs both answers and must
 * not infer one from the other.
 *
 * **Conservative by default.** Anything not known to support region decoding is reported as not
 * supporting it, so an unrecognised format takes the whole-image path rather than a region decode
 * that returns only the first frame.
 */
fun PageImageFormat.supportsRegionDecode(): Boolean = when (this) {
    // JXL has no region decoder in the platform, and the project's bridge does not expose one.
    PageImageFormat.JXL -> false
    // Not an image at all; there is nothing to region-decode.
    PageImageFormat.UNSUPPORTED -> false
    // WebP and GIF are region-decodable in principle, but an animated page is never sliced
    // regardless -- that is the animation axis, decided by [AnimationPolicy.canHoldAnimation] and
    // the byte probe, not here.
    PageImageFormat.JPEG,
    PageImageFormat.PNG,
    PageImageFormat.WEBP,
    PageImageFormat.GIF,
    PageImageFormat.ANIMATED,
    -> true
}
