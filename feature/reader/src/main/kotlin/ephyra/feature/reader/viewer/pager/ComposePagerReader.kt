package ephyra.feature.reader.viewer.pager

import android.graphics.PointF
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ephyra.feature.reader.model.ChapterTransition
import ephyra.feature.reader.model.ReaderChapter
import ephyra.feature.reader.model.ReaderPage
import ephyra.feature.reader.viewer.ViewerNavigation
import ephyra.presentation.reader.ChapterTransition
import kotlinx.coroutines.flow.distinctUntilChanged

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

    if (items.isEmpty()) {
        Box(modifier = modifier.fillMaxSize())
        return
    }

    val initialIndex = remember(items) {
        val requested = chapters?.currChapter?.requestedPage ?: 0
        val targetPage = chapters?.currChapter?.pages?.getOrNull(requested)
        if (targetPage != null) {
            val idx = items.indexOf(targetPage)
            if (idx != -1) idx else 0
        } else {
            0
        }
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
        pageCount = { items.size },
    )

    // Handle external page navigation requests (slider scrubbing, d-pad, volume keys)
    LaunchedEffect(viewer, pagerState) {
        viewer.targetPageRequest.collect { targetIndex ->
            if (targetIndex in 0 until pagerState.pageCount) {
                if (viewer.config.usePageTransitions) {
                    pagerState.animateScrollToPage(targetIndex)
                } else {
                    pagerState.scrollToPage(targetIndex)
                }
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
                        item.to?.let(onRequestPreload)
                    }
                    is ChapterTransition.Next -> {
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
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ChapterTransition(
                        transition = item,
                        currChapterDownloaded = isCurrentChapterDownloaded,
                        goingToChapterDownloaded = isPreviousChapterDownloaded,
                    )
                }
            }

            is ChapterTransition.Next -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ChapterTransition(
                        transition = item,
                        currChapterDownloaded = isCurrentChapterDownloaded,
                        goingToChapterDownloaded = isNextChapterDownloaded,
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
