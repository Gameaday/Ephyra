package ephyra.feature.reader.viewer.pager

import android.graphics.PointF
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ephyra.core.common.util.system.logcat
import ephyra.feature.reader.model.ChapterTransition
import ephyra.feature.reader.model.ReaderChapter
import ephyra.feature.reader.model.ReaderPage
import ephyra.feature.reader.viewer.ViewerNavigation
import ephyra.presentation.reader.ChapterTransition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import logcat.LogPriority

/**
 * 100% Pure Jetpack Compose Reader Canvas for Paginated Manga:
 * Supports Left-to-Right (LTR), Right-to-Left (RTL), and Vertical pagers.
 * Completely replaces DirectionalViewPager and SubsamplingScaleImageView.
 */
@Composable
fun ComposePagerReader(
    viewer: PagerViewer,
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
    var isPagerScrollEnabled by remember { mutableStateOf(true) }

    val currentChapterId = chapters?.currChapter?.chapter?.id

    LaunchedEffect(viewer, onNextChapter, onPreviousChapter) {
        viewer.onNextChapter = onNextChapter
        viewer.onPreviousChapter = onPreviousChapter
    }

    val itemsChapterId = items.firstNotNullOfOrNull {
        when (it) {
            is ReaderPage -> it.chapter.chapter.id
            is ChapterTransition -> it.from.chapter.id
            else -> null
        }
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

    androidx.compose.runtime.key(currentChapterId) {
        val pagerState = rememberPagerState(
            initialPage = initialIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
            pageCount = { items.size },
        )

        // Handle external page navigation requests (slider scrubbing, d-pad, volume keys)
        LaunchedEffect(viewer, pagerState) {
            viewer.targetPageRequest.collect { request ->
                try {
                    if (request.index in 0 until pagerState.pageCount) {
                        if (request.animate) {
                            pagerState.animateScrollToPage(request.index)
                        } else {
                            pagerState.scrollToPage(request.index)
                        }
                    }
                } catch (e: CancellationException) {
                    if (!currentCoroutineContext().isActive) throw e
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Failed to scroll to target page" }
                }
            }
        }

        // Handle user swiping / page settlement
        LaunchedEffect(pagerState, items) {
            snapshotFlow { pagerState.currentPage }
                .distinctUntilChanged()
                .collect { position ->
                    val item = items.getOrNull(position) ?: return@collect
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
                            viewer.currentPage = item
                            item.to?.let(onRequestPreload)
                        }
                        is ChapterTransition.Next -> {
                            viewer.currentPage = item
                            item.to?.let(onRequestPreload)
                        }
                    }
                }
        }

        fun handleTap(tapOffset: Offset, containerSize: Size) {
            val normX = if (containerSize.width > 0) tapOffset.x / containerSize.width else 0.5f
            val normY = if (containerSize.height > 0) tapOffset.y / containerSize.height else 0.5f
            when (viewer.config.navigator.getAction(PointF(normX, normY))) {
                ViewerNavigation.NavigationRegion.MENU -> onToggleMenu()
                ViewerNavigation.NavigationRegion.NEXT -> viewer.moveToNext()
                ViewerNavigation.NavigationRegion.PREV -> viewer.moveToPrevious()
                ViewerNavigation.NavigationRegion.RIGHT -> viewer.moveRight()
                ViewerNavigation.NavigationRegion.LEFT -> viewer.moveLeft()
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

        val pageContent: @Composable (Int) -> Unit = { position ->
            when (val item = items.getOrNull(position)) {
                is ChapterTransition.Prev -> {
                    var totalDrag by remember { mutableFloatStateOf(0f) }
                    val dragModifier = if (viewer is VerticalPagerViewer) {
                        Modifier.pointerInput(viewer, onPreviousChapter) {
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
                        }
                    } else {
                        Modifier.pointerInput(viewer, onPreviousChapter) {
                            detectHorizontalDragGestures(
                                onDragStart = { totalDrag = 0f },
                                onDragEnd = {
                                    val shouldTrigger = if (viewer is R2LPagerViewer) {
                                        totalDrag < -80f
                                    } else {
                                        totalDrag >
                                            80f
                                    }
                                    if (shouldTrigger) {
                                        onPreviousChapter()
                                    }
                                    totalDrag = 0f
                                },
                                onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(dragModifier),
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
                    val dragModifier = if (viewer is VerticalPagerViewer) {
                        Modifier.pointerInput(viewer, onNextChapter) {
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
                        }
                    } else {
                        Modifier.pointerInput(viewer, onNextChapter) {
                            detectHorizontalDragGestures(
                                onDragStart = { totalDrag = 0f },
                                onDragEnd = {
                                    val shouldTrigger = if (viewer is R2LPagerViewer) {
                                        totalDrag > 80f
                                    } else {
                                        totalDrag <
                                            -80f
                                    }
                                    if (shouldTrigger) {
                                        onNextChapter()
                                    }
                                    totalDrag = 0f
                                },
                                onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(dragModifier),
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

                is ReaderPage -> {
                    ZoomableMangaPage(
                        page = item,
                        cropBorders = viewer.config.imageCropBorders,
                        onTap = ::handleTap,
                        onLongTap = { onPageLongTap(item) },
                        onScaleChanged = { scale ->
                            // Only disable swiping between pages when zoomed in (> 1.05x)
                            isPagerScrollEnabled = scale <= 1.05f
                        },
                        isNavigationTap = { tapOffset, containerSize ->
                            val normX = if (containerSize.width > 0) tapOffset.x / containerSize.width else 0.5f
                            val normY = if (containerSize.height > 0) tapOffset.y / containerSize.height else 0.5f
                            viewer.config.navigator.getAction(PointF(normX, normY)) !=
                                ViewerNavigation.NavigationRegion.MENU
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                null -> Unit
            }
        }

        when (viewer) {
            is R2LPagerViewer -> {
                // Natural Japanese / Manga Right-to-Left paging:
                // RTL LayoutDirection ensures page 0 starts on the right, swiping left advances forward
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        userScrollEnabled = isPagerScrollEnabled,
                        modifier = modifier.fillMaxSize(),
                    ) { position ->
                        // Re-nest into LTR so text / transitions inside the page aren't mirrored
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            pageContent(position)
                        }
                    }
                }
            }

            is VerticalPagerViewer -> {
                VerticalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    userScrollEnabled = isPagerScrollEnabled,
                    modifier = modifier.fillMaxSize(),
                ) { position ->
                    pageContent(position)
                }
            }

            else -> {
                // Left-to-Right (Western Comic / Manhwa)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        userScrollEnabled = isPagerScrollEnabled,
                        modifier = modifier.fillMaxSize(),
                    ) { position ->
                        pageContent(position)
                    }
                }
            }
        }
    }
}
