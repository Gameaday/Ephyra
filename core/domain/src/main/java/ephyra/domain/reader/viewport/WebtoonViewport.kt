package ephyra.domain.reader.viewport

/**
 * Transient viewport state for the continuous (webtoon) reader, owned by the viewport.
 *
 * [DocumentViewport] is a complete pure model — pan, focal zoom, clamp, prefetch, resize — and it is
 * the intended replacement for the shipping continuous reader. It also had **no production consumer
 * at all** when this was written, which is the decay risk the programme flagged: a contract that
 * keeps passing its own tests while diverging from the behaviour it was written to replace.
 *
 * The gap is lifecycle. [DocumentViewport] is a value describing a position; it does not say *when*
 * it should change, what invalidates it, or what a cancelled gesture restores. Without that, a caller
 * re-derives the same decisions at each use site — which is how the paged reader ended up computing
 * its zoom lock three separate ways.
 *
 * A page or chapter change is the important invalidation. A continuous document is identified by
 * `chapterId`, and a transform is only meaningful against the document it was computed for. Carrying
 * a stale transform into a new chapter would render the new chapter pre-zoomed and pre-panned, so
 * [WebtoonViewport.forChapter] is the only way to build the initial state.
 *
 * **This is state, not policy.** [DocumentViewport] owns the arithmetic and this owns only the
 * lifecycle around it, exactly as [PagerViewportState] does for the paged case.
 */
data class WebtoonViewportState(
    /** The chapter this transform is valid for. A transform never crosses a chapter boundary. */
    val chapterId: String,
    /** The document viewport, or `null` until a document has been supplied. */
    val viewport: DocumentViewport? = null,
    /** `true` while a transform gesture owns the pointer stream. */
    val transforming: Boolean = false,
) {
    init {
        require(chapterId.isNotBlank()) { "chapterId must not be blank" }
    }

    /** `true` once a document has been attached; zoom arithmetic needs a real document. */
    val isMeasured: Boolean get() = viewport != null

    /** Current scale, or `1f` before a document exists. */
    val scale: Float get() = viewport?.scale ?: 1f

    /** `true` at fit scale, where vertical scrolling belongs to the list rather than the viewport. */
    val isAtFit: Boolean get() = viewport?.let { it.scale <= it.minScale } ?: true
}

/**
 * The `RDR-005` state owner for the continuous reader.
 *
 * Pure functions only, mirroring [PagerViewport] so the two readers have the same shape and a reader
 * familiar with one recognises the other. The continuous case differs in one way that matters: the
 * **vertical axis is not the viewport's to claim**. A webtoon is a long strip scrolled vertically, so
 * a vertical drag belongs to the list even when zoomed. The gesture arbiter already encodes that via
 * [ephyra.domain.reader.gesture.ReaderViewportMode.CONTINUOUS], and this type does not re-decide it.
 */
object WebtoonViewport {
    /**
     * Attaches a document, starting at fit and at the document origin.
     *
     * A new chapter always starts at fit rather than inheriting the previous chapter's transform.
     * Preserving it would open a new chapter pre-zoomed, which reads as a rendering bug.
     */
    fun forChapter(
        chapterId: String,
        document: ReaderDocument,
        viewport: ViewportSize,
        minScale: Float = 1f,
        maxScale: Float = 8f,
    ): WebtoonViewportState = WebtoonViewportState(
        chapterId = chapterId,
        viewport = DocumentViewport(
            document = document,
            viewport = viewport,
            scale = 1f,
            minScale = minScale,
            maxScale = maxScale,
        ).clamped(),
    )

    /**
     * Applies a pinch.
     *
     * Returns [state] unchanged when no document is attached rather than substituting a placeholder
     * size. A fabricated viewport would produce a focal anchor wrong by an amount only visible on real
     * hardware.
     */
    fun onZoom(
        state: WebtoonViewportState,
        factor: Float,
        focalX: Float,
        focalY: Float,
    ): WebtoonViewportState {
        val current = state.viewport ?: return state
        return state.copy(viewport = current.zoomBy(factor, DocumentPoint(focalX, focalY)))
    }

    /** Applies a pan in view units, so a drag tracks the finger regardless of current scale. */
    fun onPan(state: WebtoonViewportState, dxView: Float, dyView: Float): WebtoonViewportState {
        val current = state.viewport ?: return state
        return state.copy(viewport = current.panByViewDelta(dxView, dyView))
    }

    /** Toggles between fit and a documented zoom level, holding the visible centre. */
    fun onToggleFit(
        state: WebtoonViewportState,
        zoomLevel: Float = 2f,
    ): WebtoonViewportState {
        val current = state.viewport ?: return state
        val next =
            if (state.isAtFit) current.copy(scale = zoomLevel).clamped() else current.zoomToFit()
        return state.copy(viewport = next)
    }

    /**
     * Records a new viewport size, holding the visible centre.
     *
     * Rotation must not lose the reader's place. [DocumentViewport.resizedTo] preserves the centre and
     * re-clamps, which is why this delegates rather than re-deriving the offset.
     */
    fun onViewportResized(
        state: WebtoonViewportState,
        viewport: ViewportSize,
    ): WebtoonViewportState {
        val current = state.viewport ?: return state
        return state.copy(viewport = current.resizedTo(viewport))
    }

    /**
     * Applies a transform from a gesture effect, scoped to the chapter that produced it.
     *
     * The effect's `documentRevision` is checked against the state's chapter, and a mismatch is
     * discarded rather than applied: a transform computed for a previous chapter would render the new
     * chapter at the old chapter's position. This is what makes the chapter boundary non-negotiable
     * rather than advisory, and it is the guard `DEF-002`/`DEF-003` would depend on at cutover.
     */
    fun onTransformEffect(
        state: WebtoonViewportState,
        documentRevision: String,
        factor: Float,
        panX: Float,
        panY: Float,
        focalX: Float,
        focalY: Float,
    ): WebtoonViewportState {
        if (documentRevision != state.chapterId) return state
        val zoomed = onZoom(state, factor, focalX, focalY)
        return if (panX != 0f || panY != 0f) onPan(zoomed, panX, panY) else zoomed
    }
}
