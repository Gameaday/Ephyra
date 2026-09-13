package ephyra.feature.reader.viewer.pager

import android.graphics.Bitmap
import android.graphics.PointF
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import ephyra.feature.reader.model.ReaderPage
import ephyra.presentation.core.data.coil.cropBorders
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.launch

/**
 * 100% Jetpack Compose zoomable image viewer for manga pages.
 * Supports:
 * - Fluid pinch-to-zoom (1.0x to 5.0x scale)
 * - Double-tap to toggle zoom (centered at tap point)
 * - Pan and drag when zoomed in with boundary snapping
 * - Single-tap gesture forwarding for reader navigation zones
 * - Long-tap gesture forwarding for page action sheets
 * - Coil 3 hardware bitmap decoding and caching
 * - Download and load progress indicators
 */
@Composable
fun ZoomableMangaPage(
    page: ReaderPage,
    cropBorders: Boolean,
    onTap: (Offset, Size) -> Unit,
    onLongTap: () -> Unit,
    onScaleChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by page.statusFlow.collectAsStateWithLifecycle()
    val progress by page.progressFlow.collectAsStateWithLifecycle()

    // Proactively trigger page loading
    LaunchedEffect(page) {
        withIOContext {
            page.chapter.pageLoader?.loadPage(page)
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()
        val containerSize = remember(containerWidth, containerHeight) {
            Size(containerWidth, containerHeight)
        }

        val scaleAnim = remember { Animatable(1f) }
        val offsetAnim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

        // Keep parent informed of zoom scale (so pager can enable/disable swiping)
        LaunchedEffect(scaleAnim.value) {
            onScaleChanged(scaleAnim.value)
        }

        // Reset zoom state on page identity change
        LaunchedEffect(page) {
            scaleAnim.snapTo(1f)
            offsetAnim.snapTo(Offset.Zero)
        }

        when (val currentStatus = status) {
            is Page.State.Queue, is Page.State.LoadPage -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                }
            }

            is Page.State.DownloadImage -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (progress > 0) {
                        CircularProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.size(48.dp),
                        )
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    }
                }
            }

            is Page.State.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
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
                    OutlinedButton(
                        onClick = {
                            page.chapter.pageLoader?.retryPage(page)
                        },
                    ) {
                        Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(text = "Retry")
                    }
                }
            }

            Page.State.Ready -> {
                // Buffer the image bytes for Coil or obtain cached merged bitmap
                val imageModel by produceState<Any?>(initialValue = page.mergedBitmap, page, page.mergedBitmap) {
                    value = page.mergedBitmap ?: withIOContext {
                        try {
                            page.stream?.invoke()?.use { it.readBytes() }
                        } catch (e: Exception) {
                            null
                        }
                    }
                }

                val gestureModifier = Modifier
                    .fillMaxSize()
                    .pointerInput(page) {
                        detectTapGestures(
                            onTap = { tapOffset ->
                                onTap(tapOffset, containerSize)
                            },
                            onLongPress = {
                                onLongTap()
                            },
                            onDoubleTap = { tapOffset ->
                                scope.launch {
                                    if (scaleAnim.value > 1.1f) {
                                        // Reset zoom to 1.0x
                                        launch { scaleAnim.animateTo(1f, tween(300)) }
                                        launch { offsetAnim.animateTo(Offset.Zero, tween(300)) }
                                    } else {
                                        // Zoom in 2.5x centered on tap point
                                        val targetScale = 2.5f
                                        val targetOffsetX = (containerWidth / 2f - tapOffset.x) * (targetScale - 1f)
                                        val targetOffsetY = (containerHeight / 2f - tapOffset.y) * (targetScale - 1f)
                                        val maxPanX = (containerWidth * targetScale - containerWidth) / 2f
                                        val maxPanY = (containerHeight * targetScale - containerHeight) / 2f
                                        val clampedOffset = Offset(
                                            x = targetOffsetX.coerceIn(-maxPanX, maxPanX),
                                            y = targetOffsetY.coerceIn(-maxPanY, maxPanY),
                                        )
                                        launch { scaleAnim.animateTo(targetScale, tween(300)) }
                                        launch { offsetAnim.animateTo(clampedOffset, tween(300)) }
                                    }
                                }
                            },
                        )
                    }
                    .pointerInput(page) {
                        detectMangaTransformGestures(
                            canPan = { scaleAnim.value > 1.05f },
                            onGesture = { _, pan, zoom ->
                                val currentScale = scaleAnim.value
                                val newScale = (currentScale * zoom).coerceIn(1f, 5f)
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
                    .graphicsLayer {
                        scaleX = scaleAnim.value
                        scaleY = scaleAnim.value
                        translationX = offsetAnim.value.x
                        translationY = offsetAnim.value.y
                    }

                Box(
                    modifier = gestureModifier,
                    contentAlignment = Alignment.Center,
                ) {
                    val merged = page.mergedBitmap
                    if (merged != null && !merged.isRecycled) {
                        Image(
                            bitmap = merged.asImageBitmap(),
                            contentDescription = "Page ${page.number}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (imageModel != null) {
                        AsyncImage(
                            model = remember(imageModel, cropBorders) {
                                ImageRequest.Builder(context)
                                    .data(imageModel)
                                    .cropBorders(cropBorders)
                                    .precision(Precision.EXACT)
                                    .crossfade(false)
                                    .build()
                            },
                            contentDescription = "Page ${page.number}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
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

                // If single pointer and unzoomed, do not consume pan so parent HorizontalPager swipes cleanly!
                if (!isMultiTouch && !canPan()) {
                    continue
                }

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
                            if (it.position != it.previousPosition) {
                                it.consume()
                            }
                        }
                    }
                }
            }
        } while (!canceled && event.changes.fastAny { it.pressed })
    }
}
