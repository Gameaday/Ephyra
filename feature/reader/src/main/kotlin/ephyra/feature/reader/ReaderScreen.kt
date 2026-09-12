package ephyra.feature.reader

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ephyra.domain.reader.model.ReaderOrientation
import ephyra.domain.reader.model.ReadingMode
import ephyra.domain.reader.service.ReaderPreferences
import ephyra.feature.reader.setting.ReaderSettingsViewModel
import ephyra.feature.reader.viewer.Viewer
import ephyra.feature.reader.viewer.ViewerNavigation
import ephyra.feature.reader.viewer.pager.ComposePagerReader
import ephyra.feature.reader.viewer.pager.PagerViewer
import ephyra.presentation.core.util.collectAsState
import ephyra.presentation.reader.DisplayRefreshHost
import ephyra.presentation.reader.ReaderContentOverlay
import ephyra.presentation.reader.ReaderPageActionsSheet
import ephyra.presentation.reader.ReaderPageIndicator
import ephyra.presentation.reader.appbars.ReaderAppBars
import ephyra.presentation.reader.settings.ReaderSettingsSheet
import eu.kanade.tachiyomi.source.online.HttpSource

/**
 * Native Jetpack Compose screen hosting the reader pipeline:
 *  1. Active viewer hardware canvas (AndroidView)
 *  2. Tap navigation zones overlay
 *  3. Brightness, color filter, and flash refresh overlays
 *  4. Top and bottom navigation app bars
 *  5. Loading and page indicator badges
 *  6. Reader configuration and action dialogs
 */
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    currentViewer: Viewer?,
    navigationOverlay: ViewerNavigation?,
    onDismissNavigationOverlay: () -> Unit,
    readerPreferences: ReaderPreferences,
    displayRefreshHost: DisplayRefreshHost,
    onNavigateUp: () -> Unit,
    onClickTopAppBar: () -> Unit,
    onOpenInWebView: (() -> Unit)?,
    onOpenInBrowser: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onNextChapter: () -> Unit,
    onPreviousChapter: () -> Unit,
    onPageIndexChange: (Int) -> Unit,
    showToast: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Viewer Surface
        key(currentViewer) {
            val context = LocalContext.current
            val activity = context as? ReaderActivity
            if (currentViewer is PagerViewer && activity != null) {
                ComposePagerReader(
                    viewer = currentViewer,
                    onPageSelected = activity::onPageSelected,
                    onPageLongTap = activity::onPageLongTap,
                    onToggleMenu = activity::toggleMenu,
                    onRequestPreload = activity::requestPreloadChapter,
                    onNextChapter = onNextChapter,
                    onPreviousChapter = onPreviousChapter,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (currentViewer != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        val view = currentViewer.getView()
                        (view.parent as? ViewGroup)?.removeView(view)
                        view
                    },
                )
            }
        }

        // 2. Tap Navigation Overlay
        if (navigationOverlay != null) {
            ReaderNavigationOverlay(
                navigation = navigationOverlay,
                onDismiss = onDismissNavigationOverlay,
            )
        }

        // 3. Content Overlays (Brightness, Color Filter, E-ink Flash)
        ContentOverlay(
            state = state,
            readerPreferences = readerPreferences,
            displayRefreshHost = displayRefreshHost,
        )

        // 4. AppBars
        AppBars(
            state = state,
            viewModel = viewModel,
            currentViewer = currentViewer,
            readerPreferences = readerPreferences,
            onNavigateUp = onNavigateUp,
            onClickTopAppBar = onClickTopAppBar,
            onOpenInWebView = onOpenInWebView,
            onOpenInBrowser = onOpenInBrowser,
            onShare = onShare,
            onNextChapter = onNextChapter,
            onPreviousChapter = onPreviousChapter,
            onPageIndexChange = onPageIndexChange,
        )

        // 5. Initial Loading Indicator
        if (state.currentPage == -1 && state.currentChapter != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        // 6. Page Number Badge
        val currentChapter = state.currentChapter
        if (currentChapter != null) {
            val showPageNumber: Boolean by readerPreferences.showPageNumber().collectAsState()
            if (showPageNumber) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ReaderPageIndicator(
                        currentPage = state.currentPage,
                        totalPages = state.totalPages,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp),
                    )
                }
            }
        }

        // 7. Dialogs & Sheets
        val dialog = state.dialog
        if (dialog != null) {
            when (dialog) {
                is ReaderViewModel.Dialog.Loading -> { /* Handled by successState logic */ }
                is ReaderViewModel.Dialog.Settings -> {
                    ReaderSettingsSheet(
                        initialPage = dialog.initialPage,
                        onDismissRequest = { viewModel.onEvent(ReaderEvent.CloseDialog) },
                        onShowMenus = { viewModel.onEvent(ReaderEvent.ShowMenus(true)) },
                        onHideMenus = { viewModel.onEvent(ReaderEvent.ShowMenus(false)) },
                        viewModel = ReaderSettingsViewModel(
                            scope = scope,
                            readerState = viewModel.state,
                            onChangeReadingMode = { viewModel.onEvent(ReaderEvent.SetMangaReadingMode(it)) },
                            onChangeOrientation = { viewModel.onEvent(ReaderEvent.SetMangaOrientationType(it)) },
                            preferences = readerPreferences,
                        ),
                    )
                }
                is ReaderViewModel.Dialog.PageActions -> {
                    ReaderPageActionsSheet(
                        onDismissRequest = { viewModel.onEvent(ReaderEvent.CloseDialog) },
                        onSetAsCover = { viewModel.onEvent(ReaderEvent.SetAsCover) },
                        onShare = { viewModel.onEvent(ReaderEvent.ShareImage(it)) },
                        onSave = { viewModel.onEvent(ReaderEvent.SaveImage) },
                        onBlockPage = { viewModel.onEvent(ReaderEvent.BlockPage) },
                        onUnblockPage = { viewModel.onEvent(ReaderEvent.UnblockPage(it)) },
                        findMatchingBlockedHash = viewModel::findMatchingBlockedHash,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderNavigationOverlay(
    navigation: ViewerNavigation,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isVisible by remember { mutableStateOf(true) }
    val textMeasurer = rememberTextMeasurer()
    val context = LocalContext.current
    val textStyle = remember {
        TextStyle(
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            shadow = Shadow(
                color = Color.Black,
                blurRadius = 8f,
            ),
        )
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(animationSpec = tween(1000)),
        modifier = modifier.fillMaxSize(),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures {
                        isVisible = false
                        onDismiss()
                    }
                },
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            navigation.getRegions().forEach { region ->
                val rect = region.rectF
                val left = rect.left * canvasWidth
                val top = rect.top * canvasHeight
                val width = (rect.right - rect.left) * canvasWidth
                val height = (rect.bottom - rect.top) * canvasHeight

                drawRect(
                    color = Color(region.type.color),
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                )

                val label = context.getString(region.type.nameRes)
                val measuredText = textMeasurer.measure(
                    text = label,
                    style = textStyle,
                )
                val textX = left + (width - measuredText.size.width) / 2f
                val textY = top + (height - measuredText.size.height) / 2f
                drawText(measuredText, topLeft = Offset(textX, textY))
            }
        }
    }
}

@Composable
private fun ContentOverlay(
    state: ReaderViewModel.State,
    readerPreferences: ReaderPreferences,
    displayRefreshHost: DisplayRefreshHost,
) {
    val flashOnPageChange: Boolean by readerPreferences.flashOnPageChange().collectAsState()

    val colorOverlayEnabled: Boolean by readerPreferences.colorFilter().collectAsState()
    val colorOverlay: Int by readerPreferences.colorFilterValue().collectAsState()
    val colorOverlayMode: Int by readerPreferences.colorFilterMode().collectAsState()
    val colorOverlayBlendMode = remember(colorOverlayMode) {
        when (colorOverlayMode) {
            1 -> BlendMode.Modulate
            2 -> BlendMode.Screen
            3 -> BlendMode.Overlay
            4 -> BlendMode.Lighten
            5 -> BlendMode.Darken
            else -> BlendMode.SrcOver
        }
    }

    ReaderContentOverlay(
        brightness = state.brightnessOverlayValue,
        color = colorOverlay.takeIf { colorOverlayEnabled },
        colorBlendMode = colorOverlayBlendMode,
    )

    if (flashOnPageChange) {
        DisplayRefreshHost(hostState = displayRefreshHost)
    }
}

@Composable
private fun AppBars(
    state: ReaderViewModel.State,
    viewModel: ReaderViewModel,
    currentViewer: Viewer?,
    readerPreferences: ReaderPreferences,
    onNavigateUp: () -> Unit,
    onClickTopAppBar: () -> Unit,
    onOpenInWebView: (() -> Unit)?,
    onOpenInBrowser: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onNextChapter: () -> Unit,
    onPreviousChapter: () -> Unit,
    onPageIndexChange: (Int) -> Unit,
) {
    if (state.manga == null) return

    val cropBorderPaged: Boolean by readerPreferences.cropBorders().collectAsState()
    val cropBorderWebtoon: Boolean by readerPreferences.cropBordersWebtoon().collectAsState()
    val isPagerType = ReadingMode.isPagerType(viewModel.getMangaReadingMode())
    val cropEnabled = if (isPagerType) cropBorderPaged else cropBorderWebtoon

    ReaderAppBars(
        visible = state.menuVisible,
        mangaTitle = state.manga.title,
        chapterTitle = state.currentChapter?.chapter?.name,
        navigateUp = onNavigateUp,
        onClickTopAppBar = onClickTopAppBar,
        bookmarked = state.bookmarked,
        onToggleBookmarked = { viewModel.onEvent(ReaderEvent.ToggleChapterBookmark) },
        onOpenInWebView = onOpenInWebView,
        onOpenInBrowser = onOpenInBrowser,
        onShare = onShare,
        viewer = currentViewer,
        onNextChapter = onNextChapter,
        enabledNext = state.viewerChapters?.nextChapter != null,
        onPreviousChapter = onPreviousChapter,
        enabledPrevious = state.viewerChapters?.prevChapter != null,
        currentPage = state.currentPage,
        totalPages = state.totalPages,
        onPageIndexChange = onPageIndexChange,
        readingMode = ReadingMode.fromPreference(viewModel.getMangaReadingMode()),
        onClickReadingMode = { viewModel.onEvent(ReaderEvent.OpenReadingModeSelectDialog) },
        orientation = ReaderOrientation.fromPreference(viewModel.getMangaOrientation()),
        onClickOrientation = { viewModel.onEvent(ReaderEvent.OpenOrientationModeSelectDialog) },
        cropEnabled = cropEnabled,
        onClickCropBorder = { viewModel.onEvent(ReaderEvent.ToggleCropBorders) },
        onClickSettings = { viewModel.onEvent(ReaderEvent.OpenSettingsDialog) },
    )
}
