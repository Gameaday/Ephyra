package ephyra.feature.reader.viewer.webtoon

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import ephyra.core.common.util.system.logcat
import ephyra.domain.base.BasePreferences
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.reader.service.ReaderPreferences
import ephyra.domain.ui.UiPreferences
import ephyra.feature.reader.ReaderActivity
import ephyra.feature.reader.model.ChapterTransition
import ephyra.feature.reader.model.ReaderChapter
import ephyra.feature.reader.model.ReaderPage
import ephyra.feature.reader.model.ViewerChapters
import ephyra.feature.reader.viewer.Viewer
import ephyra.feature.reader.viewer.calculateChapterGap
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.min

/**
 * Modern Jetpack Compose-backed implementation of [Viewer] for continuous vertical reading modes
 * (Webtoon and Continuous Vertical).
 * Manages chapter state, items, page transitions, navigation events, and scroll flows,
 * with zero dependency on RecyclerView or legacy View hierarchy.
 */
class WebtoonViewer(
    val activity: ReaderActivity,
    val downloadManager: DownloadManager,
    val readerPreferences: ReaderPreferences,
    val uiPreferences: UiPreferences,
    val basePreferences: BasePreferences,
    val isContinuous: Boolean = true,
) : Viewer {

    private val scope = MainScope()

    private val _chaptersState = MutableStateFlow<ViewerChapters?>(null)
    val chaptersState: StateFlow<ViewerChapters?> = _chaptersState.asStateFlow()

    private val _itemsState = MutableStateFlow<List<Any>>(emptyList())
    val itemsState: StateFlow<List<Any>> = _itemsState.asStateFlow()

    private val _scrollToIndexRequest = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val scrollToIndexRequest: SharedFlow<Int> = _scrollToIndexRequest.asSharedFlow()

    private val _scrollByRequest = MutableSharedFlow<Float>(extraBufferCapacity = 1)
    val scrollByRequest: SharedFlow<Float> = _scrollByRequest.asSharedFlow()

    /**
     * Configuration used by this viewer, like allow taps, or crop image borders.
     */
    val config = WebtoonConfig(scope, readerPreferences)

    /**
     * Currently active item. It can be a chapter page or a chapter transition.
     */
    var currentPage: Any? = null
        private set

    private val fallbackView by lazy { View(activity) }

    init {
        config.navigationModeChangedListener = {
            val showOnStart = config.navigationOverlayOnStart || config.forceNavigationOverlay
            activity.showNavigationOverlay(config.navigator, showOnStart)
        }
    }

    /**
     * Fallback view reference to fulfill [Viewer] interface contract.
     * Actual rendering is handled natively in Compose via [ComposeWebtoonReader].
     */
    override fun getView(): View = fallbackView

    /**
     * Destroys this viewer. Called when leaving the reader or swapping viewers.
     */
    override fun destroy() {
        super.destroy()
        scope.cancel()
    }

    /**
     * Rebuilds the unified items list including previous chapter pages, chapter transitions,
     * current chapter pages, and next chapter pages for seamless continuous scrolling.
     */
    private fun rebuildItems(chapters: ViewerChapters, forceTransition: Boolean = false) {
        val newItems = mutableListOf<Any>()

        val prevHasMissingChapters = calculateChapterGap(chapters.currChapter, chapters.prevChapter) > 0
        val nextHasMissingChapters = calculateChapterGap(chapters.nextChapter, chapters.currChapter) > 0

        // Previous chapter pages
        chapters.prevChapter?.pages?.filter { !it.isHidden }?.let(newItems::addAll)

        // Previous chapter transition
        if (
            prevHasMissingChapters ||
            forceTransition ||
            chapters.prevChapter?.state !is ReaderChapter.State.Loaded
        ) {
            newItems.add(ChapterTransition.Prev(chapters.currChapter, chapters.prevChapter))
        }

        // Current chapter pages
        chapters.currChapter.pages?.filter { !it.isHidden }?.let(newItems::addAll)

        // Next chapter transition
        if (
            nextHasMissingChapters ||
            forceTransition ||
            chapters.nextChapter?.state !is ReaderChapter.State.Loaded
        ) {
            newItems.add(ChapterTransition.Next(chapters.currChapter, chapters.nextChapter))
        }

        // Next chapter pages
        chapters.nextChapter?.pages?.filter { !it.isHidden }?.let(newItems::addAll)

        _itemsState.value = newItems
    }

    /**
     * Called when a [page] is marked as active. Notifies the activity and preloads next chapter if near the end.
     */
    fun onPageSelected(page: ReaderPage, allowPreload: Boolean = true) {
        currentPage = page
        val pages = page.chapter.pages ?: return
        logcat { "onPageSelected: ${page.number}/${pages.size}" }
        activity.onPageSelected(page)

        val inPreloadRange = pages.size - page.number < 5
        if (inPreloadRange && allowPreload && page.chapter == _chaptersState.value?.currChapter) {
            val nextChapter = _chaptersState.value?.nextChapter
            if (nextChapter != null) {
                logcat { "Requesting to preload chapter ${nextChapter.chapter.chapterNumber}" }
                activity.requestPreloadChapter(nextChapter)
            }
        }
    }

    /**
     * Called when a [transition] is marked as active. Preloads the destination chapter.
     */
    fun onTransitionSelected(transition: ChapterTransition) {
        currentPage = transition
        logcat { "onTransitionSelected: $transition" }
        val toChapter = transition.to
        if (toChapter != null) {
            logcat { "Request preload destination chapter because we're on the transition" }
            activity.requestPreloadChapter(toChapter)
        }
    }

    /**
     * Tells this viewer to set the given [chapters] as active.
     */
    override fun setChapters(chapters: ViewerChapters) {
        _chaptersState.value = chapters
        val forceTransition = config.alwaysShowChapterTransition || currentPage is ChapterTransition
        rebuildItems(chapters, forceTransition)

        // Register callback so when the page pre-processor marks a page as blocked,
        // items refresh to exclude it.
        chapters.currChapter.pageLoader?.onPageFiltered = {
            activity.runOnUiThread {
                rebuildItems(chapters, false)
            }
        }

        val pages = chapters.currChapter.pages ?: return
        val targetPage = pages.getOrNull(min(chapters.currChapter.requestedPage, pages.lastIndex))
        if (targetPage != null) {
            moveToPage(targetPage)
        }
    }

    /**
     * Tells this viewer to move to the given [page].
     */
    override fun moveToPage(page: ReaderPage) {
        val position = _itemsState.value.indexOf(page)
        if (position != -1) {
            _scrollToIndexRequest.tryEmit(position)
            currentPage = page
        } else {
            logcat { "Page $page not found in items" }
        }
    }

    override fun moveToNext() {
        scrollDown()
    }

    override fun moveToPrevious() {
        scrollUp()
    }

    /**
     * Requests scrolling up by one viewport distance.
     */
    fun scrollUp() {
        _scrollByRequest.tryEmit(-1f)
    }

    /**
     * Requests scrolling down by one viewport distance.
     */
    fun scrollDown() {
        _scrollByRequest.tryEmit(1f)
    }

    /**
     * Handles hardware key events (Volume keys, D-pad, Page Up/Down, Menu).
     */
    override fun handleKeyEvent(event: KeyEvent): Boolean {
        val isUp = event.action == KeyEvent.ACTION_UP

        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (!config.volumeKeysEnabled || activity.viewModel.state.value.menuVisible) {
                    return false
                } else if (isUp) {
                    if (!config.volumeKeysInverted) scrollDown() else scrollUp()
                }
            }

            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (!config.volumeKeysEnabled || activity.viewModel.state.value.menuVisible) {
                    return false
                } else if (isUp) {
                    if (!config.volumeKeysInverted) scrollUp() else scrollDown()
                }
            }

            KeyEvent.KEYCODE_MENU -> if (isUp) activity.toggleMenu()

            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_PAGE_UP,
            -> if (isUp) scrollUp()

            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN,
            -> if (isUp) scrollDown()

            else -> return false
        }
        return true
    }

    override fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        return false
    }
}
