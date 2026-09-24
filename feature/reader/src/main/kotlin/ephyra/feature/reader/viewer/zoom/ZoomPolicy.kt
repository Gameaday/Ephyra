package ephyra.feature.reader.viewer.zoom

/**
 * Shared zoom policy for both the webtoon and the pager (classic) readers.
 *
 * Centralizes the threshold that decides whether a page is *genuinely* zoomed
 * enough to hijack single-touch input — pan-while-zoomed, disable paging between
 * pages, and suppress long-press. Keeping this in one place means the webtoon
 * ([ephyra.feature.reader.viewer.webtoon]) and the pager
 * ([ephyra.feature.reader.viewer.pager]) readers can never drift apart on the
 * lock-in behaviour that previously trapped users.
 *
 * Why this exists: a 1.05x gate clipped a sub-visual pinch (the graphicsLayer
 * clips at >1.0x, so a 1.05–1.08x zoom was effectively invisible) yet crossed
 * the gate and made the detector swallow taps / disable page swipes, so the
 * reader felt stuck until an "unpinch" dragged the scale back down. Locking
 * interaction only above [INTERACTION_LOCK] — a visibly zoomed level — means
 * incidental / overshoot pinches never trap the user. A double-tap reset
 * (gated separately, at 1.05x, in each reader's toggle) remains the escape
 * hatch for any larger zoom.
 */
object ZoomPolicy {

    /** Scale at which a page is considered fit (unzoomed). */
    const val FIT: Float = 1f

    /**
     * Scale above which the page is visibly zoomed and it is safe to hijack
     * single-touch input. Raising this above the old 1.05f removes the invisible
     * lock-in band: a clipped, sub-visual pinch no longer disables taps/swipes.
     */
    const val INTERACTION_LOCK: Float = 1.1f

    /**
     * Scale above which a double-tap is treated as a reset-to-fit (escape hatch)
     * rather than a zoom-in. Matches the old 1.05x toggle gate: any zoom the eye
     * can't actually perceive should still be collapsible with a single tap.
     */
    const val ZOOM_GATE: Float = 1.05f

    /** `true` when [scale] represents a genuine zoom that should lock interaction. */
    fun locksInteraction(scale: Float): Boolean = scale > INTERACTION_LOCK
}
