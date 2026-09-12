package ephyra.feature.reader.viewer.webtoon

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
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
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val scrollDistance = screenHeightPx * 0.75f

    if (items.isEmpty()) {
        Box(modifier = modifier.fillMaxSize())
        return
    }

    // Listen to external scroll-to-index requests (e.g. from page slider scrubbing)
    LaunchedEffect(viewer, lazyListState) {
        viewer.scrollToIndexRequest.collect { targetIndex ->
            if (targetIndex in items.indices) {
                lazyListState.scrollToItem(targetIndex)
            }
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
                        item.to?.let(onRequestPreload)
                    }
                    is ChapterTransition.Next -> {
                        item.to?.let(onRequestPreload)
                    }
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val height = size.height
                        val yRatio = offset.y / height
                        when {
                            yRatio in 0.33f..0.66f -> onToggleMenu()
                            yRatio > 0.66f -> scope.launch { lazyListState.scrollBy(scrollDistance) }
                            else -> scope.launch { lazyListState.scrollBy(-scrollDistance) }
                        }
                    },
                )
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
                        ChapterTransition(
                            transition = item,
                            currChapterDownloaded = isCurrentChapterDownloaded,
                            goingToChapterDownloaded = isPreviousChapterDownloaded,
                        )
                    }
                    is ChapterTransition.Next -> {
                        ChapterTransition(
                            transition = item,
                            currChapterDownloaded = isCurrentChapterDownloaded,
                            goingToChapterDownloaded = isNextChapterDownloaded,
                        )
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
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
                val imageModel by produceState<Any?>(initialValue = page.mergedBitmap, page, page.mergedBitmap) {
                    value = page.mergedBitmap ?: withIOContext {
                        try {
                            page.stream?.invoke()?.use { it.readBytes() }
                        } catch (e: Exception) {
                            null
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
                        model = ImageRequest.Builder(context)
                            .data(imageModel)
                            .crossfade(true)
                            .precision(Precision.EXACT)
                            .cropBorders(cropBorders)
                            .build(),
                        contentDescription = "Page ${page.number}",
                        contentScale = ContentScale.FillWidth,
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
