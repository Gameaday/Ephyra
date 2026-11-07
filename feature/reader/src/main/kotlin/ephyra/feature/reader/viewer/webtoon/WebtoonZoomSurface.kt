package ephyra.feature.reader.viewer.webtoon

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import ephyra.domain.reader.gesture.ReaderGestureEffect

//
// The continuous reader's zoom surface, extracted from `ComposeWebtoonReader` (A4, `RDR-005`).
//
// Two pieces live here, and both existed inline before: the **one container transform** that
// implements `DEF-003` (a per-item transform makes a scaled item paint outside its `LazyColumn`
// slot while the list retains the unscaled layout, so neighbours collide; one transform on the
// scroll container cannot disagree with layout), and the **chapter-scoped gesture host** that
// turns arbiter effects into transform changes.
//
// Extraction is not cosmetic. `DEF-002` was `CODE_COMPLETE` at `E3` but its zoom path was wired
// inline inside the reader composition, which the 2026-09-26 Phase-7 entry recorded as "no
// hostable path for gesture injection" — a defect fix that could not be re-driven without the
// whole reader standing up. With the pieces here, `WebtoonZoomInjectionTest` drives a real
// two-pointer pinch through the production `webtoonGestureStream` + host at `E2` (Robolectric
// pointer injection, per `B-022`), so the wiring is guardable on every CI run rather than only
// on a device.
//
// ## The fix this carries, not just the move
//
// The reader used to call `reduceWebtoonEffect(effect, committed = null)` — always `null` — so
// the reducer's `GestureCancelled` branch (`restoreCommitted`) was a **no-op in production**:
// an abandoned swipe left the strip wherever the finger last reached, exactly the behaviour
// `WebtoonGestureAdapter`'s contract says was fixed. The host holds the committed transform, so a
// cancelled gesture now actually restores it. That is a behaviour change and is disclosed as such
// in the ledger; it is the behaviour the existing contract already claimed.
//

/**
 * The document transform for the whole chapter, applied once to the scroll container.
 *
 * The transform origin is top-start so the list's own scroll offset stays meaningful in document
 * units, and `clip` keeps the scaled result inside the viewport. Reading `WebtoonZoomState.scale`
 * inside the `graphicsLayer` block means a gesture invalidates the layer, not the layout: item
 * heights and scroll position are untouched by zoom, which is the `DEF-003` guarantee.
 */
fun Modifier.webtoonDocumentTransform(zoomState: WebtoonZoomState): Modifier = graphicsLayer {
    scaleX = zoomState.scale
    scaleY = zoomState.scale
    translationX = zoomState.offsetX
    transformOrigin = TransformOrigin(0f, 0f)
    clip = true
}

/**
 * Chapter-scoped owner of the continuous reader's gesture outcomes.
 *
 * One host per chapter, not one per item: the committed transform is a property of a *gesture*
 * (`WebtoonGestureReducer`'s `CommittedTransform` documentation), so it must survive items
 * scrolling in and out of composition and must not be overwritten by a second item's stream.
 */
@Stable
class WebtoonZoomGestureHost(
    /** The chapter's document zoom state, shared by every strip. */
    val zoomState: WebtoonZoomState,
    /** The scroll owner's receiver for the focal correction; the list owns vertical position. */
    private val onZoomFocal: (Float) -> Unit,
) {
    /**
     * The transform an in-flight gesture restores to if it is abandoned. Held here rather than in
     * [WebtoonZoomState] per `WebtoonGestureReducer`'s contract, and updated on
     * `TransformCommitted` — the effect the reader previously received and dropped, which is
     * what made cancellation a no-op.
     */
    private var committed: CommittedTransform? = null

    /**
     * Reduces one arbiter effect and routes its non-transform halves.
     *
     * Ordering mirrors the inline wiring this replaces: transform first (so a correction reaches
     * the list owner on the same effect that changed the transform), then the item-level tap and
     * long-press pass-through. The commit is recorded *before* the reduce, so a
     * `GestureCancelled` arriving after a commit on the same gesture still sees the value it
     * must restore to.
     */
    fun handleEffect(
        effect: ReaderGestureEffect,
        itemSize: () -> IntSize,
        onSingleTap: (Offset, Size) -> Unit,
        onLongTap: () -> Unit,
        onDoubleTap: () -> Unit = {},
    ) {
        if (effect is ReaderGestureEffect.TransformCommitted) {
            committed = CommittedTransform(zoomState.scale, zoomState.offsetX)
        }
        when (val outcome = zoomState.reduceWebtoonEffect(effect, committed, onDoubleTap)) {
            is WebtoonGestureOutcome.TransformChanged -> onZoomFocal(outcome.scrollCorrection)
            // Delegation is achieved by the adapter *not* consuming, so the LazyColumn scrolls on
            // its own. Nothing is applied here, and that is the point: this branch is reachable
            // only because the arbiter runs on this surface.
            WebtoonGestureOutcome.DelegatedToParent,
            WebtoonGestureOutcome.NoTransformChange,
            -> Unit
        }
        when (effect) {
            is ReaderGestureEffect.SingleTap -> onSingleTap(Offset(effect.x, effect.y), itemSize().toSize())
            ReaderGestureEffect.LongPress -> onLongTap()
            else -> Unit
        }
    }
}
