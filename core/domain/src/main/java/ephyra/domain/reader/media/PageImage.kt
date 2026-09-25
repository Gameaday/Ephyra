package ephyra.domain.reader.media

import ephyra.domain.reader.viewport.DocumentRect

/**
 * Everything the reader knows about a page before it is decoded.
 *
 * This is the single description a viewport consumes. Bundling identity, source, metadata, and the
 * decode plan into one value is the point: the alternative is four loosely related fields that
 * drift apart, and a stale combination (new pixels, old crop rect) produces a wrong render that
 * no individual field looks wrong enough to catch.
 *
 * The bytes themselves are not here. [metadata] is what was *measured*, and it may be filled in
 * progressively; [PageMetadata.contentRect] being null simply means cropping has not been decided
 * yet, which is a different state from "decided not to crop".
 */
data class PageImage(
    val identity: PageSourceId,
    val source: PageSource,
    val metadata: PageMetadata,
    val plan: DecodePlan,
) {
    /**
     * Stable cache key for this page's decoded result.
     *
     * Delegates to the plan, which owns the decision about which fields affect the pixels, so the
     * key cannot drift out of sync with the plan that produced it.
     */
    fun memoryCacheKey(transformTag: String = DEFAULT_TRANSFORM_TAG): String =
        plan.cacheKey(identity, metadata, transformTag)

    /** Size the viewport must lay out against. Cropping must not move this. */
    val displaySize: PixelSize get() = metadata.displaySize

    /** True when a viewport may request independently decoded tiles for this page. */
    val supportsSlicing: Boolean get() = metadata.supportsSlicing

    /**
     * Builds a document-space rect for this page placed at [documentTop].
     *
     * Width always matches the document width, and the height is derived from the *display* aspect
     * ratio. Using intrinsic size here is what makes a page visibly resize when cropping is
     * toggled, so the display ratio is the only correct input.
     */
    fun toDocumentPage(index: Int, pageId: String, documentWidth: Float, documentTop: Float): DocumentPagePlacement {
        require(documentWidth > 0f) { "documentWidth must be positive" }
        val height = documentWidth / displaySize.aspectRatio
        return DocumentPagePlacement(
            index = index,
            pageId = pageId,
            rect = DocumentRect(
                left = 0f,
                top = documentTop,
                right = documentWidth,
                bottom = documentTop + height,
            ),
        )
    }

    companion object {
        const val DEFAULT_TRANSFORM_TAG: String = "none"
    }
}

/**
 * A page's rectangle inside a continuous document.
 *
 * Kept separate from the viewport's `DocumentPage` so media planning does not depend on the
 * viewport module's construction rules; the conversion is explicit and happens in one place.
 */
data class DocumentPagePlacement(
    val index: Int,
    val pageId: String,
    val rect: DocumentRect,
) {
    init {
        require(index >= 0) { "page index must be non-negative" }
        require(pageId.isNotBlank()) { "pageId must not be blank" }
    }
}
