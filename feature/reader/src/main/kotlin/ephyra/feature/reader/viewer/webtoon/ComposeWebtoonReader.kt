package ephyra.feature.reader.viewer.webtoon

import android.graphics.PointF
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import ephyra.core.common.util.lang.withIOContext
import ephyra.feature.reader.model.ChapterTransition
import ephyra.feature.reader.model.ReaderChapter
import ephyra.feature.reader.model.ReaderPage
import ephyra.feature.reader.viewer.ViewerNavigation
import ephyra.presentation.core.data.coil.cropBorders
import ephyra.presentation.reader.ChapterTransition
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 100% Pure Jetpack Compose continuous vertical strip reader for Webtoon and Manhwa.
 * Replaces legacy WebtoonRecyclerView and WebtoonLayoutManager with GPU-accelerated LazyColumn.
 */
@Composable
fun ComposeWebtoonReader(
    viewer: WebtoonViewer,
    onPageSelected: (ReaderPage) -> Unit,
    onPageLongTap: (ReaderPage) -> Unit,
    onToggleMenu: () -> Unit,
    onRequestPreload: (ReaderChapter) -> Unit,
    onNextChapter: () -> Unit,
    onPreviousChapter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items by viewer.itemsState.collectAsStateWithLifecycle()
    val chapters by viewer.chaptersState.collectAsStateWithLifecycle()
    val currentChapterId = chapters?.currChapter?.chapter?.id
    val itemsChapterId = items.firstNotNullOfOrNull {
        when (it) {
            is ReaderPage -> it.chapter.chapter.id
            is ChapterTransition -> it.from.chapter.id
            else -> null
        }
    }

    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val scrollDistance = screenHeightPx * 0.75f

    LaunchedEffect(viewer, onNextChapter, onPreviousChapter) {
        viewer.onNextChapter = onNextChapter
        viewer.onPreviousChapter = onPreviousChapter
    }

    if (items.isEmpty() || (currentChapterId != null && itemsChapterId != null && itemsChapterId != currentChapterId)) {
        Box(modifier = modifier.fillMaxSize())
        return
    }

    val initialIndex = remember(currentChapterId, items) {
        val currChapter = chapters?.currChapter
        if (currChapter?.startFromEnd == true) {
            val lastPageIdx = items.indexOfLast { it is ReaderPage }
            if (lastPageIdx != -1) lastPageIdx else (items.size - 1).coerceAtLeast(0)
        } else if (currChapter?.startingAtBeginning == true) {
            if (items.firstOrNull() is ChapterTransition.Prev) 1 else 0
        } else {
            val requested = currChapter?.requestedPage ?: 0
            val targetPage = currChapter?.pages?.getOrNull(requested)
            if (targetPage != null) {
                val idx = items.indexOf(targetPage)
                if (idx != -1) {
                    idx
                } else if (items.firstOrNull() is ChapterTransition.Prev) {
                    1
                } else {
                    0
                }
            } else {
                if (items.firstOrNull() is ChapterTransition.Prev) 1 else 0
            }
        }
    }

    val isCurrentChapterDownloaded = remember(chapters) {
        chapters?.currChapter?.state is ReaderChapter.State.Loaded
    }
    val isPreviousChapterDownloaded = remember(chapters) {
        chapters?.prevChapter?.state is ReaderChapter.State.Loaded
    }
    val isNextChapterDownloaded = remember(chapters) {
        chapters?.nextChapter?.state is ReaderChapter.State.Loaded
    }

    androidx.compose.runtime.key(currentChapterId) {
        val lazyListState = rememberLazyListState(
            initialFirstVisibleItemIndex = initialIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
        )

        // Listen to external scroll-to-index requests (e.g. from page slider scrubbing)
        LaunchedEffect(viewer, lazyListState) {
            viewer.scrollToIndexRequest.collect { targetIndex ->
                if (targetIndex in items.indices) {
                    lazyListState.scrollToItem(targetIndex)
                }
            }
        }

        // Listen to external scroll-by requests (e.g. from volume keys or D-pad)
        LaunchedEffect(viewer, lazyListState, scrollDistance) {
            viewer.scrollByRequest.collect { factor ->
                lazyListState.animateScrollBy(factor * scrollDistance)
            }
        }

        // Observe scroll settling and report active page / preload triggers
        LaunchedEffect(lazyListState, items) {
            snapshotFlow { lazyListState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collect { firstIndex ->
                    val item = items.getOrNull(firstIndex) ?: return@collect
                    when (item) {
                        is ReaderPage -> {
                            viewer.onPageSelected(item)
                            onPageSelected(item)
                            val currPages = chapters?.currChapter?.pages
                            if (currPages != null && (currPages.size - item.number) < 5) {
                                chapters?.nextChapter?.let(onRequestPreload)
                            }
                        }
                        is ChapterTransition.Prev -> {
                            viewer.onTransitionSelected(item)
                            item.to?.let(onRequestPreload)
                        }
                        is ChapterTransition.Next -> {
                            viewer.onTransitionSelected(item)
                            item.to?.let(onRequestPreload)
                        }
                    }
                }
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .pointerInput(viewer, onNextChapter, onPreviousChapter) {
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                        val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                        if (up != null && (up.position - down.position).getDistance() < viewConfiguration.touchSlop) {
                            val normX = if (size.width > 0) up.position.x / size.width else 0.5f
                            val normY = if (size.height > 0) up.position.y / size.height else 0.5f
                            when (viewer.config.navigator.getAction(PointF(normX, normY))) {
                                ViewerNavigation.NavigationRegion.MENU -> onToggleMenu()
                                ViewerNavigation.NavigationRegion.NEXT, ViewerNavigation.NavigationRegion.RIGHT -> {
                                    if (!lazyListState.canScrollForward) {
                                        onNextChapter()
                                    } else {
                                        scope.launch { lazyListState.animateScrollBy(scrollDistance) }
                                    }
                                }
                                ViewerNavigation.NavigationRegion.PREV, ViewerNavigation.NavigationRegion.LEFT -> {
                                    if (!lazyListState.canScrollBackward) {
                                        onPreviousChapter()
                                    } else {
                                        scope.launch { lazyListState.animateScrollBy(-scrollDistance) }
                                    }
                                }
                            }
                        }
                    }
                },
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(
                    items = items,
                    key = { index, item ->
                        when (item) {
                            is ReaderPage -> "page_${item.chapter.chapter.id}_${item.index}"
                            is ChapterTransition.Prev -> "prev_trans_${item.from.chapter.id}"
                            is ChapterTransition.Next -> "next_trans_${item.from.chapter.id}"
                            else -> "webtoon_item_$index"
                        }
                    },
                ) { _, item ->
                    when (item) {
                        is ReaderPage -> {
                            WebtoonPageItem(
                                page = item,
                                cropBorders = viewer.config.imageCropBorders,
                                onLongTap = { onPageLongTap(item) },
                            )
                        }
                        is ChapterTransition.Prev -> {
                            var totalDrag by remember { mutableFloatStateOf(0f) }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(viewer, onPreviousChapter) {
                                        detectVerticalDragGestures(
                                            onDragStart = { totalDrag = 0f },
                                            onDragEnd = {
                                                if (totalDrag > 80f) {
                                                    onPreviousChapter()
                                                }
                                                totalDrag = 0f
                                            },
                                            onVerticalDrag = { _, dragAmount -> totalDrag += dragAmount },
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                ChapterTransition(
                                    transition = item,
                                    currChapterDownloaded = isCurrentChapterDownloaded,
                                    goingToChapterDownloaded = isPreviousChapterDownloaded,
                                    onTransitionClick = onPreviousChapter,
                                )
                            }
                        }
                        is ChapterTransition.Next -> {
                            var totalDrag by remember { mutableFloatStateOf(0f) }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(viewer, onNextChapter) {
                                        detectVerticalDragGestures(
                                            onDragStart = { totalDrag = 0f },
                                            onDragEnd = {
                                                if (totalDrag < -80f) {
                                                    onNextChapter()
                                                }
                                                totalDrag = 0f
                                            },
                                            onVerticalDrag = { _, dragAmount -> totalDrag += dragAmount },
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                ChapterTransition(
                                    transition = item,
                                    currChapterDownloaded = isCurrentChapterDownloaded,
                                    goingToChapterDownloaded = isNextChapterDownloaded,
                                    onTransitionClick = onNextChapter,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebtoonPageItem(
    page: ReaderPage,
    cropBorders: Boolean,
    onLongTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status by page.statusFlow.collectAsStateWithLifecycle()
    val progress by page.progressFlow.collectAsStateWithLifecycle()

    LaunchedEffect(page) {
        withIOContext {
            page.chapter.pageLoader?.loadPage(page)
        }
    }

    val itemModifier = if (page.aspectRatio != null) {
        modifier
            .fillMaxWidth()
            .aspectRatio(page.aspectRatio!!)
    } else {
        modifier
            .fillMaxWidth()
            .wrapContentHeight()
    }

    Box(
        modifier = itemModifier,
        contentAlignment = Alignment.Center,
    ) {
        when (val currentStatus = status) {
            is Page.State.Queue, is Page.State.LoadPage -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                }
            }

            is Page.State.DownloadImage -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center,
                ) {
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
                        .fillMaxWidth()
                        .height(300.dp)
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
                        onClick = { page.chapter.pageLoader?.retryPage(page) },
                    ) {
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
                    if (page.mergedBitmap != null) {
                        value = page.mergedBitmap
                    } else if (page.cachedBytes != null) {
                        value = page.cachedBytes
                    } else {
                        value = withIOContext {
                            try {
                                page.stream?.invoke()?.use { it.readBytes() }?.also {
                                    page.cachedBytes = it
                                }
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                }

                if (page.mergedBitmap != null) {
                    Image(
                        bitmap = page.mergedBitmap!!.asImageBitmap(),
                        contentDescription = "Page ${page.number}",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .pointerInput(page) {
                                detectTapGestures(onLongPress = { onLongTap() })
                            },
                    )
                } else if (imageModel != null) {
                    val context = LocalContext.current
                    AsyncImage(
                        model = remember(imageModel, cropBorders) {
                            ImageRequest.Builder(context)
                                .data(imageModel)
                                .memoryCacheKey("page_${page.chapter.chapter.id}_${page.index}")
                                .crossfade(false)
                                .precision(Precision.EXACT)
                                .cropBorders(cropBorders)
                                .build()
                        },
                        contentDescription = "Page ${page.number}",
                        contentScale = ContentScale.FillWidth,
                        onSuccess = { result ->
                            val img = result.result.image
                            if (img.width > 0 && img.height > 0) {
                                page.width = img.width
                                page.height = img.height
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .pointerInput(page) {
                                detectTapGestures(onLongPress = { onLongTap() })
                            },
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    }
                }
            }
        }
    }
}
