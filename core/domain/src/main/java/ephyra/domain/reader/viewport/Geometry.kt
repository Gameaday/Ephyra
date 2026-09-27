package ephyra.domain.reader.viewport

// Geometry shared by the reader viewports and the media contracts.
//
// These are values, not a rendering model. They were extracted from the retired
// `DocumentViewport.kt` (ADR-0010), which had the useful property that pan and zoom were two
// operations on one transform -- but that property belonged to the canvas viewport, which is
// retired.
//
// The extraction was driven by the compiler, not by taste. Deleting the viewport broke
// `PageImage.kt` and `PagerZoomPolicy.kt`, which is what proved these two types are shared rather
// than canvas-specific. The rest of that file's declarations -- `DocumentSize`, `DocumentPoint`,
// `ReaderDocument`, `DocumentPage`, and the `intersects`/`centerX`/`centerY` helpers -- had no
// consumer left once the viewport and the partition were gone, so they were deleted rather than
// retained. `ReaderDocument` in particular described an ordered page model only the retired
// viewport built, and keeping it would have been the unwired-contract problem in miniature: a
// plausible type with nothing obliged to construct it.
//
// `DocumentRect.contains` is retained because a rectangle type without containment invites every
// future caller to re-derive it.

/** Viewport extent in view units (device-independent units at scale 1). */
data class ViewportSize(val width: Float, val height: Float) {
    init {
        require(width > 0f && height > 0f) { "viewport size must be positive" }
        require(width.isFinite() && height.isFinite()) { "viewport size must be finite" }
    }
}

/** Axis-aligned rectangle in a single declared coordinate space. */
data class DocumentRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(right > left && bottom > top) { "rect must have positive extent" }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun contains(other: DocumentRect): Boolean =
        other.left >= left && other.right <= right && other.top >= top && other.bottom <= bottom
}
