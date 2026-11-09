package ephyra.domain.reader.pager

import kotlin.math.abs

/**
 * Decides whether a page is a short "stub" strip that smart merge should absorb into a neighbour.
 *
 * **Why this is a pure policy rather than overloads on `ImageUtil`.** The stub bounds were declared
 * in three places that could drift independently: `ReaderPreferences`' defaults (`0.02f`/`0.30f`),
 * `ImageUtil`'s private `STUB_MIN/MAX_HEIGHT_FRACTION` constants (the same numbers), and `Downloader`'s
 * hardcoded literals. The old floor of 2 % was itself the defect the owner reported on 2026-11-09: a
 * 1-pixel filler strip — shorter than 2 % of the page — was classified as a *real* page and
 * published on its own, so the reader showed a blank flicker "page" between merged pairs. The fix is
 * not a smaller constant in one of the three places but a single owner of the rule: the floor is now
 * 0 % (a same-width strip of any height, down to 1 px, is a stub), and the ceiling stays an
 * exclusive 30 % so a genuinely short page (a tall panel, a two-panel strip) is never absorbed.
 *
 * **Why the width tolerance is inclusive and the height ceiling exclusive.** Scanlators emit
 * watermark strips at exactly the page width, but re-encodes shave a few pixels; a 5 % inclusive
 * tolerance absorbs those without absorbing a half-width page. The ceiling is exclusive because a
 * page *at* 30 % of the reference height is more likely a real short page than a strip — where the
 * rule is uncertain, it must not merge.
 *
 * **Why callers pass the anchor's original dimensions, not the merged bitmap's.** Both call sites
 * used to re-measure every chained candidate against the *growing* merged height, so a chain of
 * strip-full-strip-strip eventually compared a later page against an image several times taller than
 * the original page — and a real page under 30 % of the merged image could still be absorbed. A
 * candidate is a stub relative to what it was scanned beside, not relative to what the merge has so
 * far produced; that reference choice lives in the callers, this policy just refuses to guess it.
 */
object SmartMergePolicy {

    /** Inclusive width tolerance: candidate and reference widths within 5 % are "same width". */
    const val WIDTH_TOLERANCE_FRACTION = 0.05f

    /**
     * Default minimum height fraction (inclusive). 0 % by owner directive 2026-11-09: even a 1 px
     * filler strip is a stub. The previous 2 % floor classified those strips as real pages.
     */
    const val DEFAULT_MIN_HEIGHT_FRACTION = 0f

    /**
     * Default maximum height fraction (exclusive): a page at or above 30 % of the reference height
     * is treated as real content and never absorbed.
     */
    const val DEFAULT_MAX_HEIGHT_FRACTION = 0.30f

    /**
     * @param candidateWidth width of the page being classified.
     * @param candidateHeight height of the page being classified.
     * @param referenceWidth width of the page the candidate would merge with — the anchor's
     *        *original* width, never a merged bitmap's.
     * @param referenceHeight height of the reference page.
     * @param minHeightFraction per-series floor, inclusive; defaults to the owner-directed 0 %.
     * @param maxHeightFraction per-series ceiling, exclusive; defaults to 30 %.
     * @return true when the candidate is a stub strip of the reference page.
     */
    fun isStub(
        candidateWidth: Int,
        candidateHeight: Int,
        referenceWidth: Int,
        referenceHeight: Int,
        minHeightFraction: Float = DEFAULT_MIN_HEIGHT_FRACTION,
        maxHeightFraction: Float = DEFAULT_MAX_HEIGHT_FRACTION,
    ): Boolean {
        // Fails closed on dimensions the header probe could not read. A page of unknown size is a
        // real page until proven otherwise: absorbing it would destroy content the classifier never
        // saw.
        if (candidateWidth <= 0 || candidateHeight <= 0 || referenceWidth <= 0 || referenceHeight <= 0) {
            return false
        }
        // Fails closed on inverted knobs. A mis-stored per-series preference must disable merging,
        // not invert the rule into "absorb everything".
        if (minHeightFraction >= maxHeightFraction) {
            return false
        }
        val widthSimilar = abs(candidateWidth - referenceWidth) <= referenceWidth * WIDTH_TOLERANCE_FRACTION
        if (!widthSimilar) return false
        val heightFraction = candidateHeight.toFloat() / referenceHeight.toFloat()
        return heightFraction >= minHeightFraction && heightFraction < maxHeightFraction
    }
}
