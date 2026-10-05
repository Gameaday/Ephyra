package ephyra.presentation.core.ui.navigation

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/*
 * Predictive back, so gesture progress has **one** implementation instead of one per call site.
 *
 * ## Why this exists
 *
 * The app declared `android:enableOnBackInvokedCallback="true"` and used `BackHandler` in eleven
 * places, but had **no `PredictiveBackHandler` anywhere**. That combination is worse than having
 * neither: opting in means the system draws its own back-to-home and cross-activity animation, so
 * an in-app surface that intercepts back without participating in the gesture gets *no* preview at
 * all. The user swipes and the sheet simply vanishes at the end of the gesture, which is exactly
 * the outcome the gesture exists to remove.
 *
 * ## The three responsibilities, and the one that is usually wrong
 *
 * A progress handler must drive an animation from `BackEventCompat.progress` while the finger is
 * down, **commit** when the gesture completes, and **restore** when it is cancelled. The third is
 * where implementations usually go wrong: a cancelled gesture arrives as a `CancellationException`
 * and not as another progress event, so a handler that only collects progress leaves the surface
 * stranded at whatever offset the finger last reached — a half-dismissed sheet that never resolves.
 *
 * ## Interruption is deliberately a no-op
 *
 * When a gesture is cancelled because a *different* back handler took over — a dialog opened
 * mid-swipe, say — the correct behaviour is to leave the surface alone and let the new owner
 * decide the outcome. Animating back here would fight it. Callers whose position is owned by a
 * drag state should use [PredictiveBackDraggableProgress] instead, which needs no restore because
 * it writes real state rather than a transient animation value.
 */

/**
 * Runs [onProgress] as the back gesture advances, then [onCommit] when it completes.
 *
 * [onCancelled] runs when the gesture is abandoned — swiped back far enough to cancel, or taken
 * over by another handler. It defaults to *doing nothing*, which is the safe default for a surface
 * whose state is not owned by the gesture.
 *
 * [onCommit] is **not** called on cancellation, so a surface cannot both restore and commit.
 */
@Composable
fun PredictiveBackProgress(
    enabled: Boolean,
    onProgress: (progress: Float) -> Unit = {},
    onCommit: () -> Unit,
    onCancelled: () -> Unit = {},
) {
    val currentOnProgress by rememberUpdatedState(onProgress)
    val currentOnCommit by rememberUpdatedState(onCommit)
    val currentOnCancelled by rememberUpdatedState(onCancelled)

    PredictiveBackHandler(enabled = enabled) { flow ->
        try {
            flow.collect { event -> currentOnProgress(event.progress) }
            currentOnCommit()
        } catch (cancellation: CancellationException) {
            // Rethrown after the restore, never swallowed. Swallowing it leaves the dispatcher
            // believing this callback still consumes the gesture, so the *next* back press would be
            // routed here instead of to the handler that took over — the same defect class as
            // `ReaderGesturePointerAdapter` dropping `DelegateSingleScroll` (`B-023`), where the
            // gesture arbiter's decision never reached its consumer.
            currentOnCancelled()
            throw cancellation
        }
    }
}

/**
 * Drives an [AnchoredDraggableState] from the back gesture, so a sheet moves *with* the finger
 * instead of jumping at the end of it.
 *
 * The gesture writes the same state a drag writes, so there is exactly one owner of the sheet's
 * position whether the user swipes the surface or swipes back. That is what makes this correct
 * rather than merely animated: two independently animating copies of one position is precisely the
 * `DocumentViewport` / `LazyColumn` conflict in `B-025`, where two owners of one axis fight and the
 * content jumps.
 *
 * The animation is seeded from the state's *current* offset rather than a fixed 0-to-1, so a sheet
 * already partly dragged is not snapped to a hardcoded position when a gesture starts.
 * [onCommit] fires only if the gesture carried the sheet past [commitThreshold]; a short swipe
 * springs back, which is what a user who aborted the gesture expects.
 *
 * No restore is needed on interruption: the state holds a real offset that the drag modifier can
 * settle from, and whoever took the gesture now owns the outcome.
 */
@Composable
fun PredictiveBackDraggableProgress(
    state: AnchoredDraggableState<*>,
    enabled: Boolean = true,
    commitThreshold: Float = 0.5f,
    onCommit: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val currentOnCommit by rememberUpdatedState(onCommit)

    PredictiveBackHandler(enabled = enabled) { flow ->
        val startOffset = state.offset.takeIf { it.isFinite() } ?: 0f
        if (startOffset == 0f) {
            // The handler contract REQUIRES the progress flow to be collected, even
            // when there is nothing to drag: returning early here crashed with
            // "You must collect the progress flow" whenever a back swipe started
            // while the sheet sat fully settled (e.g. chapter filter/sort sheet on
            // the series page). Drain it and let the gesture pass through.
            flow.collect { }
            return@PredictiveBackHandler
        }
        var travelled = 0f
        try {
            flow.collect { event ->
                // `1 - progress`: the back gesture travels inward from the edge while the sheet
                // travels outward from the screen, so the two move in opposite directions.
                val target = backGestureOffset(startOffset, event.progress)
                scope.launch { state.dispatchRawDelta(target - travelled) }
                travelled = target
            }
            if (travelled <= startOffset * (1f - commitThreshold)) {
                currentOnCommit()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        }
    }
}

/**
 * The offset a back gesture produces for a surface anchored at [startOffset], as a fraction of the
 * distance travelled so far.
 *
 * Extracted so the mapping from gesture progress to surface offset is asserted directly rather
 * than only through a gesture nobody can produce in a unit test. `1 - progress` because the two
 * travel in opposite directions, and getting that backwards produces a sheet that slides *in* as
 * the user swipes back — a defect that looks like the gesture is simply inverted and is easy to
 * ship without noticing.
 */
internal fun backGestureOffset(startOffset: Float, progress: Float): Float = startOffset * (1f - progress)
