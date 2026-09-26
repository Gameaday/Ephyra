package ephyra.feature.reader.viewer.pager

/*
 * Binds [PagerViewportState] to Compose: a rendered transform, and a pointer stream in.
 *
 * Nothing here is wired to the shipping reader yet. `ZoomableMangaPage` keeps its own detector until
 * the replacement viewport owns the gesture; landing both at once would mean two active reader
 * architectures, which `ROADMAP.md` non-negotiable rule 7 forbids.
 */
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import ephyra.domain.reader.gesture.ReaderGestureConfig
import ephyra.domain.reader.gesture.ReaderGestureEffect
import ephyra.domain.reader.viewport.PagerViewport
import ephyra.domain.reader.viewport.PagerViewportState
import ephyra.domain.reader.viewport.PagerZoomTransform
import ephyra.domain.reader.viewport.ViewportSize
import ephyra.feature.reader.viewer.gesture.detectReaderGestures

/**
 * Applies [transform] as a clipped, centre-origin graphics layer.
 *
 * This is the adapter step of `RDR-004`, and the seam where `DEF-001` actually happened. That defect
 * was not a maths error — the pinch arithmetic was correct — it was that the computed transform was
 * never attached to any modifier, so state changed and nothing drew. **No pure policy test can catch
 * that class of defect**, because the policy was never wrong. The transform is therefore applied here,
 * in one place, and effect handling lives in `PagerViewportState.reduceEffect` rather than in a
 * composable, so a reviewer can confirm in one place that the rendered value and the computed value
 * are the same expression.
 *
 * `transformOrigin = Center` is not a default to assume: [PagerZoomPolicy] inverts the focal point
 * about the viewport centre, so a different origin would silently misplace the zoom anchor while every
 * arithmetic test still passed.
 *
 * `clip = true` bounds the painted result to the page box, so a zoomed page is cropped by the viewport
 * rather than drawing over its pager neighbours.
 */
fun Modifier.pagerZoomLayer(transform: PagerZoomTransform): Modifier = graphicsLayer {
    scaleX = transform.scale
    scaleY = transform.scale
    translationX = transform.offsetX
    translationY = transform.offsetY
    transformOrigin = TransformOrigin.Center
    clip = true
}

/** Records the measured viewport size so zoom arithmetic has a real size to work in. */
fun Modifier.onPagerViewportMeasured(onMeasured: (ViewportSize) -> Unit): Modifier =
    onSizeChanged { size ->
        onMeasured(ViewportSize(size.width.toFloat(), size.height.toFloat()))
    }

/**
 * The gesture configuration for a pager viewport.
 *
 * [meaningfullyZoomed] is derived from the *rendered* scale rather than a remembered flag, so the
 * pager's scroll-enable cannot drift from what is on screen. Those two being out of step is how a
 * reader ends up with paging disabled while visibly at fit.
 */
fun pagerGestureConfig(
    state: PagerViewportState,
    touchSlop: Float,
    zoomLockThreshold: Float,
): ReaderGestureConfig = ReaderGestureConfig(
    viewportMode = ephyra.domain.reader.gesture.ReaderViewportMode.PAGED,
    touchSlop = touchSlop,
    meaningfullyZoomed = PagerViewport.ownsPan(zoomLockThreshold, state.transform.scale),
)

/**
 * Attaches the arbiter to a pointer stream for a paged viewport.
 *
 * The caller receives every effect and decides what to do with the non-transform ones. This only
 * guarantees the stream is observed before parent consumers, which is the ownership rule in
 * `READER_GESTURE_CONTRACT.md`.
 */
fun Modifier.pagerGestureStream(
    documentRevision: () -> String,
    config: () -> ReaderGestureConfig,
    onEffect: (ReaderGestureEffect) -> Unit,
): Modifier = pointerInput(Unit) {
    detectReaderGestures(
        documentRevision = documentRevision,
        config = config,
        onEffect = onEffect,
    )
}
