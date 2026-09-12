package ephyra.feature.reader

import android.app.Activity
import android.app.assist.AssistContent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.View.LAYER_TYPE_HARDWARE
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import ephyra.core.common.notification.NotificationManager
import ephyra.core.common.util.lang.launchNonCancellable
import ephyra.core.common.util.system.logcat
import ephyra.domain.base.BasePreferences
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.reader.model.ReaderOrientation
import ephyra.domain.reader.model.ReadingMode
import ephyra.domain.reader.service.ReaderPreferences
import ephyra.domain.ui.UiPreferences
import ephyra.feature.reader.ReaderViewModel.SetAsCoverResult.AddToLibraryFirst
import ephyra.feature.reader.ReaderViewModel.SetAsCoverResult.Error
import ephyra.feature.reader.ReaderViewModel.SetAsCoverResult.Success
import ephyra.feature.reader.model.ReaderChapter
import ephyra.feature.reader.model.ReaderPage
import ephyra.feature.reader.model.ViewerChapters
import ephyra.feature.reader.viewer.Viewer
import ephyra.feature.reader.viewer.ViewerNavigation
import ephyra.feature.reader.viewer.navigation.DisabledNavigation
import ephyra.feature.reader.viewer.pager.L2RPagerViewer
import ephyra.feature.reader.viewer.pager.R2LPagerViewer
import ephyra.feature.reader.viewer.pager.VerticalPagerViewer
import ephyra.feature.reader.viewer.webtoon.WebtoonViewer
import ephyra.presentation.core.data.coil.TachiyomiImageDecoder
import ephyra.presentation.core.ui.activity.BaseActivity
import ephyra.presentation.core.util.AppNavigator
import ephyra.presentation.core.util.system.copyToClipboard
import ephyra.presentation.core.util.system.isNightMode
import ephyra.presentation.core.util.system.openInBrowser
import ephyra.presentation.core.util.system.toShareIntent
import ephyra.presentation.core.util.system.toast
import ephyra.presentation.core.util.view.applyHighRefreshRate
import ephyra.presentation.core.util.view.overrideTransitionCompat
import ephyra.presentation.core.util.view.setComposeContent
import ephyra.presentation.reader.DisplayRefreshHost
import ephyra.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import javax.inject.Inject
import ephyra.presentation.core.R as CoreR

@AndroidEntryPoint
@OptIn(FlowPreview::class)
class ReaderActivity : BaseActivity() {

    companion object {
        fun newIntent(context: Context, mangaId: Long?, chapterId: Long?): Intent {
            return Intent(context, ReaderActivity::class.java).apply {
                putExtra("manga", mangaId)
                putExtra("chapter", chapterId)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }

    @Inject lateinit var readerPreferences: ReaderPreferences

    @Inject lateinit var preferences: BasePreferences

    @Inject lateinit var navigator: AppNavigator

    @Inject lateinit var notificationManager: NotificationManager

    @Inject lateinit var downloadManager: DownloadManager

    @Inject lateinit var uiPreferences: UiPreferences

    val viewModel: ReaderViewModel by viewModels()
    private var assistUrl: String? = null

    /**
     * Configuration at reader level, like background color or forced orientation.
     */
    private var config: ReaderConfig? = null

    private var menuToggleToast: Toast? = null
    private var readingModeToast: Toast? = null
    private val displayRefreshHost: DisplayRefreshHost by lazy { DisplayRefreshHost(readerPreferences) }

    private val windowInsetsController: WindowInsetsControllerCompat by lazy {
        WindowInsetsControllerCompat(
            window,
            window.decorView,
        )
    }

    private var currentViewer by mutableStateOf<Viewer?>(null)
    private var navigationOverlayState by mutableStateOf<ViewerNavigation?>(null)

    /**
     * Reading mode the current [ReaderViewModel.State.viewer] was created for. Used to detect
     * when the viewer must be recreated because the reading mode type changed.
     */
    private var viewerType: ReadingMode? = null

    internal var isScrollingThroughPages = false

    override fun onCreate(savedInstanceState: Bundle?) {
        window.applyHighRefreshRate()

        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        super.onCreate(savedInstanceState)

        setComposeContent {
            TachiyomiTheme {
                ReaderScreen(
                    viewModel = viewModel,
                    currentViewer = currentViewer,
                    navigationOverlay = navigationOverlayState,
                    onDismissNavigationOverlay = { navigationOverlayState = null },
                    readerPreferences = readerPreferences,
                    displayRefreshHost = displayRefreshHost,
                    onNavigateUp = onBackPressedDispatcher::onBackPressed,
                    onClickTopAppBar = ::openMangaScreen,
                    onOpenInWebView = ::openChapterInWebView.takeIf { viewModel.getSource() is HttpSource },
                    onOpenInBrowser = ::openChapterInBrowser.takeIf { viewModel.getSource() is HttpSource },
                    onShare = ::shareChapter.takeIf { viewModel.getSource() is HttpSource },
                    onNextChapter = { lifecycleScope.launch { viewModel.loadNextChapter() } },
                    onPreviousChapter = { lifecycleScope.launch { viewModel.loadPreviousChapter() } },
                    onPageIndexChange = ::moveToPageIndex,
                    showToast = ::showToast,
                )
            }
        }

        if (viewModel.needsInit()) {
            val manga = intent.extras?.getLong("manga", -1) ?: -1L
            val chapter = intent.extras?.getLong("chapter", -1) ?: -1L
            if (manga == -1L || chapter == -1L) {
                finish()
                return
            }
            notificationManager.dismissNewChaptersNotification(manga)

            lifecycleScope.launchNonCancellable {
                val initResult = viewModel.init(manga, chapter)
                if (!initResult.getOrDefault(false)) {
                    val exception = initResult.exceptionOrNull() ?: IllegalStateException("Unknown error")
                    withContext(Dispatchers.Main) {
                        setInitialChapterError(exception)
                    }
                }
            }
        }

        config = ReaderConfig()

        // Push chapters into the viewer whenever the chapter list changes. The first emission
        // (after `init` loads the initial chapter) is what creates the viewer — see
        // [updateViewer]. Viewer creation only mutates `State.viewer`, not `viewerChapters`,
        // so `distinctUntilChanged` guarantees this flow cannot re-trigger itself.
        viewModel.state
            .map { it.viewerChapters }
            .filterNotNull()
            .distinctUntilChanged()
            .onEach { setChapters(it) }
            .launchIn(lifecycleScope)

        viewModel.eventFlow
            .onEach { event ->
                when (event) {
                    is ReaderViewModel.Event.ReloadViewerChapters -> reloadViewerChapters()
                    is ReaderViewModel.Event.PageChanged -> displayRefreshHost.flash()
                    is ReaderViewModel.Event.SetOrientation -> setOrientation(event.orientation)
                    is ReaderViewModel.Event.SetCoverResult -> onSetAsCoverResult(event.result)
                    is ReaderViewModel.Event.BlockPageResult -> onBlockPageResult(event.result)
                    is ReaderViewModel.Event.SavedImage -> onSaveImageResult(event.result)
                    is ReaderViewModel.Event.ShareImage -> onShareImageResult(event.uri, event.page)
                    is ReaderViewModel.Event.CopyImage -> onCopyImageResult(event.uri)
                }
            }
            .launchIn(lifecycleScope)

        readerPreferences.trueColor().changes()
            .onEach { applyColorLayerPaint() }
            .launchIn(lifecycleScope)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            readerPreferences.drawUnderCutout().changes()
                .onEach { drawUnderCutout ->
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode = if (drawUnderCutout) {
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        } else {
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                        }
                    }
                }
                .launchIn(lifecycleScope)
        }

        if (savedInstanceState != null) {
            menuToggleToast?.cancel()
        }
    }

    fun showNavigationOverlay(navigation: ViewerNavigation, showOnStart: Boolean) {
        if (showOnStart && navigation !is DisabledNavigation) {
            navigationOverlayState = navigation
        }
    }

    override fun onDestroy() {
        currentViewer?.destroy()
        super.onDestroy()
        config = null
        menuToggleToast?.cancel()
        readingModeToast?.cancel()
    }

    override fun onPause() {
        viewModel.onEvent(ReaderEvent.ActivityFinish)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val menuVisible = viewModel.state.value.menuVisible
            setMenuVisibility(menuVisible)
        }
    }

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        assistUrl?.let { outContent.webUri = it.toUri() }
    }

    override fun finish() {
        viewModel.onEvent(ReaderEvent.ActivityFinish)
        super.finish()
        overrideTransitionCompat(
            Activity.OVERRIDE_TRANSITION_CLOSE,
            CoreR.anim.shared_axis_x_pop_enter,
            CoreR.anim.shared_axis_x_pop_exit,
        )
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handled = currentViewer?.handleKeyEvent(event) ?: false
        return handled || super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val handled = currentViewer?.handleGenericMotionEvent(event) ?: false
        return handled || super.dispatchGenericMotionEvent(event)
    }

    private val bottomFlickDetector by lazy {
        android.view.GestureDetector(
            this,
            object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float,
                ): Boolean {
                    if (e1 == null) return false
                    val deltaY = e2.y - e1.y
                    val deltaX = e2.x - e1.x

                    // Only detect flicks starting in the bottom 25% of the screen
                    val screenHeight = resources.displayMetrics.heightPixels
                    val bottomZoneStart = screenHeight * 0.75f

                    if (e1.y >= bottomZoneStart) {
                        // Swift bottom-up flick (velocityY < -1000f, deltaY < -80dp)
                        if (velocityY < -1000f && kotlin.math.abs(deltaY) > 80f &&
                            kotlin.math.abs(deltaY) > kotlin.math.abs(deltaX)
                        ) {
                            currentViewer?.moveToNext()
                            return true
                        }
                    }
                    return false
                }
            },
        )
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        bottomFlickDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun setMenuVisibility(visible: Boolean) {
        viewModel.onEvent(ReaderEvent.ShowMenus(visible))
        if (visible) {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        } else {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun setChapters(chapters: ViewerChapters) {
        updateViewer()
        currentViewer?.setChapters(chapters)
    }

    /**
     * Re-delivers the currently active chapters to the viewer (adapter rebuild). Emitted when
     * the underlying chapter page list changed (page filtering, adjacent-chapter preloads) or
     * after the reading mode / orientation flags were updated.
     */
    private fun reloadViewerChapters() {
        val chapters = viewModel.state.value.viewerChapters ?: return
        setChapters(chapters)
    }

    /**
     * Ensures the Android [Viewer] instance exists and matches the current reading mode.
     */
    private fun updateViewer() {
        val state = viewModel.state.value
        if (state.manga == null) return

        val newType = ReadingMode.fromPreference(viewModel.getMangaReadingMode())
        val existingViewer = currentViewer
        if (existingViewer != null && viewerType == newType) {
            return
        }

        val newViewer = createViewer(newType)
        existingViewer?.destroy()
        currentViewer = newViewer
        viewModel.onEvent(ReaderEvent.ViewerLoaded(newViewer))
        viewerType = newType
        updateViewerInset(true, true)
        applyColorLayerPaint()
    }

    private fun createViewer(readingMode: ReadingMode): Viewer {
        return when (readingMode) {
            ReadingMode.LEFT_TO_RIGHT -> L2RPagerViewer(this, downloadManager, readerPreferences, uiPreferences)
            ReadingMode.RIGHT_TO_LEFT -> R2LPagerViewer(this, downloadManager, readerPreferences, uiPreferences)
            ReadingMode.VERTICAL -> VerticalPagerViewer(this, downloadManager, readerPreferences, uiPreferences)
            ReadingMode.WEBTOON -> WebtoonViewer(
                this,
                downloadManager = downloadManager,
                readerPreferences = readerPreferences,
                uiPreferences = uiPreferences,
                basePreferences = preferences,
            )
            ReadingMode.CONTINUOUS_VERTICAL -> WebtoonViewer(
                this,
                downloadManager = downloadManager,
                readerPreferences = readerPreferences,
                uiPreferences = uiPreferences,
                basePreferences = preferences,
                isContinuous = false,
            )
            ReadingMode.DEFAULT -> {
                logcat(LogPriority.WARN) { "Reading mode DEFAULT while creating viewer; falling back to Webtoon" }
                WebtoonViewer(
                    this,
                    downloadManager = downloadManager,
                    readerPreferences = readerPreferences,
                    uiPreferences = uiPreferences,
                    basePreferences = preferences,
                )
            }
        }
    }

    /**
     * Re-applies the colour layer paint on the current viewer.
     */
    private fun applyColorLayerPaint() {
        val viewer = currentViewer ?: return
        val paint = config?.getCombinedPaint(isNightMode(), readerPreferences.trueColor().getSync())
        viewer.getView().setLayerType(LAYER_TYPE_HARDWARE, paint)
    }

    private fun openMangaScreen() {
        val mangaId = viewModel.state.value.manga?.id ?: return
        navigator.openMangaScreen(this, mangaId)
    }

    private fun openChapterInWebView() {
        val url = viewModel.getChapterUrl() ?: return
        val manga = viewModel.state.value.manga ?: return
        navigator.openWebView(this, url, manga.source, manga.title)
    }

    private fun openChapterInBrowser() {
        val url = viewModel.getChapterUrl() ?: return
        openInBrowser(url)
    }

    private fun shareChapter() {
        val url = viewModel.getChapterUrl() ?: return
        startActivity(url.toUri().toShareIntent(this, "text/plain"))
    }

    private fun showToast(stringRes: Int) {
        readingModeToast?.cancel()
        readingModeToast = toast(stringRes)
    }

    private fun setInitialChapterError(error: Throwable) {
        logcat(LogPriority.ERROR, error) { "Error loading initial chapter" }
        AlertDialog.Builder(this)
            .setMessage(error.message)
            .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun moveToPageIndex(index: Int) {
        val viewer = currentViewer ?: return
        val pages = viewModel.state.value.viewerChapters?.currChapter?.pages
            ?.filterNot { it.isHidden } ?: return
        val page = pages.getOrNull(index) ?: return
        viewer.moveToPage(page)
    }

    fun onPageSelected(page: ReaderPage) {
        viewModel.onEvent(ReaderEvent.PageSelected(page))
    }

    fun onPageLongTap(page: ReaderPage) {
        viewModel.onEvent(ReaderEvent.OpenPageDialog(page))
    }

    fun requestPreloadChapter(chapter: ReaderChapter) {
        lifecycleScope.launchNonCancellable { viewModel.preload(chapter) }
    }

    fun toggleMenu() {
        setMenuVisibility(!viewModel.state.value.menuVisible)
    }

    fun showMenu() {
        setMenuVisibility(true)
    }

    fun hideMenu() {
        setMenuVisibility(false)
    }

    fun onShareImageResult(uri: Uri, page: ReaderPage) {
        startActivity(uri.toShareIntent(this))
    }

    fun onCopyImageResult(uri: Uri) {
        copyToClipboard(uri.toString(), uri.toString())
    }

    fun onSaveImageResult(result: ReaderViewModel.SaveImageResult) {
        when (result) {
            is ReaderViewModel.SaveImageResult.Success -> {
                SaveImageNotifier(this).onComplete(result.uri)
                toast(ephyra.app.core.common.R.string.picture_saved)
            }
            is ReaderViewModel.SaveImageResult.Error -> toast(result.error.message)
        }
    }

    fun onSetAsCoverResult(result: ReaderViewModel.SetAsCoverResult) {
        when (result) {
            Success -> toast(ephyra.app.core.common.R.string.cover_updated)
            AddToLibraryFirst -> toast(ephyra.app.core.common.R.string.notification_first_add_to_library)
            Error -> toast(ephyra.app.core.common.R.string.error_saving_cover)
        }
    }

    fun onBlockPageResult(result: ReaderViewModel.BlockPageResult) {
        when (result) {
            is ReaderViewModel.BlockPageResult.Success -> toast(ephyra.app.core.common.R.string.page_blocked)
            is ReaderViewModel.BlockPageResult.Error -> toast(ephyra.app.core.common.R.string.page_block_error)
        }
    }

    fun setOrientation(orientation: Int) {
        requestedOrientation = orientation
    }

    private fun updateViewerInset(all: Boolean, bottom: Boolean) {
        val viewer = currentViewer ?: return
        val view = viewer.getView()
        view.applyInsetsPadding(ViewCompat.getRootWindowInsets(window.decorView), all, bottom)
    }

    private fun View.applyInsetsPadding(insets: WindowInsetsCompat?, all: Boolean, bottom: Boolean) {
        val systemBars = insets?.getInsets(WindowInsetsCompat.Type.systemBars()) ?: androidx.core.graphics.Insets.NONE
        setPadding(
            if (all) systemBars.left else paddingLeft,
            if (all) systemBars.top else paddingTop,
            if (all) systemBars.right else paddingRight,
            if (all || bottom) systemBars.bottom else paddingBottom,
        )
    }

    inner class ReaderConfig {

        fun getCombinedPaint(isNightMode: Boolean, trueColor: Boolean): Paint {
            return Paint().apply {
                if (isNightMode && !trueColor) {
                    colorFilter = ColorMatrixColorFilter(
                        ColorMatrix().apply {
                            setSaturation(0f)
                            val matrix = floatArrayOf(
                                -1f, 0f, 0f, 0f, 255f,
                                0f, -1f, 0f, 0f, 255f,
                                0f, 0f, -1f, 0f, 255f,
                                0f, 0f, 0f, 1f, 0f,
                            )
                            postConcat(ColorMatrix(matrix))
                        },
                    )
                }
            }
        }

        val grayBackgroundColor = Color.GRAY

        init {
            readerPreferences.readerTheme().changes()
                .onEach { theme ->
                    val color = when (theme) {
                        0 -> Color.WHITE
                        1 -> Color.BLACK
                        2 -> automaticBackgroundColor()
                        else -> Color.GRAY
                    }
                    window.decorView.setBackgroundColor(color)
                    updateViewer()
                }
                .launchIn(lifecycleScope)

            readerPreferences.trueColor().changes()
                .onEach { applyColorLayerPaint() }
                .launchIn(lifecycleScope)

            readerPreferences.fullscreen().changes()
                .onEach { fullscreen ->
                    if (fullscreen) {
                        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                    } else {
                        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                    }
                }
                .launchIn(lifecycleScope)

            readerPreferences.keepScreenOn().changes()
                .onEach { setKeepScreenOn(it) }
                .launchIn(lifecycleScope)

            readerPreferences.customBrightness().changes()
                .onEach { setCustomBrightness(it) }
                .launchIn(lifecycleScope)

            readerPreferences.customBrightnessValue().changes()
                .onEach { setCustomBrightnessValue(it) }
                .launchIn(lifecycleScope)

            readerPreferences.colorFilter().changes()
                .onEach { applyColorLayerPaint() }
                .launchIn(lifecycleScope)

            readerPreferences.colorFilterValue().changes()
                .onEach { applyColorLayerPaint() }
                .launchIn(lifecycleScope)

            readerPreferences.colorFilterMode().changes()
                .onEach { applyColorLayerPaint() }
                .launchIn(lifecycleScope)
        }

        private fun automaticBackgroundColor(): Int {
            return if (isNightMode()) {
                Color.BLACK
            } else {
                Color.WHITE
            }
        }

        fun setDisplayProfile(data: String) {
            TachiyomiImageDecoder.displayProfile = if (data.isNotEmpty()) {
                try {
                    contentResolver.openInputStream(data.toUri())?.readBytes()
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) {
                        "Failed to read display ICC profile from URI; disabling colour management"
                    }
                    null
                }
            } else {
                null
            }
            updateViewer()
        }

        fun setKeepScreenOn(enabled: Boolean) {
            if (enabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        fun setCustomBrightness(enabled: Boolean) {
            if (enabled) {
                setCustomBrightnessValue(readerPreferences.customBrightnessValue().getSync())
            } else {
                val layoutParams = window.attributes
                layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = layoutParams
            }
        }

        fun setCustomBrightnessValue(value: Int) {
            if (readerPreferences.customBrightness().getSync()) {
                val layoutParams = window.attributes
                layoutParams.screenBrightness = value / 100f
                window.attributes = layoutParams
            }
        }

        fun setLayerPaint(isNightMode: Boolean, trueColor: Boolean) {
            val viewer = currentViewer ?: return
            viewer.getView().setLayerType(LAYER_TYPE_HARDWARE, getCombinedPaint(isNightMode, trueColor))
        }
    }
}
