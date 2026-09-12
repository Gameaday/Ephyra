package ephyra.feature.reader.viewer.pager

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.ImageUtil
import ephyra.core.common.util.system.logcat
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.reader.service.ReaderPreferences
import ephyra.domain.ui.UiPreferences
import ephyra.feature.reader.ReaderActivity
import ephyra.feature.reader.model.ChapterTransition
import ephyra.feature.reader.model.InsertPage
import ephyra.feature.reader.model.ReaderPage
import ephyra.feature.reader.model.ViewerChapters
import ephyra.feature.reader.viewer.Viewer
import ephyra.feature.reader.viewer.calculateChapterGap
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
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
 * Modern Jetpack Compose-backed viewer implementation for paginated reading modes
 * (L2R, R2L, and Vertical). Manages chapter page lists, transitions, navigation events,
 * and smart combine pre-scanning, with zero dependency on DirectionalViewPager.
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

    private val _targetPageRequest = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val targetPageRequest = _targetPageRequest.asSharedFlow()

    /**
     * Configuration used by the pager, including navigation mode, scale mode, and gestures.
     */
    val config = PagerConfig(this, scope, readerPreferences)

    /**
     * Currently active item (either [ReaderPage] or [ChapterTransition]).
     */
    var currentPage: Any? = null
        private set

    /**
     * Background job that proactively scans all pages in the current chapter for stub patterns
     * and merges them before they are displayed.
     */
    private var preScanJob: Job? = null

    private val fallbackView by lazy { View(activity) }

    init {
        config.navigationModeChangedListener = {
            val showOnStart = config.navigationOverlayOnStart || config.forceNavigationOverlay
            activity.showNavigationOverlay(config.navigator, showOnStart)
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

        val pages = chapters.currChapter.pages ?: return
        val targetPage = pages.getOrNull(min(chapters.currChapter.requestedPage, pages.lastIndex))
        if (targetPage != null) {
            moveToPage(targetPage)
        }
    }

    private fun rebuildItems(chapters: ViewerChapters) {
        val newItems = mutableListOf<Any>()
        val prevHasMissingChapters = calculateChapterGap(chapters.currChapter, chapters.prevChapter) > 0
        val nextHasMissingChapters = calculateChapterGap(chapters.nextChapter, chapters.currChapter) > 0

        // Previous chapter transition
        if (
            prevHasMissingChapters ||
            config.alwaysShowChapterTransition ||
            chapters.prevChapter?.state !is ephyra.feature.reader.model.ReaderChapter.State.Loaded
        ) {
            newItems.add(ChapterTransition.Prev(chapters.currChapter, chapters.prevChapter))
        }

        // Current chapter visible pages
        chapters.currChapter.pages?.filter { !it.isHidden }?.let(newItems::addAll)

        // Next chapter transition
        val nextTransition = ChapterTransition.Next(chapters.currChapter, chapters.nextChapter)
        if (
            nextHasMissingChapters ||
            config.alwaysShowChapterTransition ||
            chapters.nextChapter?.state !is ephyra.feature.reader.model.ReaderChapter.State.Loaded
        ) {
            newItems.add(nextTransition)
        }

        _itemsState.value = newItems
    }

    /**
     * Tells this viewer to move to the given [page].
     */
    override fun moveToPage(page: ReaderPage) {
        val items = _itemsState.value
        val position = items.indexOf(page)
        if (position != -1) {
            _targetPageRequest.tryEmit(position)
            currentPage = page
        } else {
            logcat { "Page $page not found in items list" }
        }
    }

    override fun moveToNext() {
        moveRight()
    }

    override fun moveToPrevious() {
        moveLeft()
    }

    fun moveRight() {
        val current = currentItemIndex()
        val count = _itemsState.value.size
        if (current < count - 1) {
            _targetPageRequest.tryEmit(current + 1)
        }
    }

    fun moveLeft() {
        val current = currentItemIndex()
        if (current > 0) {
            _targetPageRequest.tryEmit(current - 1)
        }
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
        activity.onPageSelected(page)
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
        preScanJob = scope.launch {
            withIOContext {
                for (index in pages.indices) {
                    if (!isActive) break

                    val page = pages[index]
                    if (page is InsertPage || page.mergedBitmap != null || page.isHidden) continue

                    if (page.status != Page.State.Ready) {
                        val arrived = page.statusFlow.firstOrNull { it == Page.State.Ready || it is Page.State.Error }
                        if (arrived != Page.State.Ready) continue
                    }

                    val nextPage = pages.getOrNull(index + 1) ?: continue
                    if (nextPage.isHidden) continue

                    if (nextPage.status != Page.State.Ready) {
                        val arrived = nextPage.statusFlow.firstOrNull {
                            it == Page.State.Ready || it is Page.State.Error
                        }
                        if (arrived != Page.State.Ready) continue
                    }

                    val streamFn = page.stream ?: continue
                    val nextStreamFn = nextPage.stream ?: continue
                    try {
                        val currentSource = streamFn().use { Buffer().readFrom(it) }
                        if (ImageUtil.isAnimatedAndSupported(currentSource)) continue
                        val isStub = nextStreamFn().use { ImageUtil.isSmallPage(it, currentSource) }
                        if (!isStub) continue

                        val nextSource = nextStreamFn().use { Buffer().readFrom(it) }
                        val mergedBitmap = ImageUtil.mergePages(currentSource, nextSource)
                        if (page.mergedBitmap == null) {
                            nextPage.isAbsorbed = true
                            page.mergedBitmap = mergedBitmap
                            activity.runOnUiThread {
                                onPageAbsorb(nextPage)
                            }
                        }
                    } catch (e: Exception) {
                        logcat(LogPriority.WARN, e) { "Smart combine pre-scan failed for page ${page.index}" }
                    }
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
