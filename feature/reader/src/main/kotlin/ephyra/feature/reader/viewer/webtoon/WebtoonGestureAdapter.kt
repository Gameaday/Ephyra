package ephyra.feature.reader.viewer.webtoon

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import ephyra.domain.reader.gesture.ReaderGestureConfig
import ephyra.domain.reader.gesture.ReaderGestureEffect
import ephyra.domain.reader.gesture.ReaderViewportMode
import ephyra.feature.reader.viewer.gesture.detectReaderGestures
import ephyra.feature.reader.viewer.zoom.ZoomPolicy

/*
 * Binds the continuous reader's pointer stream to [ephyra.domain.reader.gesture.ReaderGestureArbiter].
 *
 * ## Why this file exists
 *
 * `RDR-003` -- one gesture arbiter per viewport -- was `CODE_COMPLETE` and production-wired for the
 * **paged** reader only. The continuous reader kept its own hand-rolled `detectWebtoonGestures`,
 * which reimplemented tap sequencing, long-press timing, pan/zoom arbitration and the
 * delegate-to-parent rule inline. That is the same condition non-negotiable rule 7 exists to
 * prevent: two active gesture architectures in one reader, which can disagree about who owns a
 * gesture.
 *
 * The cost was not theoretical. `DelegateSingleScroll` -- the arbiter's only *negative* decision,
 * "I decline this, the parent should scroll" -- had no consumer on this surface, so the continuous
 * viewport could not learn it had declined. `B-023` found exactly this in the pager adapter, where
 * the effect was forwarded to the owner but the owner never received it; here it was never produced
 * at all, because nothing ran the arbiter.
 *
 * ## What the arbiter's CONTINUOUS mode changes
 *
 * [ReaderViewportMode.CONTINUOUS] makes `viewportOwnsPan` require `abs(panX) > abs(panY)`. That is
 * the rule this surface needs, and the old detector approximated it with
 * `shouldClaimWebtoonHorizontalPan`: a vertical drag belongs to the `LazyColumn`, a horizontal drag
 * belongs to the viewport, and a tie goes to the list. Encoding it in the arbiter rather than in the
 * adapter is what makes the two readers agree by construction.
 *
 * ## Behaviour is preserved, and the differences are the point
 *
 * The swap is behaviour-preserving where the old detector was already correct, and different only
 * where the arbiter is strictly better:
 *
 * - **Double-tap timing** now comes from `viewConfiguration.doubleTapTimeoutMillis` and the tap
 *   sequencer, rather than a hard-coded `350L` plus a second hand-rolled `isWebtoonDoubleTap`. Two
 *   copies of a timing rule is how `DEF-020`/`DEF-021` drifted.
 * - **Transform start** requires the arbiter's own phase transition, so a pinch can no longer be
 *   claimed and then abandoned mid-gesture; `TransformCommitted`/`GestureCancelled` now reach the
 *   viewport, so a cancelled swipe restores the committed transform instead of leaving the strip at
 *   the offset the finger last reached.
 */

/**
 * The gesture configuration for a continuous viewport.
 *
 * [meaningfullyZoomed] is derived from the *rendered* scale via [ZoomPolicy.locksInteraction]
 * rather than a remembered flag, so the horizontal-pan lock cannot drift from what is on screen.
 * Those two being out of step is how a reader ends up refusing to pan while visibly zoomed.
 */
fun webtoonGestureConfig(
    getScale: () -> Float,
    touchSlop: Float,
): ReaderGestureConfig = ReaderGestureConfig(
    viewportMode = ReaderViewportMode.CONTINUOUS,
    touchSlop = touchSlop,
    meaningfullyZoomed = ZoomPolicy.locksInteraction(getScale()),
)

/**
 * Attaches the arbiter to a pointer stream for a continuous viewport.
 *
 * [documentRevision] identifies the document the gesture belongs to. When it changes mid-gesture --
 * a chapter transition, or a different strip scrolling in -- the arbiter's revision check discards
 * the in-flight state instead of applying a transform computed against a document that no longer
 * exists. The old detector had no such notion and could zoom a strip that had already been
 * replaced.
 *
 * Events are observed at [androidx.compose.ui.input.pointer.PointerEventPass.Initial] so the arbiter
 * sees a second pointer before the `LazyColumn` can consume the pinch as a vertical drag. The
 * parent is not blocked from scrolling: delegation is achieved by *not* consuming, which is exactly
 * what [ReaderGestureEffect.DelegateSingleScroll] means.
 */
fun Modifier.webtoonGestureStream(
    documentRevision: () -> String,
    getScale: () -> Float,
    enabled: Boolean = true,
    onEffect: (ReaderGestureEffect) -> Unit,
): Modifier = pointerInput(Unit) {
    if (!enabled) return@pointerInput
    detectReaderGestures(
        documentRevision = documentRevision,
        config = { webtoonGestureConfig(getScale, viewConfiguration.touchSlop) },
        onEffect = onEffect,
    )
}
