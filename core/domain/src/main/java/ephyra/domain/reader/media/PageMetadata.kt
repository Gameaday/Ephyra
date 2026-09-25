package ephyra.domain.reader.media

/** Container/codec families the reader must plan for. */
enum class PageImageFormat {
    JPEG,
    PNG,

    /** Static or animated; the bytes do not say which. See [PageMetadata.animation]. */
    WEBP,
    GIF,

    /** Animated GIF/WebP content that the platform decodes frame-by-frame. */
    ANIMATED,

    /** JPEG XL, only available through the project bridge decoder. */
    JXL,

    /** Bytes that are not a recognised image. */
    UNSUPPORTED,
}

/**
 * Whether a page holds animation, which changes what the reader may do to it.
 *
 * This is a property of the bytes, not of the source's URL extension: a `.webp` can be either.
 * It is decided once, at planning time, because the answer changes which operations are legal.
 * Slicing or cropping an animated page is not a matter of doing the same work more slowly — it
 * would require re-encoding every frame, so those pages take a whole-image path instead.
 */
enum class AnimationKind {
    /** A single still image. Supports slicing, cropping, and tiled decode. */
    STATIC,

    /** Multiple frames. Must be presented whole; slicing is not available. */
    ANIMATED,
}

/** A rectangle in intrinsic image pixel coordinates. */
data class PixelRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(right > left && bottom > top) { "pixel rect must have positive extent" }
        require(left >= 0 && top >= 0) { "pixel rect must not start outside the image" }
    }

    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * What is known about a page before it is decoded.
 *
 * The distinction this type enforces is between *intrinsic* size and *content* size. Intrinsic is
 * the encoded pixel extent. Content is the part that is actually artwork once a uniform border is
 * trimmed. Laying out against intrinsic size while cropping against content size is exactly what
 * makes a cropped page appear to change size on load; both numbers have to travel together.
 *
 * [contentRect] is null until cropping has been measured. A null content rect means "the whole
 * image", and must never be silently treated as "no content".
 */
data class PageMetadata(
    val intrinsicSize: PixelSize,
    val animation: AnimationKind,
    val format: PageImageFormat,
    val contentRect: PixelRect? = null,
) {
    init {
        require(intrinsicSize.width > 0 && intrinsicSize.height > 0) { "intrinsic size must be positive" }
    }

    /**
     * Size after cropping, or the intrinsic size when nothing was trimmed.
     *
     * Every layout decision uses this, so that turning crop on does not move the page geometry.
     */
    val displaySize: PixelSize
        get() = contentRect?.let { PixelSize(it.width, it.height) } ?: intrinsicSize

    /** True when cropping measurably changed the image. */
    val isCropped: Boolean get() = contentRect != null

    /**
     * True when a viewport may split this page into independently decoded tiles.
     *
     * Animation is the only blocker: tiles would have to be re-encoded per frame. Unknown or
     * unsupported formats are still sliceable in principle, so they are not blocked here — a
     * decode failure is handled by falling back to a whole-image request.
     */
    val supportsSlicing: Boolean get() = animation == AnimationKind.STATIC
}

/** Intrinsic pixel size. */
data class PixelSize(val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0) { "pixel size must be positive" }
    }

    val aspectRatio: Float get() = width.toFloat() / height.toFloat()
}
