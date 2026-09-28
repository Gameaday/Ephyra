package ephyra.feature.reader.viewer.pager

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import ephyra.core.common.util.system.ImageUtil
import ephyra.core.common.util.system.logcat
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.reader.pager.StepOriginPolicy
import ephyra.domain.reader.service.ReaderPreferences
import ephyra.domain.ui.UiPreferences
import ephyra.feature.reader.ReaderActivity
import ephyra.feature.reader.model.ChapterTransition
import ephyra.feature.reader.model.InsertPage
import ephyra.feature.reader.model.NavigationVector
import ephyra.feature.reader.model.ReaderPage
import ephyra.feature.reader.model.ViewerChapters
import ephyra.feature.reader.viewer.ChapterPositionTracker
import ephyra.feature.reader.viewer.Viewer
import ephyra.feature.reader.viewer.calculateChapterGap
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.LogPriority
import okio.Buffer
import kotlin.math.min

/**
 * 100% Pure Jetpack Compose-first implementation of [PagerViewer].
 */
@Suppress("LeakingThis")
abstract class PagerViewer(
    val activity: ReaderActivity,
    val downloadManager: DownloadManager,
    val readerPreferences: ReaderPreferences,
    val uiPreferences: UiPreferences,
) : Viewer {

    private val scope = MainScope()

    private val _chaptersState = MutableStateFlow<ViewerChapters?>(null)
    val chaptersState = _chaptersState.asStateFlow()

    private val _itemsState = MutableStateFlow<List<Any>>(emptyList())
    val itemsState = _itemsState.asStateFlow()

    data class TargetPage(val index: Int, val animate: Boolean = false)

    // Conflated to the newest value, deliberately.
    //
    // This is a latest-value-wins signal: dragging the seek bar emits dozens of targets per second
    // and only the final one matters. The previous configuration was a 64-slot buffer, which kept
    // a *queue* of stale targets for the collector to churn through — and because `scrollToPage` is
    // suspending, each in-flight scroll was cancelled by the next emission, so the pager chased a
    // target the user had already dragged past and settled late.
    //
    // `replay = 1` is what does the work: the replay cache always holds the most recently emitted
    // value, so a slow collector skips the stale intermediate targets entirely and acts on the
    // current one. `DROP_OLDEST` then bounds the *buffer* without discarding the newest value,
    // which `DROP_LATEST` would do.
    private val _targetPageRequest = MutableSharedFlow<TargetPage>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val targetPageRequest = _targetPageRequest.asSharedFlow()

    /**
     * Configuration used by the pager, including navigation mode, scale mode, and gestures.
     */
    val config = PagerConfig(this, scope, readerPreferences)

    /**
     * Currently active item (either [ReaderPage] or [ChapterTransition]).
     */
    var currentPage: Any? = null
        internal set(value) {
            field = value
            if (value !is ReaderPage) {
                pendingTargetIndex = null
            }
        }

    /**
     * Callbacks invoked when reaching chapter boundaries.
     */
    var onNextChapter: (() -> Unit)? = null
    var onPreviousChapter: (() -> Unit)? = null

    private val positionTracker = ChapterPositionTracker()

    /**
     * Background job that proactively scans all pages in the current chapter for stub patterns
     * and merges them before they are displayed.
     */
    private var preScanJob: Job? = null

    private val fallbackView by lazy { View(activity) }

    private var lastSmartCombine = config.smartCombine

    init {
        config.navigationModeChangedListener = {
            val showOnStart = config.navigationOverlayOnStart || config.forceNavigationOverlay
            activity.showNavigationOverlay(config.navigator, showOnStart)
        }
        config.imagePropertyChangedListener = {
            activity.runOnUiThread {
                handleImagePropertyChanged()
            }
        }
    }

    private fun handleImagePropertyChanged() {
        val smartCombineNow = config.smartCombine
        if (smartCombineNow != lastSmartCombine) {
            lastSmartCombine = smartCombineNow
            val chapters = _chaptersState.value
            val pages = chapters?.currChapter?.pages
            if (smartCombineNow) {
                launchSmartCombinePreScan(pages)
            } else {
                preScanJob?.cancel()
                pages?.forEach { page ->
                    page.isAbsorbed = false
                    page.clearMergedBitmap()
                }
                chapters?.let { rebuildItems(it) }
            }
        }
    }

    override fun destroy() {
        super.destroy()
        preScanJob?.cancel()
        scope.cancel()
    }

    /**
     * Fallback view reference to fulfill [Viewer] interface contract.
     * The actual rendering is handled natively in Compose via [ComposePagerReader].
     */
    override fun getView(): View = fallbackView

    /**
     * Tells this viewer to set the given [chapters] as active.
     */
    override fun setChapters(chapters: ViewerChapters) {
        pendingTargetIndex = null
        _chaptersState.value = chapters
        rebuildItems(chapters)

        // Register callback so when the page pre-processor marks a page as blocked,
        // the items list refreshes to exclude it.
        chapters.currChapter.pageLoader?.onPageFiltered = {
            activity.runOnUiThread {
                rebuildItems(chapters)
            }
        }

        launchSmartCombinePreScan(chapters.currChapter.pages)

        val curr = chapters.currChapter
        val pages = curr.pages
        // `pages != null` repeated after the claim purely to restore the smart-cast the
        // compiler needs inside the block; claimPosition already required it via canPosition.
        if (positionTracker.claimPosition(curr.chapter.id, canPosition = pages != null) &&
            pages != null
        ) {
            val targetPage = when {
                curr.startFromEnd -> pages.lastOrNull { !it.isHidden } ?: pages.last()
                curr.startingAtBeginning -> pages.firstOrNull { !it.isHidden } ?: pages.first()
                else -> pages.getOrNull(min(curr.requestedPage, pages.lastIndex)) ?: pages.first()
            }
            moveToPage(targetPage)
        }
    }

    private fun rebuildItems(chapters: ViewerChapters) {
        val newItems = mutableListOf<Any>()
        val prevHasMissingChapters = calculateChapterGap(chapters.currChapter, chapters.prevChapter) > 0
        val nextHasMissingChapters = calculateChapterGap(chapters.nextChapter, chapters.currChapter) > 0

        // Previous chapter transition (persistent whenever a previous chapter exists, a gap is present, or configured)
        if (
            prevHasMissingChapters ||
            config.alwaysShowChapterTransition ||
            chapters.prevChapter != null
        ) {
            newItems.add(ChapterTransition.Prev(chapters.currChapter, chapters.prevChapter))
        }

        // Current chapter visible pages
        chapters.currChapter.pages?.filter { !it.isHidden }?.let(newItems::addAll)

        // Next chapter transition (persistent whenever a next chapter exists, a gap is present, or configured)
        val nextTransition = ChapterTransition.Next(chapters.currChapter, chapters.nextChapter)
        if (
            nextHasMissingChapters ||
            config.alwaysShowChapterTransition ||
            chapters.nextChapter != null
        ) {
            newItems.add(nextTransition)
        }

        _itemsState.value = newItems
    }

    private var pendingTargetIndex: Int? = null

    /**
     * Tells this viewer to move to the given [page].
     *
     * Whether the jump animates follows the `sliderNavMode` preference. It previously did not: this
     * method hardcoded `animate = false`, so a user who chose "Smooth" and one who chose "Instant"
     * got byte-identical behaviour while the settings screen described a distinction that did not
     * exist. The preference was stored, mirrored into [PagerConfig], surfaced in two settings UIs —
     * and read by nobody, which a project-wide search for `sliderNavMode` confirmed.
     */
    override fun moveToPage(page: ReaderPage) {
        val items = _itemsState.value
        val position = items.indexOf(page)
        if (position != -1) {
            pendingTargetIndex = null
            currentPage = page
            _targetPageRequest.tryEmit(
                TargetPage(position, animate = config.sliderNavMode == ReaderPreferences.SLIDER_NAV_SMOOTH),
            )
        } else {
            logcat { "Page $page not found in items list" }
        }
    }

    override fun moveToNext() {
        val current = stepOriginIndex()
        val count = _itemsState.value.size
        if (current < count - 1) {
            val next = current + 1
            pendingTargetIndex = next
            _targetPageRequest.tryEmit(TargetPage(next, animate = config.usePageTransitions))
        } else if (count > 0 && current >= count - 1) {
            pendingTargetIndex = null
            onNextChapter?.invoke()
        }
    }

    override fun moveToPrevious() {
        val current = stepOriginIndex()
        if (current > 0) {
            val prev = current - 1
            pendingTargetIndex = prev
            _targetPageRequest.tryEmit(TargetPage(prev, animate = config.usePageTransitions))
        } else if (current <= 0) {
            pendingTargetIndex = null
            onPreviousChapter?.invoke()
        }
    }

    open fun moveRight() {
        moveToNext()
    }

    open fun moveLeft() {
        moveToPrevious()
    }

    fun moveUp() {
        moveToPrevious()
    }

    fun moveDown() {
        moveToNext()
    }

    private fun currentItemIndex(): Int {
        val cur = currentPage ?: return 0
        return _itemsState.value.indexOf(cur).coerceAtLeast(0)
    }

    fun onPageSelected(page: ReaderPage) {
        currentPage = page
        val index = _itemsState.value.indexOf(page)
        if (index != -1 && index == pendingTargetIndex) {
            pendingTargetIndex = null
        }
        activity.onPageSelected(page)
    }

    /**
     * The index a step should move from.
     *
     * `pendingTargetIndex` is cleared only by `onPageSelected` when the settled index happens to
     * equal it, by `setChapters`, or when [currentPage] is set to a non-page item. A `moveToNext`
     * that was dropped before it landed therefore left a stale value that nothing would clear, and
     * every later step advanced from that phantom index — the pager drifted permanently out of sync
     * with the visible page until the chapter changed.
     *
     * Two properties close that. A pending index can never sit outside the current items, so a
     * stale value from a shorter chapter cannot push a step past the end. And a pending index that
     * has fallen *behind* [currentPage] is discarded rather than trusted, because the pager has
     * demonstrably moved past it: a request that no longer describes the pager's position is
     * discarded, not obeyed.
     */
    private fun stepOriginIndex(): Int {
        val items = _itemsState.value
        val resolved = StepOriginPolicy.resolve(
            settledIndex = currentItemIndex(),
            pendingIndex = pendingTargetIndex,
            itemCount = items.size,
        )
        if (resolved.clearPending) {
            pendingTargetIndex = null
        }
        return resolved.origin
    }

    fun onPageAbsorb(parentPage: ReaderPage, absorbedPage: ReaderPage) {
        parentPage.mergedBitmap?.let { bmp ->
            parentPage.width = bmp.width
            parentPage.height = bmp.height
        }
        if (currentPage == absorbedPage) {
            currentPage = parentPage
        }
        _itemsState.update { current -> current.filter { it != absorbedPage } }
        // Smart-combine merges the *following* page into its predecessor, so the parent
        // page contains the absorbed page's content. Completion must respect the vector
        // the user actually arrived with: swiping back into a chapter whose last visible
        // page was absorbed must not mark it read.
        activity.viewModel.checkChapterCompletion(parentPage, activity.viewModel.lastNavigationVector)
    }

    fun onPageAbsorb(page: ReaderPage) {
        _itemsState.update { current -> current.filter { it != page } }
    }

    fun onPageSplit(currentPage: ReaderPage, newPage: InsertPage) {
        _itemsState.update { current ->
            val list = current.toMutableList()
            val index = list.indexOf(currentPage)
            if (index != -1) {
                list.add(index + 1, newPage)
            }
            list
        }
    }

    private fun launchSmartCombinePreScan(pages: List<ReaderPage>?) {
        preScanJob?.cancel()
        if (!config.smartCombine || pages == null) return
        preScanJob = scope.launch(Dispatchers.IO) {
            var index = 0
            while (index < pages.size && isActive) {
                val page = pages[index]
                if (page is InsertPage || page.isHidden) {
                    index++
                    continue
                }

                if (page.status != Page.State.Ready) {
                    val arrived = page.statusFlow.firstOrNull { it == Page.State.Ready || it is Page.State.Error }
                    if (arrived != Page.State.Ready || !isActive) {
                        index++
                        continue
                    }
                }

                var nextIndex = index + 1
                while (nextIndex < pages.size && pages[nextIndex].isHidden) {
                    nextIndex++
                }
                val nextPage = pages.getOrNull(nextIndex)
                if (nextPage == null || nextPage is InsertPage) {
                    index++
                    continue
                }

                if (nextPage.status != Page.State.Ready) {
                    val arrived = nextPage.statusFlow.firstOrNull {
                        it == Page.State.Ready || it is Page.State.Error
                    }
                    if (arrived != Page.State.Ready || !isActive) {
                        index++
                        continue
                    }
                }

                val streamFn = page.stream
                val nextStreamFn = nextPage.stream
                if (streamFn == null || nextStreamFn == null) {
                    index++
                    continue
                }

                try {
                    val currentSource = streamFn().use { Buffer().readFrom(it) }
                    if (ImageUtil.isAnimatedAndSupported(currentSource)) {
                        index++
                        continue
                    }

                    // 1. Check if the CURRENT page is a top-banner stub of the NEXT page
                    if (page.mergedBitmap == null) {
                        val nextSourceForRef = nextStreamFn().use { Buffer().readFrom(it) }
                        if (!ImageUtil.isAnimatedAndSupported(nextSourceForRef)) {
                            val isTopStub = streamFn().use { ImageUtil.isSmallPage(it, nextSourceForRef) }
                            if (isTopStub) {
                                val mergedBitmap = ImageUtil.mergePages(currentSource, nextSourceForRef)
                                page.isAbsorbed = true
                                nextPage.mergedBitmap = mergedBitmap
                                nextPage.width = mergedBitmap.width
                                nextPage.height = mergedBitmap.height
                                activity.runOnUiThread {
                                    onPageAbsorb(nextPage, page)
                                }
                                index = nextIndex
                                continue
                            }
                        }
                    }

                    // 2. Check if NEXT page (and subsequent chained pages) are bottom stubs of CURRENT page
                    var candidateIndex = nextIndex
                    var absorbedAny = false
                    while (candidateIndex < pages.size && isActive) {
                        val candidateNext = pages[candidateIndex]
                        if (candidateNext is InsertPage || candidateNext.isHidden) {
                            candidateIndex++
                            continue
                        }
                        if (candidateNext.status != Page.State.Ready) {
                            val arrived = candidateNext.statusFlow.firstOrNull {
                                it == Page.State.Ready || it is Page.State.Error
                            }
                            if (arrived != Page.State.Ready || !isActive) break
                        }
                        val candidateStreamFn = candidateNext.stream ?: break

                        val isBottomStub = if (page.mergedBitmap != null) {
                            candidateStreamFn().use {
                                ImageUtil.isSmallPage(it, page.mergedBitmap!!.width, page.mergedBitmap!!.height)
                            }
                        } else {
                            candidateStreamFn().use {
                                ImageUtil.isSmallPage(it, currentSource)
                            }
                        }

                        if (!isBottomStub) break

                        val candidateSource = candidateStreamFn().use { Buffer().readFrom(it) }
                        if (ImageUtil.isAnimatedAndSupported(candidateSource)) break

                        val mergedBitmap = page.mergedBitmap?.let { previous ->
                            // The superseded merge is dropped rather than recycled: ART
                            // reclaims its pixel buffer, and the pager may still be
                            // rendering it via a live Compose snapshot.
                            ImageUtil.mergePages(previous, candidateSource)
                        } ?: ImageUtil.mergePages(currentSource, candidateSource)

                        candidateNext.isAbsorbed = true
                        page.mergedBitmap = mergedBitmap
                        page.width = mergedBitmap.width
                        page.height = mergedBitmap.height
                        absorbedAny = true

                        activity.runOnUiThread {
                            onPageAbsorb(page, candidateNext)
                        }
                        candidateIndex++
                    }

                    if (absorbedAny) {
                        index = candidateIndex
                    } else {
                        index++
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Smart combine pre-scan failed for page ${page.index}" }
                    index++
                }
            }
        }
    }

    override fun handleKeyEvent(event: KeyEvent): Boolean {
        val isUp = event.action == KeyEvent.ACTION_UP
        val ctrlPressed = event.metaState.and(KeyEvent.META_CTRL_ON) > 0

        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (!config.volumeKeysEnabled || activity.viewModel.state.value.menuVisible) {
                    return false
                } else if (isUp) {
                    if (!config.volumeKeysInverted) moveDown() else moveUp()
                }
            }

            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (!config.volumeKeysEnabled || activity.viewModel.state.value.menuVisible) {
                    return false
                } else if (isUp) {
                    if (!config.volumeKeysInverted) moveUp() else moveDown()
                }
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isUp) {
                    if (ctrlPressed) moveToNext() else moveRight()
                }
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isUp) {
                    if (ctrlPressed) moveToPrevious() else moveLeft()
                }
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> if (isUp) moveDown()
            KeyEvent.KEYCODE_DPAD_UP -> if (isUp) moveUp()
            KeyEvent.KEYCODE_PAGE_DOWN -> if (isUp) moveDown()
            KeyEvent.KEYCODE_PAGE_UP -> if (isUp) moveUp()
            KeyEvent.KEYCODE_MENU -> if (isUp) activity.toggleMenu()
            else -> return false
        }
        return true
    }

    override fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_CLASS_POINTER != 0) {
            when (event.action) {
                MotionEvent.ACTION_SCROLL -> {
                    if (event.getAxisValue(MotionEvent.AXIS_VSCROLL) < 0.0f) {
                        moveDown()
                    } else {
                        moveUp()
                    }
                    return true
                }
            }
        }
        return false
    }
}
