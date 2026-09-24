package ephyra.feature.reader.viewer.webtoon

import android.graphics.PointF
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.DeviceUtil
import ephyra.core.common.util.system.ImageUtil
import ephyra.feature.reader.model.ChapterTransition
import ephyra.feature.reader.model.ReaderChapter
import ephyra.feature.reader.model.ReaderPage
import ephyra.feature.reader.viewer.ChapterPositionTracker
import ephyra.feature.reader.viewer.ReaderPageErrorView
import ephyra.feature.reader.viewer.ReaderPageLoadingView
import ephyra.feature.reader.viewer.ViewerNavigation
import ephyra.feature.reader.viewer.pageImageErrorPainter
import ephyra.feature.reader.viewer.pageImagePlaceholderPainter
import ephyra.feature.reader.viewer.readerPageMemoryCacheKey
import ephyra.presentation.core.data.coil.cropBorders
import ephyra.presentation.reader.ChapterTransition
import ephyra.presentation.reader.TransitionDirection
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okio.Buffer
import java.io.ByteArrayInputStream
import kotlin.math.abs

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
    onReturnToSeries: () -> Unit,
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
    val context = LocalContext.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val scrollDistance = screenHeightPx * 0.75f

    // Shared visual zoom, reset per chapter: one pinch updates every strip so moving
    // 1→5 (or back) keeps a consistent scale instead of per-item jumps.
    val zoomState = rememberWebtoonZoomState(
        chapterId = currentChapterId,
        zoomEnabled = viewer.config.doubleTapZoom,
        zoomOutDisabled = viewer.config.zoomOutDisabled,
    )
    val zoomEnabled = viewer.config.doubleTapZoom

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

        // Direction-aware byte warm-up: LazyColumn only composes the visible window, and
        // loadPage() fires from inside the item — so sections skipped during a fast fling
        // never queue and sit blank. Watching the first/last visible indices lets us queue
        // the *next* N pages' downloads ahead of composition (bytes land in cachedBytes /
        // chapter disk cache before the item exists). Tier-scaled so LOW devices don't
        // burst the network and trip the source rate limit.
        val prefetchWindow = when (DeviceUtil.performanceTier(context)) {
            DeviceUtil.PerformanceTier.LOW -> 1
            DeviceUtil.PerformanceTier.MEDIUM -> 2
            DeviceUtil.PerformanceTier.HIGH -> 3
        }
        LaunchedEffect(lazyListState, items, prefetchWindow) {
            snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo.map { it.index } }
                .distinctUntilChanged()
                .collect { visible ->
                    if (visible.isEmpty()) return@collect
                    val pages = items.mapNotNull { it as? ReaderPage }
                    if (pages.isEmpty()) return@collect
                    val lastVisible = visible.max()
                    val firstVisible = visible.min()
                    // Forward: next unread window past the viewport.
                    var queued = 0
                    for (index in (lastVisible + 1)..(lastVisible + prefetchWindow)) {
                        val item = items.getOrNull(index) as? ReaderPage ?: continue
                        if (item.status == Page.State.Queue) {
                            withIOContext { item.chapter.pageLoader?.loadPage(item) }
                            if (++queued >= prefetchWindow) break
                        }
                    }
                    // Backward: single page behind (covers 200-segment back-scroll).
                    (firstVisible - 1 downTo maxOf(0, firstVisible - 1)).forEach { index ->
                        val item = items.getOrNull(index) as? ReaderPage ?: return@forEach
                        if (item.status == Page.State.Queue) {
                            withIOContext { item.chapter.pageLoader?.loadPage(item) }
                        }
                    }
                }
        }

        // A freshly opened chapter must start pinned at its resolved top slice. Reserving
        // slice heights upstream is not enough on its own: while the first slices decode, the
        // lazy list can still anchor itself lower in a long strip, which is exactly the
        // "viewport jumps to the bottom" symptom. Scrolling explicitly here locks the anchor
        // until the initial layout pass has settled.
        // One-shot positioning: run once per chapter, never on late page arrivals.
        // Re-firing on items.size changes (pages arriving async) yanks the scroll position
        // mid-chapter, which reads as jumps between sections. The shared tracker resets its
        // marker on chapter change, so revisits still re-position exactly once without
        // accumulating ids over a long session.
        val positionTracker = remember { ChapterPositionTracker() }
        LaunchedEffect(currentChapterId, items.size) {
            if (currentChapterId != null && positionTracker.claimPosition(currentChapterId)) {
                lazyListState.scrollToItem(
                    index = initialIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
                    scrollOffset = 0,
                )
            }
        }

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

        // Coverage-based completion. A webtoon counts as finished only once the final slice
        // has been fully revealed inside the viewport. Watching the *last* visible item rather
        // than the first keeps completion working for short trailing slices, which can never
        // occupy the top of the viewport on their own and therefore never trip a
        // first-visible-item check.
        val isChapterCovered by remember(lazyListState) {
            derivedStateOf {
                val layoutInfo = lazyListState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                if (totalItems == 0) return@derivedStateOf false

                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
                    ?: return@derivedStateOf false
                lastVisible.index == totalItems - 1 &&
                    (lastVisible.offset + lastVisible.size) <= layoutInfo.viewportEndOffset
            }
        }

        LaunchedEffect(lazyListState, items) {
            snapshotFlow { isChapterCovered }
                .distinctUntilChanged()
                .collect { covered ->
                    if (!covered) return@collect
                    val lastPage = items.lastOrNull { it is ReaderPage } as? ReaderPage
                        ?: return@collect
                    onPageSelected(lastPage)
                }
        }

        val webtoonOverscrollConnection = remember(onNextChapter, onPreviousChapter) {
            object : NestedScrollConnection {
                var accumulatedOverscroll = 0f

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source == NestedScrollSource.UserInput) {
                        if (!lazyListState.canScrollForward && available.y < 0) {
                            accumulatedOverscroll += available.y
                        } else if (!lazyListState.canScrollBackward && available.y > 0) {
                            accumulatedOverscroll += available.y
                        }
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    val threshold = with(density) { 60.dp.toPx() }
                    if (!lazyListState.canScrollForward &&
                        (accumulatedOverscroll < -threshold || available.y < -800f)
                    ) {
                        onNextChapter()
                    } else if (!lazyListState.canScrollBackward &&
                        (accumulatedOverscroll > threshold || available.y > 800f)
                    ) {
                        onPreviousChapter()
                    }
                    accumulatedOverscroll = 0f
                    return Velocity.Zero
                }
            }
        }

        // Pinch zoom + horizontal pan is applied per page item (visual only): content
        // rescales without changing LazyColumn layout, so zoom can never shift section
        // positions while scrolling between strips. Double-tap toggles 1x/2x fit.
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
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(webtoonOverscrollConnection),
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
                    // Separate composition pools so flings don't reuse a spinner/transition
                    // composition for a weight-shared slice column (or vice versa) — the
                    // main source of blank/mis-measured items on fast scroll.
                    contentType = { _, item ->
                        webtoonContentType(item, viewer.config.imageCropBorders)
                    },
                ) { _, item ->
                    when (item) {
                        is ReaderPage -> {
                            WebtoonPageItem(
                                page = item,
                                cropBorders = viewer.config.imageCropBorders,
                                zoomState = zoomState,
                                zoomEnabled = zoomEnabled,
                                onLongTap = { onPageLongTap(item) },
                            )
                        }
                        is ChapterTransition.Prev -> {
                            ChapterTransition(
                                transition = item,
                                currChapterDownloaded = isCurrentChapterDownloaded,
                                goingToChapterDownloaded = isPreviousChapterDownloaded,
                                onTransitionClick = onPreviousChapter,
                                onReturnClick = {
                                    scope.launch { lazyListState.animateScrollBy(scrollDistance) }
                                },
                                onReturnToSeries = onReturnToSeries,
                                direction = TransitionDirection.VERTICAL,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                            )
                        }
                        is ChapterTransition.Next -> {
                            ChapterTransition(
                                transition = item,
                                currChapterDownloaded = isCurrentChapterDownloaded,
                                goingToChapterDownloaded = isNextChapterDownloaded,
                                onTransitionClick = onNextChapter,
                                onReturnClick = {
                                    scope.launch { lazyListState.animateScrollBy(-scrollDistance) }
                                },
                                onReturnToSeries = onReturnToSeries,
                                direction = TransitionDirection.VERTICAL,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                            )
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
    zoomState: WebtoonZoomState,
    zoomEnabled: Boolean,
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

    // Visibility watchdog: sections skipped during a fast fling may sit composed-but-blank
    // (stale Queue/LoadPage, never queued). When this item is actually on screen and still
    // not Ready after the grace period, re-queue at high priority so parking on a blank
    // middle section loads it — no scroll-away-and-back needed.
    var itemIsVisible by remember(page) { mutableStateOf(false) }
    LaunchedEffect(page, itemIsVisible, status) {
        if (!itemIsVisible) return@LaunchedEffect
        if (status is Page.State.Ready || status is Page.State.Error) return@LaunchedEffect
        kotlinx.coroutines.delay(WebtoonVisibility.WATCHDOG_GRACE_MS)
        val current = page.status
        if (current is Page.State.Ready || current is Page.State.Error) return@LaunchedEffect
        if (page.cachedBytes == null && page.mergedBitmap == null) {
            withIOContext {
                page.chapter.pageLoader?.loadPage(page)
            }
        }
    }

    val intrinsicDimensions by produceState<Pair<Int, Int>?>(
        initialValue = page.aspectRatio?.let { page.width to page.height },
        page,
        page.cachedBytes,
        page.mergedBitmap,
        // React to the page status transition (Queue -> ... -> Ready). The loader worker
        // sets [page.stream] immediately before flipping [page.status] to Ready; without
        // this key the produceState block never re-runs after the first compose, so a
        // strip whose bytes arrive after first layout stays blank until the composition is
        // discarded (scroll off & back). `page.status` is collected from statusFlow and is
        // stable per transition, so this only triggers the cheap null-gated re-check.
        status,
    ) {
        if (value == null) {
            val bytes = page.cachedBytes ?: withIOContext {
                runCatching {
                    page.stream?.invoke()?.use { it.readBytes() }
                }.getOrNull()
            }
            if (bytes != null) {
                page.cachedBytes = bytes
                value = withIOContext {
                    ByteArrayInputStream(bytes).use(ImageUtil::getImageDimensions)
                }
            }
        }

        value?.let { (width, height) -> page.recordDimensionsOnce(width, height) }
    }

    // Single layout contract for every branch below (see webtoonItemBox): when dims are
    // known the item reserves the final full-strip height up front; sliced / single /
    // loading states all render inside this box so switching between them can never
    // resize the item or shift siblings.
    val itemModifier = modifier.webtoonItemBox(webtoonAspectRatio(intrinsicDimensions))

    Box(
        modifier = itemModifier
            .onGloballyPositioned { coordinates ->
                // Track on-screen visibility for the watchdog: actually intersecting the
                // window (not merely composed nearby via the prefetch window).
                itemIsVisible = coordinates.isAttached && !coordinates.boundsInWindow().isEmpty
            }
            // Visual-only zoom: graphicsLayer never changes layout size, so pinch and
            // double-tap resize content to fit without moving any section in the list.
            // Single pointerInput: tap/zoom share one detector chain so scales apply
            // once and vertical scroll always reaches the list.
            .graphicsLayer {
                scaleX = zoomState.scale
                scaleY = zoomState.scale
                translationX = zoomState.offsetX
                // Clip while zoomed: a scaled-up strip must not paint over its neighbors.
                clip = zoomState.scale > 1.01f
            }
            .pointerInput(page.index, zoomEnabled to zoomState) {
                if (!zoomEnabled) {
                    detectTapGestures(onLongPress = { onLongTap() })
                } else {
                    val min = zoomState.min
                    val max = zoomState.max
                    detectWebtoonGestures(
                        zoomMin = min,
                        zoomMax = max,
                        getScale = { zoomState.scale },
                        onZoom = { s: Float, p: Float -> zoomState.applyZoom(s, p) },
                        onDoubleTapToggle = { zoomState.toggleFit() },
                        onLongPress = onLongTap,
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when (val currentStatus = status) {
            is Page.State.Queue, is Page.State.LoadPage -> {
                // Visible-but-loading keeps the reserved box (no 300dp jump) with a
                // spinner: blank-background-forever becomes spinner → image, so the
                // visibility watchdog's work is obvious during testing.
                ReaderPageLoadingView(progress = progress)
            }

            is Page.State.DownloadImage -> {
                ReaderPageLoadingView(progress = progress)
            }

            is Page.State.Error -> {
                val isRateLimited = remember(currentStatus) {
                    isRateLimitError(currentStatus.error)
                }
                ReaderPageErrorView(
                    modifier = Modifier,
                    error = currentStatus.error,
                    pageNumber = page.number,
                    onRetry = { page.chapter.pageLoader?.retryPage(page) },
                    // 429/503: the source asked us to back off — an instant Retry storm
                    // only extends the ban. Debounce with a countdown, matching the
                    // RateLimitBackoffInterceptor's escalation posture.
                    retryContent = if (isRateLimited) {
                        { RateLimitedRetry(page = page, onRetry = { page.chapter.pageLoader?.retryPage(page) }) }
                    } else {
                        null
                    },
                )
            }

            Page.State.Ready -> {
                var readyBytesReadFailed by remember(page) { mutableStateOf(false) }
                val readyBytes by produceState<ByteArray?>(
                    initialValue = page.mergedBitmap?.let { null } ?: page.cachedBytes,
                    page,
                    page.mergedBitmap,
                    // Retry that re-downloads yields new bytes: include the size tag so the
                    // state re-resolves instead of serving the stale entry (blank section
                    // that only loads after scroll-away-and-back).
                    page.cachedBytes?.size,
                    // Re-run when the page crosses into Ready: the worker sets [page.stream]
                    // just before marking Ready, so reacting to the status transition lets the
                    // bytes be read (and cachedBytes populated) on first paint instead of
                    // forcing a scroll-away-and-back recycle to invalidate the composition.
                    status,
                ) {
                    readyBytesReadFailed = false
                    if (page.mergedBitmap == null && value == null) {
                        value = withIOContext {
                            try {
                                page.stream?.invoke()?.use { it.readBytes() }?.also {
                                    page.cachedBytes = it
                                }
                            } catch (e: Exception) {
                                null
                            }
                        }
                        readyBytesReadFailed = value == null
                    }
                }

                // Animated check on buffered bytes (peek-based, no pixel decode). Animated
                // pages and JXL (unsupported by BitmapRegionDecoder) bypass slicing:
                // region decode would return the first frame only / fail outright.
                val animatedHint = remember(readyBytes) {
                    readyBytes?.let { bytes ->
                        runCatching {
                            Buffer().write(bytes).let {
                                ImageUtil.isAnimatedAndSupported(it) ||
                                    ImageUtil.findImageType(bytes.inputStream()) ==
                                    ImageUtil.ImageType.JXL
                            }
                        }.getOrDefault(false)
                    } ?: false
                }

                // Use the device's actual screen width as the target for image loading.
                // This ensures the image is loaded at native screen resolution, preserving
                // quality for any device (phones, tablets, foldables) regardless of density.
                val density = LocalDensity.current
                val configuration = LocalConfiguration.current
                val targetWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
                // Compose viewport height drives the per-slice ceiling (~1.5 viewports).
                val viewportHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

                // Long strips render sliced at full width resolution; everything else (and any
                // slice failure) uses the single-image path via `fallback`.
                val shouldAttemptSlices = !cropBorders && !animatedHint &&
                    page.mergedBitmap == null && readyBytes != null &&
                    (intrinsicDimensions != null || (page.width > 0 && page.height > 0))

                if (page.mergedBitmap != null) {
                    Image(
                        bitmap = page.mergedBitmap!!.asImageBitmap(),
                        contentDescription = "Page ${page.number}",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            // Fill the reserved item box (sized by the single aspect contract
                            // above) — never wrapContent, which would resize the LazyColumn
                            // item after layout and jump siblings when moving between sections.
                            .fillMaxSize()
                            .pointerInput(page.index) {
                                detectTapGestures(onLongPress = { onLongTap() })
                            },
                    )
                } else if (shouldAttemptSlices && readyBytes != null) {
                    val (knownW, knownH) = intrinsicDimensions ?: (page.width to page.height)
                    SlicedWebtoonImage(
                        page = page,
                        bytes = readyBytes!!,
                        srcWidth = knownW,
                        srcHeight = knownH,
                        targetWidthPx = targetWidthPx,
                        viewportHeightPx = viewportHeightPx,
                        cropBorders = cropBorders,
                        isAnimated = animatedHint,
                        fallback = {
                            SingleWebtoonImage(
                                page = page,
                                imageModel = readyBytes!!,
                                cropBorders = cropBorders,
                                targetWidthPx = targetWidthPx,
                                densityScale = density.density,
                                bytesSize = readyBytes!!.size,
                            )
                        },
                    )
                } else if (readyBytesReadFailed) {
                    ReaderPageErrorView(
                        error = IllegalStateException("Page image is unavailable"),
                        pageNumber = page.number,
                        onRetry = { page.chapter.pageLoader?.retryPage(page) },
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

/**
 * Single-image fallback path: the pre-slice Coil request (screen-width constrained).
 * Used for short pages and whenever slicing is bypassed or fails.
 */
@Composable
private fun SingleWebtoonImage(
    page: ReaderPage,
    imageModel: Any,
    cropBorders: Boolean,
    targetWidthPx: Float,
    densityScale: Float,
    bytesSize: Int,
) {
    val context = LocalContext.current
    // Decode at physical pixels (css width * density): decoding at bare screenWidthDp
    // then upscaling via FillWidth is the "blurry strip" bug on high-dpi devices.
    val decodeWidth = (targetWidthPx * densityScale.coerceAtLeast(1f)).toInt().coerceAtLeast(1)
    AsyncImage(
        model = remember(imageModel, cropBorders, decodeWidth, bytesSize) {
            ImageRequest.Builder(context)
                .data(imageModel)
                .memoryCacheKey(
                    readerPageMemoryCacheKey(page, cropBorders) +
                        "_w${decodeWidth}_b$bytesSize",
                )
                .crossfade(false)
                .precision(Precision.EXACT)
                .cropBorders(cropBorders)
                // Constrain the decode to the physical screen width: the full strip is
                // downsampled only in width (never below the display size) while keeping
                // its full vertical detail.
                .size(decodeWidth)
                .build()
        },
        placeholder = pageImagePlaceholderPainter(),
        error = pageImageErrorPainter(),
        contentDescription = "Page ${page.number}",
        contentScale = ContentScale.FillWidth,
        // Never overwrite page dims here: the item box is already reserved from intrinsic
        // dimensions, and a Coil-reported size (post downsample/crop) would resize the
        // LazyColumn item after layout and jump surrounding sections. Gestures live on the
        // owning item Box — no pointerInput here so detectors never nest or double-apply.
        modifier = Modifier.fillMaxSize(),
    )
}
