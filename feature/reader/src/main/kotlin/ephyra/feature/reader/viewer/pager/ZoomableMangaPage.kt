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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
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
import ephyra.feature.reader.model.ReaderPage
import ephyra.feature.reader.viewer.readerPageMemoryCacheKey
import ephyra.presentation.core.components.ExpressiveCircularProgressIndicator
import ephyra.presentation.core.data.coil.cropBorders
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
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

        LaunchedEffect(scaleAnim.value) { onScaleChanged(scaleAnim.value) }
        LaunchedEffect(page) {
            scaleAnim.snapTo(1f)
            offsetAnim.snapTo(Offset.Zero)
        }

        val gestureModifier = Modifier
            .fillMaxSize()
            .pointerInput(page, containerSize) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPos = down.position
                    val upEvent = try {
                        withTimeout(viewConfiguration.longPressTimeoutMillis) {
                            val up = waitForUpOrCancellation()
                            if (up != null && !up.isConsumed) up else null
                        }
                    } catch (_: TimeoutCancellationException) {
                        if (scaleAnim.value <= 1.05f) onLongTap()
                        null
                    }

                    if (upEvent != null && (upEvent.position - downPos).getDistance() < viewConfiguration.touchSlop) {
                        val tapOffset = upEvent.position
                        val currentTime = System.currentTimeMillis()
                        val isDoubleTap = currentTime - lastTapTime < 350L &&
                            (tapOffset - lastTapOffset).getDistance() < viewConfiguration.touchSlop * 3
                        val isNav = isNavigationTap?.invoke(tapOffset, containerSize) ?: false

                        if (scaleAnim.value > 1.05f) {
                            if (isDoubleTap) {
                                scope.launch {
                                    launch { scaleAnim.animateTo(1f, tween(300)) }
                                    launch { offsetAnim.animateTo(Offset.Zero, tween(300)) }
                                }
                                lastTapTime = 0L
                            } else {
                                lastTapTime = currentTime
                                lastTapOffset = tapOffset
                                if (isNav) onTap(tapOffset, containerSize)
                            }
                        } else if (isNav) {
                            onTap(tapOffset, containerSize)
                            lastTapTime = 0L
                        } else if (isDoubleTap) {
                            val targetScale = 2.5f
                            val targetOffset = Offset(
                                x = (containerWidth / 2f - tapOffset.x) * (targetScale - 1f),
                                y = (containerHeight / 2f - tapOffset.y) * (targetScale - 1f),
                            )
                            val maxPanX = (containerWidth * targetScale - containerWidth).coerceAtLeast(0f) / 2f
                            val maxPanY = (containerHeight * targetScale - containerHeight).coerceAtLeast(0f) / 2f
                            val clampedOffset = Offset(
                                x = targetOffset.x.coerceIn(-maxPanX, maxPanX),
                                y = targetOffset.y.coerceIn(-maxPanY, maxPanY),
                            )
                            scope.launch {
                                launch { scaleAnim.animateTo(targetScale, tween(300)) }
                                launch { offsetAnim.animateTo(clampedOffset, tween(300)) }
                            }
                            lastTapTime = 0L
                        } else {
                            lastTapTime = currentTime
                            lastTapOffset = tapOffset
                            onTap(tapOffset, containerSize)
                        }
                    }
                }
            }
            .pointerInput(page, containerSize) {
                detectMangaTransformGestures(
                    canPan = { scaleAnim.value > 1.05f },
                    onGesture = { _, pan, zoom ->
                        val newScale = (scaleAnim.value * zoom).coerceIn(1f, 5f)
                        val maxPanX = ((containerWidth * newScale) - containerWidth).coerceAtLeast(0f) / 2f
                        val maxPanY = ((containerHeight * newScale) - containerHeight).coerceAtLeast(0f) / 2f
                        val newOffset = if (newScale > 1f) {
                            Offset(
                                x = (offsetAnim.value.x + pan.x).coerceIn(-maxPanX, maxPanX),
                                y = (offsetAnim.value.y + pan.y).coerceIn(-maxPanY, maxPanY),
                            )
                        } else {
                            Offset.Zero
                        }
                        scope.launch {
                            scaleAnim.snapTo(newScale)
                            offsetAnim.snapTo(newOffset)
                        }
                    },
                )
            }

        Box(modifier = gestureModifier, contentAlignment = Alignment.Center) {
            when (val currentStatus = status) {
                is Page.State.Queue, is Page.State.LoadPage -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ExpressiveCircularProgressIndicator(progress = -1f, modifier = Modifier.size(48.dp))
                    }
                }
                is Page.State.DownloadImage -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (progress > 0) {
                            ExpressiveCircularProgressIndicator(
                                progress = progress / 100f,
                                modifier = Modifier.size(48.dp),
                            )
                        } else {
                            ExpressiveCircularProgressIndicator(progress = -1f, modifier = Modifier.size(48.dp))
                        }
                    }
                }
                is Page.State.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = currentStatus.error.message ?: "Failed to load page ${page.number}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = { page.chapter.pageLoader?.retryPage(page) }) {
                            Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(text = "Retry")
                        }
                    }
                }
                Page.State.Ready -> {
                    val imageModel by produceState<Any?>(
                        initialValue = page.mergedBitmap ?: page.cachedBytes,
                        page,
                        page.mergedBitmap,
                    ) {
                        value = page.mergedBitmap ?: page.cachedBytes ?: withIOContext {
                            try {
                                page.stream?.invoke()?.use { it.readBytes() }?.also { page.cachedBytes = it }
                            } catch (_: Exception) {
                                null
                            }
                        }
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
                    val imageModifier = if (isTallImage) {
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(tallImageScrollState)
                            .wrapContentHeight(unbounded = true)
                    } else {
                        Modifier.fillMaxSize()
                    }
                    val imageContentScale = if (isTallImage) ContentScale.FillWidth else ContentScale.Fit

                    Box(
                        modifier = imageModifier,
                        contentAlignment = Alignment.Center,
                    ) {
                        if (merged != null && !merged.isRecycled) {
                            Image(
                                bitmap = merged.asImageBitmap(),
                                contentDescription = "Page ${page.number}",
                                contentScale = imageContentScale,
                                modifier = Modifier.fillMaxSize(),
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
                                        .apply { if (isTallImage) size(CoilSize.ORIGINAL) }
                                        .crossfade(false)
                                        .build()
                                },
                                contentDescription = "Page ${page.number}",
                                contentScale = imageContentScale,
                                modifier = imageModifier,
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

private suspend fun PointerInputScope.detectMangaTransformGestures(
    canPan: () -> Boolean,
    onGesture: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
) {
    awaitEachGesture {
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.fastAny { it.isConsumed }
            if (!canceled) {
                val pointerCount = event.changes.count { it.pressed }
                val isMultiTouch = pointerCount >= 2
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                if (!isMultiTouch && !canPan()) continue

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    pan += panChange
                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = kotlin.math.abs(1f - zoom) * centroidSize
                    val panMotion = pan.getDistance()
                    if ((isMultiTouch && zoomMotion > touchSlop) || (canPan() && panMotion > touchSlop)) {
                        pastTouchSlop = true
                    }
                }

                if (pastTouchSlop) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    if (zoomChange != 1f || (canPan() && panChange != Offset.Zero)) {
                        onGesture(centroid, if (canPan()) panChange else Offset.Zero, zoomChange)
                        event.changes.fastForEach {
                            if (it.position != it.previousPosition) it.consume()
                        }
                    }
                }
            }
        } while (!canceled && event.changes.fastAny { it.pressed })
    }
}
