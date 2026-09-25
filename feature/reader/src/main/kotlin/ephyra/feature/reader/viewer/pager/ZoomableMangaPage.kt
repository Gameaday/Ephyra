package ephyra.feature.reader.viewer.pager

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.ImageUtil
import ephyra.domain.reader.viewport.PagerTransformPoint
import ephyra.domain.reader.viewport.PagerZoomPolicy
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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
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
        var lastTapTime by remember { mutableStateOf(0L) }
        var lastTapOffset by remember { mutableStateOf(Offset.Zero) }
        var pendingSingleTap by remember { mutableStateOf<Job?>(null) }
        val transformMutex = remember { Mutex() }

        // Zoom arithmetic lives in the domain so the focal-point invariant is unit tested. The
        // composable only adapts gesture values to it and applies the result.
        val zoomPolicy = remember { PagerZoomPolicy() }
        val viewportSize = remember(containerWidth, containerHeight) {
            ViewportSize(containerWidth, containerHeight)
        }

        LaunchedEffect(scaleAnim.value) { onScaleChanged(scaleAnim.value) }
        LaunchedEffect(page) {
            pendingSingleTap?.cancel()
            pendingSingleTap = null
            scaleAnim.snapTo(1f)
            offsetAnim.snapTo(Offset.Zero)
        }

        val gestureModifier = Modifier
            .fillMaxSize()
            .pointerInput(page, containerSize) {
                detectPagerGestures(
                    canPan = { ZoomPolicy.locksInteraction(scaleAnim.value) },
                    onLongPress = {
                        pendingSingleTap?.cancel()
                        pendingSingleTap = null
                        onLongTap()
                    },
                    onTap = { tapOffset ->
                        val currentTime = System.currentTimeMillis()
                        val isDoubleTap = currentTime - lastTapTime < 350L &&
                            (tapOffset - lastTapOffset).getDistance() < viewConfiguration.touchSlop * 3
                        val isNav = isNavigationTap?.invoke(tapOffset, containerSize) ?: false

                        if (scaleAnim.value > ZoomPolicy.ZOOM_GATE) {
                            if (isDoubleTap) {
                                pendingSingleTap?.cancel()
                                pendingSingleTap = null
                                scope.launch {
                                    launch { scaleAnim.animateTo(1f, tween(300)) }
                                    launch { offsetAnim.animateTo(Offset.Zero, tween(300)) }
                                }
                                lastTapTime = 0L
                            } else {
                                lastTapTime = currentTime
                                lastTapOffset = tapOffset
                                if (isNav) {
                                    pendingSingleTap?.cancel()
                                    pendingSingleTap = scope.launch {
                                        delay(350L)
                                        onTap(tapOffset, containerSize)
                                    }
                                }
                            }
                        } else if (isDoubleTap) {
                            pendingSingleTap?.cancel()
                            pendingSingleTap = null
                            // Anchored on the tapped point, so double-tap zoom grows out from the
                            // finger rather than from the page centre.
                            val target = zoomPolicy.doubleTap(
                                current = PagerZoomTransform(
                                    scaleAnim.value,
                                    offsetAnim.value.x,
                                    offsetAnim.value.y,
                                ),
                                tapped = PagerTransformPoint(tapOffset.x, tapOffset.y),
                                viewportSize = viewportSize,
                                targetScale = 2.5f,
                            )
                            scope.launch {
                                launch { scaleAnim.animateTo(target.scale, tween(300)) }
                                launch {
                                    offsetAnim.animateTo(
                                        Offset(target.offsetX, target.offsetY),
                                        tween(300),
                                    )
                                }
                            }
                            lastTapTime = 0L
                        } else {
                            lastTapTime = currentTime
                            lastTapOffset = tapOffset
                            pendingSingleTap?.cancel()
                            pendingSingleTap = scope.launch {
                                delay(350L)
                                onTap(tapOffset, containerSize)
                            }
                        }
                    },
                    onTransform = { centroid, pan, zoom ->
                        pendingSingleTap?.cancel()
                        pendingSingleTap = null
                        scope.launch {
                            transformMutex.withLock {
                                // The centroid is the focal point. It was previously discarded, so
                                // the content slid away from the fingers instead of staying under
                                // them. `PagerZoomPolicy` holds the focal point fixed and clamps
                                // pan to the scaled viewport; both are unit tested.
                                val next = zoomPolicy.next(
                                    current = PagerZoomTransform(
                                        scaleAnim.value,
                                        offsetAnim.value.x,
                                        offsetAnim.value.y,
                                    ),
                                    zoom = zoom,
                                    pan = pan.x,
                                    panY = pan.y,
                                    focal = PagerTransformPoint(centroid.x, centroid.y),
                                    viewportSize = viewportSize,
                                )
                                scaleAnim.snapTo(next.scale)
                                offsetAnim.snapTo(Offset(next.offsetX, next.offsetY))
                            }
                        }
                    },
                )
            }

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
                    // `clip = true` bounds the painted result to the page box, so a zoomed page is
                    // cropped by the viewport instead of drawing over its pager neighbours.
                    val zoomModifier = Modifier.graphicsLayer {
                        scaleX = scaleAnim.value
                        scaleY = scaleAnim.value
                        translationX = offsetAnim.value.x
                        translationY = offsetAnim.value.y
                        transformOrigin = TransformOrigin.Center
                        clip = true
                    }

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

internal fun shouldClaimPagerTransform(
    pressedCount: Int,
    accumulatedPan: Offset,
    canPan: Boolean,
    touchSlop: Float,
): Boolean {
    return pressedCount >= 2 || (canPan && accumulatedPan.getDistance() >= touchSlop)
}

private suspend fun PointerInputScope.detectPagerGestures(
    canPan: () -> Boolean,
    onLongPress: () -> Unit,
    onTap: (Offset) -> Unit,
    onTransform: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
        val downPosition = down.position
        val touchSlop = viewConfiguration.touchSlop
        val longPressAt = System.currentTimeMillis() + viewConfiguration.longPressTimeoutMillis
        var accumulatedPan = Offset.Zero
        var transformStarted = false
        var wasMultiTouch = false

        while (true) {
            val event = try {
                val remaining = (longPressAt - System.currentTimeMillis()).coerceAtLeast(1L)
                withTimeout(remaining) { awaitPointerEvent(PointerEventPass.Initial) }
            } catch (_: TimeoutCancellationException) {
                if (!transformStarted && !wasMultiTouch) onLongPress()
                break
            }
            val pressedCount = event.changes.count { it.pressed }
            if (pressedCount == 0) {
                val up = event.changes.firstOrNull { it.id == down.id }
                if (!transformStarted && !wasMultiTouch && up != null &&
                    (up.position - downPosition).getDistance() < touchSlop
                ) {
                    onTap(up.position)
                }
                break
            }
            // A parent may consume the first one-pointer movement before the second pointer
            // arrives. Do not end the gesture: the second pointer must still be observed and
            // allowed to claim the pinch before the pager/list consumes the transform.
            if (pressedCount < 2 && event.changes.fastAny { it.isConsumed }) {
                // Keep observing, but do not start single-pointer panning from a consumed event.
                continue
            }

            val isMultiTouch = pressedCount >= 2
            if (isMultiTouch) wasMultiTouch = true
            val zoomChange = event.calculateZoom()
            val panChange = event.calculatePan()

            if (!transformStarted) {
                accumulatedPan += panChange
                if (shouldClaimPagerTransform(pressedCount, accumulatedPan, canPan(), touchSlop)) {
                    transformStarted = true
                }
            }

            if (transformStarted && (zoomChange != 1f || (canPan() && panChange != Offset.Zero) || isMultiTouch)) {
                val centroid = event.calculateCentroid(useCurrent = false)
                onTransform(centroid, if (canPan()) panChange else Offset.Zero, zoomChange)
                event.changes.fastForEach {
                    if (it.position != it.previousPosition) it.consume()
                }
            }
        }
    }
}
