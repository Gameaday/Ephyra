package ephyra.feature.reader.viewer.pager

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.ImageUtil
import ephyra.domain.reader.gesture.ReaderGestureEffect
import ephyra.domain.reader.viewport.PagerViewport
import ephyra.domain.reader.viewport.PagerViewportState
import ephyra.domain.reader.viewport.PagerZoomTransform
import ephyra.domain.reader.viewport.ViewportSize
import ephyra.feature.reader.model.ReaderPage
import ephyra.feature.reader.viewer.ReaderPageErrorView
import ephyra.feature.reader.viewer.ReaderPageLoadingView
import ephyra.feature.reader.viewer.pageImageErrorPainter
import ephyra.feature.reader.viewer.pageImagePlaceholderPainter
import ephyra.feature.reader.viewer.readerPageMemoryCacheKey
import ephyra.feature.reader.viewer.zoom.ZoomPolicy
import ephyra.presentation.core.data.coil.cropBorders
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayInputStream
import coil3.size.Size as CoilSize

@Composable
fun ZoomableMangaPage(
    page: ReaderPage,
    cropBorders: Boolean,
    onTap: (Offset, Size) -> Unit,
    onLongTap: () -> Unit,
    onScaleChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isNavigationTap: ((Offset, Size) -> Boolean)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by page.statusFlow.collectAsStateWithLifecycle()
    val progress by page.progressFlow.collectAsStateWithLifecycle()

    LaunchedEffect(page) {
        withIOContext { page.chapter.pageLoader?.loadPage(page) }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()
        val containerSize = remember(containerWidth, containerHeight) { Size(containerWidth, containerHeight) }
        val scaleAnim = remember { Animatable(1f) }
        val offsetAnim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
        val transformMutex = remember { Mutex() }

        val viewportSize = remember(containerWidth, containerHeight) {
            ViewportSize(containerWidth, containerHeight)
        }

        // RDR-004: the viewport state is the single owner of the rendered transform.
        //
        // The two `Animatable`s remain only as the *timing* mechanism for double-tap, which is
        // animated, and are mirrored into `viewportState` on every frame. They are deliberately not
        // the source of truth: reading the transform from them is what let the two disagree, which is
        // the `DEF-001` defect class. Pinch remains instant and double-tap remains a 300ms tween.
        //
        // The gesture source is `detectReaderGestures`; the legacy `detectPagerGestures` and
        // `shouldClaimPagerTransform` are deleted, so there is exactly one pointer path in this
        // reader.
        var viewportState by remember { mutableStateOf(PagerViewportState(viewportSize = viewportSize)) }

        // Mirror the animation driver into the owning state. Without this the two would disagree,
        // which is precisely the failure this refactor exists to remove.
        LaunchedEffect(scaleAnim, offsetAnim) {
            snapshotFlow { scaleAnim.value to offsetAnim.value }
                .collect { (scale, offset) ->
                    val next = PagerZoomTransform(scale, offset.x, offset.y)
                    viewportState = viewportState.copy(
                        transform = next,
                        transforming = scale > ZoomPolicy.INTERACTION_LOCK,
                    )
                }
        }

        LaunchedEffect(viewportState.transform) {
            onScaleChanged(viewportState.transform.scale)
        }

        // A page change invalidates the transform: a new page always starts at fit. The adapter
        // cancels any pending tap on its own when the document revision changes, so there is no
        // tap bookkeeping left here to reset.
        LaunchedEffect(page) {
            scaleAnim.snapTo(1f)
            offsetAnim.snapTo(Offset.Zero)
            viewportState = PagerViewportState(viewportSize = viewportSize)
        }

        // A rotation or resize re-clamps rather than leaving the page dragged out of bounds.
        LaunchedEffect(viewportSize) {
            val action = PagerViewport.onViewportSizeChanged(viewportState, viewportSize)
            if (action.changed) {
                scaleAnim.snapTo(action.transform.scale)
                offsetAnim.snapTo(Offset(action.transform.offsetX, action.transform.offsetY))
            }
        }

        // RDR-004 stage 2: the gesture arbiter now owns the pointer stream.
        //
        // `detectPagerGestures` is deleted rather than deprecated. Two live gesture paths in one
        // reader is exactly what ROADMAP.md non-negotiable rule 7 forbids.
        //
        // The mapping is behaviour-preserving. The legacy detector already deferred at-fit single
        // taps by `delay(350L)` and already guarded long-press with
        // `!transformStarted && !wasMultiTouch`, so the swap changes neither timing. What does
        // change, as improvements rather than regressions: a transform now requires a second
        // pointer, and `DelegateSingleScroll` reaches this consumer so the viewport learns it
        // declined.
        //
        // Tap *routing* stays here rather than moving into the arbiter because it is product
        // policy, not gesture recognition: a navigation-zone tap turns the page, a menu-zone tap
        // toggles the menu, a double-tap at fit zooms, and a double-tap while zoomed returns to fit.
        // Applies a transform effect. Split out so the `when` below stays a readable mapping from
        // effect to behaviour, and so `TransformStarted` and `TransformUpdated` — which differ only
        // in how the arbiter tracks them — share one implementation rather than a grouped branch
        // that Kotlin cannot smart-cast. The two adapters are named apart because Kotlin treats
        // local functions differing only in parameter type as recursive.
        //
        // Declared before its callers: a local function cannot be forward-referenced, so the
        // overloads that delegate here must appear after it.
        fun applyTransform(
            zoomChange: Float,
            panX: Float,
            panY: Float,
            focalX: Float,
            focalY: Float,
        ) {
            scope.launch {
                transformMutex.withLock {
                    val action = PagerViewport.onTransform(
                        state = viewportState,
                        zoomChange = zoomChange,
                        panX = panX,
                        panY = panY,
                        focalX = focalX,
                        focalY = focalY,
                    )
                    if (action.changed) {
                        viewportState = viewportState.copy(
                            transform = action.transform,
                            transforming = true,
                        )
                        // Pinch is instant, matching the legacy detector's `snapTo`.
                        scaleAnim.snapTo(action.transform.scale)
                        offsetAnim.snapTo(
                            Offset(
                                action.transform.offsetX,
                                action.transform.offsetY,
                            ),
                        )
                    }
                }
            }
        }

        fun applyStartedTransform(effect: ReaderGestureEffect.TransformStarted) =
            applyTransform(effect.zoomChange, effect.panX, effect.panY, effect.centroidX, effect.centroidY)

        fun applyUpdatedTransform(effect: ReaderGestureEffect.TransformUpdated) =
            applyTransform(effect.zoomChange, effect.panX, effect.panY, effect.centroidX, effect.centroidY)

        // Handles one arbiter effect. Declared before `gestureModifier` so the mapping from effect
        // to product behaviour is readable in one place, and so every effect is handled
        // exhaustively — a new effect added to the arbiter will not compile until it is considered
        // here rather than being silently ignored.
        fun handlePagerEffect(effect: ReaderGestureEffect) {
            when (effect) {
                is ReaderGestureEffect.TransformStarted -> applyStartedTransform(effect)
                is ReaderGestureEffect.TransformUpdated -> applyUpdatedTransform(effect)

                ReaderGestureEffect.LongPress -> onLongTap()

                is ReaderGestureEffect.DoubleTap -> {
                    if (viewportState.transform.scale > ZoomPolicy.ZOOM_GATE) {
                        // Zoomed: return to fit, animated as before.
                        scope.launch {
                            launch { scaleAnim.animateTo(1f, tween(300)) }
                            launch { offsetAnim.animateTo(Offset.Zero, tween(300)) }
                        }
                    } else {
                        // At fit: zoom in from the tapped point, animated as before.
                        val target = PagerViewport.onDoubleTap(
                            state = viewportState,
                            tappedX = effect.x,
                            tappedY = effect.y,
                            targetScale = 2.5f,
                        )
                        scope.launch {
                            launch { scaleAnim.animateTo(target.transform.scale, tween(300)) }
                            launch {
                                offsetAnim.animateTo(
                                    Offset(
                                        target.transform.offsetX,
                                        target.transform.offsetY,
                                    ),
                                    tween(300),
                                )
                            }
                        }
                    }
                }

                is ReaderGestureEffect.SingleTap -> {
                    val tapOffset = Offset(effect.x, effect.y)
                    val isNav = isNavigationTap?.invoke(tapOffset, containerSize) ?: false
                    // While zoomed only a navigation-zone tap acts; at fit every tap routes, which
                    // is how the menu is toggled. The arbiter has already delivered this after the
                    // double-tap window, so no extra delay is applied here.
                    val routes = viewportState.transform.scale <= ZoomPolicy.ZOOM_GATE || isNav
                    if (routes) onTap(tapOffset, containerSize)
                }

                // The arbiter declined and the pager owns this gesture. Deliberately not consumed,
                // which is precisely what lets the pager scroll.
                ReaderGestureEffect.DelegateSingleScroll -> Unit

                // A commit keeps the transform already applied; a cancellation restores the
                // committed one. Both are the state owner's business, so nothing is needed here.
                is ReaderGestureEffect.TransformCommitted,
                is ReaderGestureEffect.GestureCancelled,
                ReaderGestureEffect.None,
                is ReaderGestureEffect.TapCandidate,
                -> Unit
            }
        }

        // The arbiter's slop must match the Android touch slop the legacy detector used. It is read
        // from `LocalViewConfiguration` rather than hard-coded so a density change cannot silently
        // alter gesture ownership. It has to be read here, in composition, because the `config`
        // lambda below is not itself composable.
        val touchSlop = LocalViewConfiguration.current.touchSlop

        val gestureModifier = Modifier
            .fillMaxSize()
            .pagerGestureStream(
                documentRevision = { page.toString() },
                config = {
                    pagerGestureConfig(
                        state = viewportState,
                        touchSlop = touchSlop,
                        zoomLockThreshold = ZoomPolicy.INTERACTION_LOCK,
                    )
                },
                onEffect = { effect -> handlePagerEffect(effect) },
            )

        Box(modifier = gestureModifier, contentAlignment = Alignment.Center) {
            when (val currentStatus = status) {
                is Page.State.Queue, is Page.State.LoadPage -> {
                    ReaderPageLoadingView(progress = progress)
                }
                is Page.State.DownloadImage -> {
                    ReaderPageLoadingView(progress = progress)
                }
                is Page.State.Error -> {
                    ReaderPageErrorView(
                        modifier = Modifier,
                        error = currentStatus.error,
                        pageNumber = page.number,
                        onRetry = { page.chapter.pageLoader?.retryPage(page) },
                    )
                }
                Page.State.Ready -> {
                    var imageReadFailed by remember(page) { mutableStateOf(false) }
                    val imageModel by produceState<Any?>(
                        initialValue = page.mergedBitmap ?: page.cachedBytes,
                        page,
                        page.mergedBitmap,
                        page.cachedBytes,
                        page.status,
                    ) {
                        val resolved = page.mergedBitmap ?: page.cachedBytes ?: withIOContext {
                            try {
                                page.stream?.invoke()?.use { it.readBytes() }?.also { page.cachedBytes = it }
                            } catch (_: Exception) {
                                null
                            }
                        }
                        value = resolved
                        imageReadFailed = resolved == null
                    }
                    var imageAspectRatio by remember(page) { mutableStateOf(page.aspectRatio) }
                    val tallImageScrollState = rememberScrollState()
                    val merged = page.mergedBitmap

                    LaunchedEffect(imageModel, merged) {
                        val imageBytes = imageModel as? ByteArray
                        val dimensions = when {
                            merged != null && !merged.isRecycled -> merged.width to merged.height
                            imageBytes != null -> withIOContext {
                                ByteArrayInputStream(imageBytes).use(ImageUtil::getImageDimensions)
                            }
                            else -> null
                        }
                        if (dimensions != null && dimensions.first > 0 && dimensions.second > 0) {
                            page.width = dimensions.first
                            page.height = dimensions.second
                            imageAspectRatio = dimensions.first.toFloat() / dimensions.second
                        }
                    }

                    val isTallImage = imageAspectRatio?.let { it < 0.5f } == true
                    // Regular pages always fit the available screen without distortion. Tall pages
                    // remain width-filled and vertically scrollable.
                    val contentBoxModifier = if (isTallImage) {
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(tallImageScrollState)
                    } else {
                        Modifier.fillMaxSize()
                    }
                    val imageItemModifier = if (isTallImage) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier.fillMaxSize()
                    }
                    val imageContentScale = if (isTallImage) {
                        ContentScale.FillWidth
                    } else {
                        ContentScale.Fit
                    }

                    // The zoom transform is applied here and nowhere else. Before this, the pager
                    // computed `scaleAnim`/`offsetAnim` on every pinch and never bound them to any
                    // modifier, so a pinch changed internal state that nothing drew -- the direct
                    // cause of the reported "paged pinch zoom does nothing".
                    //
                    // It now reads from `viewportState`, the single owner, via `pagerZoomLayer`.
                    // `clip = true` bounds the painted result to the page box, so a zoomed page is
                    // cropped by the viewport instead of drawing over its pager neighbours.
                    val zoomModifier = Modifier.pagerZoomLayer(viewportState.transform)

                    Box(
                        modifier = contentBoxModifier.then(zoomModifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (merged != null && !merged.isRecycled && !cropBorders) {
                            Image(
                                bitmap = merged.asImageBitmap(),
                                contentDescription = "Page ${page.number}",
                                contentScale = imageContentScale,
                                modifier = imageItemModifier,
                            )
                        } else if (imageModel != null) {
                            AsyncImage(
                                model = remember(imageModel, cropBorders, isTallImage) {
                                    ImageRequest.Builder(context)
                                        .data(imageModel)
                                        .memoryCacheKey(
                                            readerPageMemoryCacheKey(page, cropBorders) +
                                                if (isTallImage) "_tall" else "",
                                        )
                                        .cropBorders(cropBorders)
                                        .precision(Precision.EXACT)
                                        // Decode at the source's original resolution so
                                        // zooming in preserves the full detail instead of
                                        // showing a downsampled (blurry) version.
                                        .size(CoilSize.ORIGINAL)
                                        .crossfade(false)
                                        .build()
                                },
                                placeholder = pageImagePlaceholderPainter(),
                                error = pageImageErrorPainter(),
                                contentDescription = "Page ${page.number}",
                                contentScale = imageContentScale,
                                modifier = imageItemModifier,
                            )
                        } else if (imageReadFailed) {
                            ReaderPageErrorView(
                                error = IllegalStateException("Page image is unavailable"),
                                pageNumber = page.number,
                                onRetry = { page.chapter.pageLoader?.retryPage(page) },
                            )
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        }
                    }
                }
            }
        }
    }
}
