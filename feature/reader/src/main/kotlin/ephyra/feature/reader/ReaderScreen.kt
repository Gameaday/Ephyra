package ephyra.feature.reader

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.res.stringResource
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
import ephyra.feature.reader.viewer.extractEdgeColor
import ephyra.feature.reader.viewer.pager.ComposePagerReader
import ephyra.feature.reader.viewer.pager.PagerViewer
import ephyra.feature.reader.viewer.webtoon.ComposeWebtoonReader
import ephyra.feature.reader.viewer.webtoon.WebtoonViewer
import ephyra.presentation.core.theme.MotionTokens
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
    viewerHost: ReaderViewerHost,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // "Automatic" theme: the canvas takes its tint from the border pixels of the page on
    // screen. Sampling walks only the outer band of a downscaled copy, off the main thread,
    // so changing pages never stalls a frame; the shift itself is animated rather than
    // snapped, which avoids a hard cut between pages of differing tone.
    val readerTheme: Int by readerPreferences.readerTheme().collectAsState()
    val automaticBackground = readerTheme == ReaderPreferences.THEME_AUTOMATIC

    // R-C (Stage 4, audit F7): the automatic background samples the *rendered* page — the page
    // the reader actually selected. The old read indexed `pages[getOrNull(state.currentPage)]`,
    // but `currentPage` is a display position (it counts only visible pages) that the pager
    // reports as "position of the page being shown + 1" during settlement, so with nothing
    // hidden it resolved to the *next* page's bytes: the tint tracked the upcoming page instead
    // of the one on screen. The view model now records the selected page itself in
    // `state.currentReaderPage`; reading its resource here cannot drift from what the user
    // sees, and no display-to-raw index arithmetic exists in the UI layer at all.
    //
    // Falls back to null (and therefore to the themed surface colour) whenever the page has
    // not been decoded into memory yet, so the canvas never flashes an arbitrary tint.
    val renderedSurfaceBytes = state.currentReaderPage?.resource?.renderedSurfaceBytes

    val sampledEdgeColor by produceState<Color?>(
        initialValue = null,
        automaticBackground,
        renderedSurfaceBytes,
    ) {
        value = if (automaticBackground && renderedSurfaceBytes != null) {
            extractEdgeColor(renderedSurfaceBytes)?.let { Color(it) }
        } else {
            null
        }
    }

    val canvasColor by animateColorAsState(
        targetValue = sampledEdgeColor ?: MaterialTheme.colorScheme.surface,
        animationSpec = tween(
            durationMillis = MotionTokens.DURATION_MEDIUM_3,
            easing = MotionTokens.EasingStandard,
        ),
        label = "ReaderBackgroundColor",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (automaticBackground) Modifier.background(canvasColor) else Modifier),
    ) {
        // 1. Viewer Surface
        //
        // R-D (Stage 2 of the reader modular redesign, 2026-11-08): the viewer surfaces route
        // their host callbacks through the injected ReaderViewerHost instead of a
        // `context as? ReaderActivity` cast - the rendering path no longer knows the concrete
        // host class, and it can no longer silently not render because a cast returned null (no
        // production context produced one: the screen is composed only by ReaderActivity, which
        // implements the contract).
        key(currentViewer) {
            if (currentViewer is PagerViewer) {
                ComposePagerReader(
                    viewer = currentViewer,
                    onPageSelected = viewerHost::onPageSelected,
                    onPageLongTap = viewerHost::onPageLongTap,
                    onToggleMenu = viewerHost::toggleMenu,
                    onRequestPreload = viewerHost::requestPreloadChapter,
                    onNextChapter = onNextChapter,
                    onPreviousChapter = onPreviousChapter,
                    onReturnToSeries = onNavigateUp,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (currentViewer is WebtoonViewer) {
                ComposeWebtoonReader(
                    viewer = currentViewer,
                    onPageSelected = viewerHost::onPageSelected,
                    onPageLongTap = viewerHost::onPageLongTap,
                    onToggleMenu = viewerHost::toggleMenu,
                    onRequestPreload = viewerHost::requestPreloadChapter,
                    onNextChapter = onNextChapter,
                    onPreviousChapter = onPreviousChapter,
                    onReturnToSeries = onNavigateUp,
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
    val regionLabels = navigation.getRegions().associate { it.type to stringResource(it.type.nameRes) }
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
        exit = fadeOut(animationSpec = tween(MotionTokens.DURATION_EXTRA_LONG_4)),
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

                val label = regionLabels[region.type].orEmpty()
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
    readerPreferences: ReaderPreferences,
    displayRefreshHost: DisplayRefreshHost,
) {
    val flashOnPageChange: Boolean by readerPreferences.flashOnPageChange().collectAsState()

    // R-B (Stage 1 of the reader modular redesign, 2026-11-08): brightness is read
    // reactively here instead of being relayed through ViewModel state. The overlay is
    // the single pixel owner of the dim scrim (negative slider values) and the colour
    // filter; the Activity keeps only window-level brightness.
    val customBrightnessEnabled: Boolean by readerPreferences.customBrightness().collectAsState()
    val customBrightnessValue: Int by readerPreferences.customBrightnessValue().collectAsState()

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
        brightness = if (customBrightnessEnabled) customBrightnessValue else 0,
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
