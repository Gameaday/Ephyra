package ephyra.domain.reader.viewport

import kotlin.math.max
import kotlin.math.min

/** Immutable size in a single declared coordinate space. */
data class DocumentSize(val width: Float, val height: Float) {
    init {
        require(width > 0f && height > 0f) { "document size must be positive" }
        require(width.isFinite() && height.isFinite()) { "document size must be finite" }
    }
}

/** Viewport extent in view units (device-independent units at scale 1). */
data class ViewportSize(val width: Float, val height: Float) {
    init {
        require(width > 0f && height > 0f) { "viewport size must be positive" }
        require(width.isFinite() && height.isFinite()) { "viewport size must be finite" }
    }
}

data class DocumentPoint(val x: Float, val y: Float)

/** Axis-aligned rectangle in a single declared coordinate space. */
data class DocumentRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(right > left && bottom > top) { "rect must have positive extent" }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun intersects(other: DocumentRect): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top

    fun contains(other: DocumentRect): Boolean =
        other.left >= left && other.right <= right && other.top >= top && other.bottom <= bottom
}

/** Logical document page/strip rectangle. Page rectangles never overlap and never reorder. */
data class DocumentPage(
    val index: Int,
    val pageId: String,
    val rect: DocumentRect,
) {
    init {
        require(index >= 0) { "page index must be non-negative" }
        require(pageId.isNotBlank()) { "pageId must not be blank" }
    }
}

/** The immutable document model: ordered pages inside one bounding rect. */
data class ReaderDocument(
    val documentId: String,
    val size: DocumentSize,
    val pages: List<DocumentPage>,
) {
    init {
        require(documentId.isNotBlank()) { "documentId must not be blank" }
    }
}

/**
 * Pure continuous-document viewport.
 *
 * The viewport is the single owner of the document transform. Pan and zoom are two operations on
 * the same transform, so they can never disagree the way per-item layouts do. Layout paints tiles
 * through [documentToView]; it never scales individual items.
 *
 * `offset` is the document-space coordinate of the viewport top-left corner. `scale` converts
 * document units into view units.
 */
data class DocumentViewport(
    val document: ReaderDocument,
    val viewport: ViewportSize,
    val offset: DocumentPoint = DocumentPoint(0f, 0f),
    val scale: Float = 1f,
    val minScale: Float = 1f,
    val maxScale: Float = 8f,
) {
    init {
        require(minScale > 0f && maxScale >= minScale) { "invalid scale bounds" }
        require(scale.isFinite() && scale > 0f) { "scale must be finite and positive" }
        require(offset.x.isFinite() && offset.y.isFinite()) { "offset must be finite" }
    }

    /** Scale at which the document fits the viewport width. */
    val fitScale: Float
        get() = (viewport.width / document.size.width).coerceIn(minScale, maxScale)

    /** Document rectangle currently visible in the viewport. */
    val visibleRect: DocumentRect
        get() = DocumentRect(
            left = offset.x,
            top = offset.y,
            right = offset.x + viewport.width / scale,
            bottom = offset.y + viewport.height / scale,
        )

    fun viewToDocument(point: DocumentPoint): DocumentPoint = DocumentPoint(
        x = offset.x + point.x / scale,
        y = offset.y + point.y / scale,
    )

    fun documentToView(point: DocumentPoint): DocumentPoint = DocumentPoint(
        x = (point.x - offset.x) * scale,
        y = (point.y - offset.y) * scale,
    )

    /** Pan by a view-space delta, then clamp. */
    fun panByViewDelta(dxView: Float, dyView: Float): DocumentViewport {
        val moved = copy(
            offset = DocumentPoint(offset.x + dxView / scale, offset.y + dyView / scale),
        )
        return moved.clamped()
    }

    /**
     * Zoom by [factor] while keeping the document point under [focalView] stable, then clamp.
     * Invariant: `documentToView(viewToDocument(focal)) == focal` whenever the clamp is inactive.
     */
    fun zoomBy(factor: Float, focalView: DocumentPoint): DocumentViewport {
        require(factor.isFinite() && factor > 0f) { "zoom factor must be finite and positive" }
        val anchor = viewToDocument(focalView)
        val nextScale = (scale * factor).coerceIn(minScale, maxScale)
        val nextOffset = DocumentPoint(
            x = anchor.x - focalView.x / nextScale,
            y = anchor.y - focalView.y / nextScale,
        )
        return copy(scale = nextScale, offset = nextOffset).clamped()
    }

    /** Resets scale to fit while holding the current visible centre. */
    fun zoomToFit(): DocumentViewport {
        val centreX = visibleRect.centerX
        val centreY = visibleRect.centerY
        val target = fitScale
        val nextOffset = DocumentPoint(
            x = centreX - viewport.width / (2f * target),
            y = centreY - viewport.height / (2f * target),
        )
        return copy(scale = target, offset = nextOffset).clamped()
    }

    /** Clamps scale into bounds and the visible rect inside the document. */
    fun clamped(): DocumentViewport {
        val boundedScale = scale.coerceIn(minScale, maxScale)
        val maxOffsetX = max(0f, document.size.width - viewport.width / boundedScale)
        val maxOffsetY = max(0f, document.size.height - viewport.height / boundedScale)
        return copy(
            scale = boundedScale,
            offset = DocumentPoint(
                x = offset.x.coerceIn(0f, maxOffsetX),
                y = offset.y.coerceIn(0f, maxOffsetY),
            ),
        )
    }

    /**
     * Rect that must stay requested: the visible rect expanded by [viewportMargin] extra viewports
     * per side, clipped to the document. The performance budget caps this at two viewports.
     */
    fun prefetchRect(viewportMargin: Float = 2f): DocumentRect {
        require(viewportMargin >= 0f) { "viewportMargin must be non-negative" }
        val spanX = viewport.width / scale
        val spanY = viewport.height / scale
        val marginX = spanX * viewportMargin
        val marginY = spanY * viewportMargin
        return DocumentRect(
            left = max(0f, offset.x - marginX),
            top = max(0f, offset.y - marginY),
            right = min(document.size.width, offset.x + spanX + marginX),
            bottom = min(document.size.height, offset.y + spanY + marginY),
        )
    }

    /** Pages intersecting the prefetch rect, in document order. */
    fun pagesInPrefetch(viewportMargin: Float = 2f): List<DocumentPage> {
        val rect = prefetchRect(viewportMargin)
        return document.pages.filter { it.rect.intersects(rect) }
    }

    /** Copies with a different viewport size, preserving the visible centre and scale. */
    fun resizedTo(viewport: ViewportSize): DocumentViewport {
        val centreX = visibleRect.centerX
        val centreY = visibleRect.centerY
        val nextOffset = DocumentPoint(
            x = centreX - viewport.width / (2f * scale),
            y = centreY - viewport.height / (2f * scale),
        )
        return copy(viewport = viewport, offset = nextOffset).clamped()
    }

    /** Pages intersecting the currently visible rect, in document order. */
    fun visiblePages(): List<DocumentPage> {
        val rect = visibleRect
        return document.pages.filter { it.rect.intersects(rect) }
    }
}
