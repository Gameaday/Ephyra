package ephyra.domain.reader.media

/**
 * Why a page cannot be presented as independently decoded slices.
 *
 * Each case names a distinct failure so the fallback is explainable rather than a generic
 * "unsupported". The distinction matters because only [NONE] is a property of the bytes; the rest
 * are properties of the current configuration and must stop blocking as soon as that changes.
 */
enum class SlicingBlocker {
    /** Slicing is available. */
    NONE,

    /**
     * The bytes hold animation.
     *
     * A slice would have to be re-encoded per frame, so this is a hard block that no setting
     * removes. It is kept distinct from [ANIMATION_DETECTION_FAILED] because a definitive answer
     * and a missing one must not be treated the same way.
     */
    ANIMATED_CONTENT,

    /**
     * The format cannot be region-decoded at all.
     *
     * Distinct from [ANIMATED_CONTENT] on purpose: a progressive JPEG and a JXL file are both
     * "not sliceable", but conflating them with animation reports a false reason and hides a
     * genuinely static page behind an animation label.
     */
    FORMAT_NOT_REGION_DECODABLE,

    /**
     * Animation could not be determined.
     *
     * Blocks slicing because guessing wrong produces a strip that shows only its first frame, which
     * is worse than the single-image fallback. Treated as unknown, not as static.
     */
    ANIMATION_DETECTION_FAILED,

    /** Crop-borders measurement is still in flight, so slice geometry is not yet known. */
    CROP_UNDECIDED,

    /** No bytes are available to slice from; a fetch has to happen first. */
    NO_BYTES,

    /** Intrinsic dimensions are unknown, so slice geometry cannot be derived. */
    UNKNOWN_SIZE,

    /** The page is already resident as one merged bitmap; there is nothing to slice. */
    ALREADY_MERGED,
}

/** The render path a page will actually take. */
enum class PageRenderPath {
    /** Independently decoded slices, in document order. */
    SLICED,

    /** The page already has one merged bitmap and it is used as-is. */
    MERGED_BITMAP,

    /** One whole-image decode, after any crop transform. */
    WHOLE_IMAGE,
}

/** What is known about animation detection for a specific page. */
sealed interface AnimationVerdict {
    /** The bytes were inspected: [animated] is authoritative. */
    data class Detected(val animated: Boolean) : AnimationVerdict

    /**
     * Detection did not complete.
     *
     * Deliberately not a boolean. An unknown answer must not be coerced to "static", because a
     * static answer for an animated page silently drops every frame but the first.
     */
    data object Indeterminate : AnimationVerdict
}

/**
 * Decides the render path for a page.
 *
 * Ownership: this was previously an inline condition in the webtoon composable
 * (`!cropBorders && !animatedHint && ...`). Three consequences of that placement, all corrected
 * here:
 *
 *  1. **Crop disabled slicing outright.** Because the guard was `!cropBorders`, turning crop on
 *     switched a long strip to a single full-height decode. For a page taller than the texture
 *     limit that is exactly the decode that fails, so crop made tall strips worse. Crop changes
 *     which rectangle to slice, not whether slicing is allowed.
 *  2. **JXL was reported as animation.** The old check folded a JXL type test into the same flag
 *     as animated content, so a static JXL page was blocked with an animation reason.
 *  3. **It was unreachable from a test.** The condition could only be evaluated by a real
 *     composition with real bytes.
 *
 * The policy here is total: every combination yields a [PageRenderPath], and none of them can
 * produce a slice of animated content.
 */
object RenderPathPolicy {

    /**
     * Chooses the render path.
     *
     * @param verdict animation detection result; [AnimationVerdict.Indeterminate] blocks slicing.
     * @param regionDecodable whether the format supports region decoding at all.
     * @param contentRect measured crop bounds, or null when cropping is enabled but not yet decided.
     * @param cropRequested whether the user asked for crop-borders.
     * @param hasMergedBitmap whether a smart-combine merge is already resident.
     * @param hasBytes whether page bytes are available to slice from.
     * @param hasKnownSize whether intrinsic dimensions are known.
     */
    fun choose(
        verdict: AnimationVerdict,
        regionDecodable: Boolean,
        cropRequested: Boolean,
        contentRect: PixelRect?,
        hasMergedBitmap: Boolean,
        hasBytes: Boolean,
        hasKnownSize: Boolean,
    ): PageRenderPath {
        if (hasMergedBitmap) return PageRenderPath.MERGED_BITMAP
        val blocker = blockerFor(
            verdict = verdict,
            regionDecodable = regionDecodable,
            cropRequested = cropRequested,
            contentRect = contentRect,
            hasBytes = hasBytes,
            hasKnownSize = hasKnownSize,
        )
        return if (blocker == SlicingBlocker.NONE) {
            PageRenderPath.SLICED
        } else {
            PageRenderPath.WHOLE_IMAGE
        }
    }

    /** Why [choose] ruled out slicing, for logging and for tests. */
    fun blockerFor(
        verdict: AnimationVerdict,
        regionDecodable: Boolean,
        cropRequested: Boolean,
        contentRect: PixelRect?,
        hasBytes: Boolean,
        hasKnownSize: Boolean,
    ): SlicingBlocker = when {
        // A definitive animation answer outranks everything: it is a property of the bytes.
        verdict is AnimationVerdict.Detected && verdict.animated -> SlicingBlocker.ANIMATED_CONTENT
        !regionDecodable -> SlicingBlocker.FORMAT_NOT_REGION_DECODABLE
        verdict is AnimationVerdict.Indeterminate -> SlicingBlocker.ANIMATION_DETECTION_FAILED
        // Crop is requested but unmeasured. Slicing is not forbidden; the geometry is not known yet.
        cropRequested && contentRect == null -> SlicingBlocker.CROP_UNDECIDED
        !hasBytes -> SlicingBlocker.NO_BYTES
        !hasKnownSize -> SlicingBlocker.UNKNOWN_SIZE
        else -> SlicingBlocker.NONE
    }

    /**
     * The rectangle slices must be cut from, in intrinsic image coordinates.
     *
     * This is what makes slicing crop-aware: a cropped page is sliced from its content rect, not
     * from the full image, so the slices tile the visible artwork exactly. Returns the whole image
     * when nothing was cropped.
     */
    fun sliceSourceRect(intrinsic: PixelSize, contentRect: PixelRect?): PixelRect =
        contentRect ?: PixelRect(0, 0, intrinsic.width, intrinsic.height)

    /**
     * True when [sliceRect] lies fully inside the region slices are allowed to cover.
     *
     * Guards against a measured crop rect that is inconsistent with the declared intrinsic size,
     * which would otherwise produce a slice extending past the image and decoding as transparent.
     */
    fun isSliceRectValid(intrinsic: PixelSize, sliceRect: PixelRect): Boolean =
        sliceRect.right <= intrinsic.width &&
            sliceRect.bottom <= intrinsic.height &&
            sliceRect.left >= 0 &&
            sliceRect.top >= 0
}
