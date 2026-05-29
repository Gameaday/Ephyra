package ephyra.feature.manga

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.logcat
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.content.model.toContentItem
import ephyra.domain.content.model.toContentUnit
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.hasCustomCover
import ephyra.domain.manga.model.toSManga
import ephyra.feature.category.presentation.components.ChangeCategoryDialog
import ephyra.feature.manga.presentation.ChapterSettingsDialog
import ephyra.feature.manga.presentation.DuplicateMangaDialog
import ephyra.feature.manga.presentation.EditCoverAction
import ephyra.feature.manga.presentation.MangaScreen
import ephyra.feature.manga.presentation.components.CoverSearchDialog
import ephyra.feature.manga.presentation.components.DeleteChaptersDialog
import ephyra.feature.manga.presentation.components.EditMetadataDialog
import ephyra.feature.manga.presentation.components.MangaCoverDialog
import ephyra.feature.manga.presentation.components.ScanlatorFilterDialog
import ephyra.feature.manga.presentation.components.SetIntervalDialog
import ephyra.feature.migration.dialog.MigrateMangaDialog
import ephyra.feature.reader.ReaderActivity
import ephyra.presentation.core.feature.SafeFeatureContainer
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.Screen
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import ephyra.presentation.core.ui.viewer.MediaViewerRegistry
import ephyra.presentation.core.util.ifSourcesLoaded
import ephyra.presentation.core.util.isTabletUi
import ephyra.presentation.core.util.system.copyToClipboard
import ephyra.presentation.core.util.system.toShareIntent
import ephyra.presentation.core.util.system.toast
import ephyra.source.local.isLocalOrStub
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import logcat.LogPriority

@Composable
fun MangaDetailsScreen(
    mangaId: Long,
    fromSource: Boolean = false,
    navController: NavController = LocalNavController.current,
    navigateUp: () -> Unit = { navController.popBackStack() },
    mediaViewerRegistry: MediaViewerRegistry,
    onAssistUrlComputed: (String?) -> Unit = {},
) {
    SafeFeatureContainer(
        featureName = "MangaDetails",
        viewModelClass = MangaScreenModel::class.java,
        onBack = navigateUp,
    ) { screenModel ->
        MangaDetailsScreen(
            mangaId = mangaId,
            fromSource = fromSource,
            screenModel = screenModel,
            navController = navController,
            navigateUp = navigateUp,
            mediaViewerRegistry = mediaViewerRegistry,
            onAssistUrlComputed = onAssistUrlComputed,
        )
    }
}

@Composable
fun MangaDetailsScreen(
    mangaId: Long,
    fromSource: Boolean = false,
    screenModel: MangaScreenModel,
    navController: NavController,
    mediaViewerRegistry: MediaViewerRegistry,
    navigateUp: () -> Unit = { navController.popBackStack() },
    onAssistUrlComputed: (String?) -> Unit = {},
) {
    if (!ifSourcesLoaded()) {
        LoadingScreen()
        return
    }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(mangaId, fromSource) {
        screenModel.init(mangaId, fromSource)
    }

    val state by screenModel.state.collectAsStateWithLifecycle()

    if (state is MangaScreenModel.State.Loading) {
        LoadingScreen()
        return
    }

    val successState = state as MangaScreenModel.State.Success
    val isHttpSource = remember { successState.source is HttpSource }

    LaunchedEffect(successState.manga, screenModel.source) {
        if (isHttpSource) {
            try {
                withIOContext {
                    val url = getMangaUrl(screenModel.manga, screenModel.source)
                    onAssistUrlComputed(url)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to get manga URL" }
            }
        }
    }

    MangaScreen(
        state = successState,
        snackbarHostState = screenModel.snackbarHostState,
        nextUpdate = successState.manga.expectedNextUpdate,
        isTabletUi = isTabletUi(),
        chapterSwipeStartAction = successState.chapterSwipeStartAction,
        chapterSwipeEndAction = successState.chapterSwipeEndAction,
        navigateUp = navigateUp,
        onChapterClicked = { openChapter(context, navController, mediaViewerRegistry, successState.manga, it) },
        onDownloadChapter = if (!successState.source.isLocalOrStub()) {
            { items, action -> screenModel.onEvent(MangaScreenEvent.RunChapterDownloadActions(items, action)) }
        } else {
            null
        },
        onAddToLibraryClicked = {
            screenModel.onEvent(MangaScreenEvent.ToggleFavorite())
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        },
        onWebViewClicked = if (isHttpSource) {
            {
                openMangaInWebView(
                    navController,
                    screenModel.manga,
                    screenModel.source,
                )
            }
        } else {
            null
        },
        onWebViewLongClicked = if (isHttpSource) {
            {
                copyMangaUrl(
                    context,
                    screenModel.manga,
                    screenModel.source,
                )
            }
        } else {
            null
        },
        onTrackingClicked = {
            if (!successState.hasLoggedInTrackers) {
                navController.navigate(ScreenRoutes.Settings.route)
            } else {
                screenModel.onEvent(MangaScreenEvent.ShowTrackDialog)
            }
        },
        onTagSearch = {
            scope.launch {
                performGenreSearch(
                    navController,
                    it,
                )
            }
        },
        onFilterButtonClicked = { screenModel.onEvent(MangaScreenEvent.ShowSettingsDialog) },
        onRefresh = { screenModel.onEvent(MangaScreenEvent.FetchAllFromSource(manualFetch = true)) },
        onContinueReading = {
            continueReading(
                context,
                navController,
                mediaViewerRegistry,
                successState.manga,
                screenModel.getNextUnreadChapter(),
            )
        },
        onSearch = { query, global ->
            scope.launch {
                performSearch(
                    navController,
                    query,
                    global,
                )
            }
        },
        onCoverClicked = { screenModel.onEvent(MangaScreenEvent.ShowCoverDialog) },
        onShareClicked = if (isHttpSource) {
            { shareManga(context, screenModel.manga, screenModel.source) }
        } else {
            null
        },
        onDownloadActionClicked = if (!successState.source.isLocalOrStub()) {
            { screenModel.onEvent(MangaScreenEvent.RunDownloadAction(it)) }
        } else {
            null
        },
        onEditCategoryClicked = if (successState.manga.favorite) {
            { screenModel.onEvent(MangaScreenEvent.ShowChangeCategoryDialog) }
        } else {
            null
        },
        onEditFetchIntervalClicked = if (successState.manga.favorite) {
            { screenModel.onEvent(MangaScreenEvent.ShowSetFetchIntervalDialog) }
        } else {
            null
        },
        onMigrateClicked = if (successState.manga.favorite) {
            {
                navController.navigate(ScreenRoutes.MigrateManga.createRoute(successState.manga.id))
            }
        } else {
            null
        },
        onEditNotesClicked = {
            navController.navigate(ScreenRoutes.MangaNotes.createRoute(successState.manga.id))
        },
        onEditMetadataClicked = if (successState.manga.favorite || (successState.manga.canonicalId != null)) {
            { screenModel.onEvent(MangaScreenEvent.ShowEditMetadataDialog) }
        } else {
            null
        },
        onMultiBookmarkClicked = { ch, b -> screenModel.onEvent(MangaScreenEvent.BookmarkChapters(ch, b)) },
        onMultiMarkAsReadClicked = { ch, b -> screenModel.onEvent(MangaScreenEvent.MarkChaptersRead(ch, b)) },
        onMarkPreviousAsReadClicked = { screenModel.onEvent(MangaScreenEvent.MarkPreviousChapterRead(it)) },
        onMultiDeleteClicked = { screenModel.onEvent(MangaScreenEvent.ShowDeleteChapterDialog(it)) },
        onChapterSwipe = { ch, sw -> screenModel.onEvent(MangaScreenEvent.ChapterSwipe(ch, sw)) },
        onChapterSelected = { item, selected, fromLongPress ->
            screenModel.onEvent(MangaScreenEvent.ToggleSelection(item, selected, fromLongPress))
        },
        onAllChapterSelected = { screenModel.onEvent(MangaScreenEvent.ToggleAllSelection(it)) },
        onInvertSelection = { screenModel.onEvent(MangaScreenEvent.InvertSelection) },
    )

    var showScanlatorsDialog by remember { mutableStateOf(value = false) }

    val onDismissRequest = { screenModel.onEvent(MangaScreenEvent.DismissDialog) }
    when (val dialog = successState.dialog) {
        null -> {}
        is MangaScreenModel.Dialog.ChangeCategory -> {
            ChangeCategoryDialog(
                initialSelection = dialog.initialSelection.toImmutableList(),
                onDismissRequest = onDismissRequest,
                onEditCategories = {
                    navController.navigate(ephyra.presentation.core.ui.navigation.Screen.Category)
                },
                onConfirm = { include, _ ->
                    screenModel.onEvent(MangaScreenEvent.MoveMangaToCategoriesAndAddToLibrary(dialog.manga, include))
                },
            )
        }
        is MangaScreenModel.Dialog.DeleteChapters -> {
            DeleteChaptersDialog(
                onDismissRequest = onDismissRequest,
                onConfirm = {
                    screenModel.onEvent(MangaScreenEvent.ToggleAllSelection(selected = false))
                    screenModel.onEvent(MangaScreenEvent.DeleteChapters(dialog.chapters))
                },
            )
        }

        is MangaScreenModel.Dialog.DuplicateManga -> {
            DuplicateMangaDialog(
                duplicates = dialog.duplicates,
                onDismissRequest = onDismissRequest,
                onConfirm = { screenModel.onEvent(MangaScreenEvent.ToggleFavorite(checkDuplicate = false)) },
                onOpenManga = {
                    navController.navigate(
                        ephyra.presentation.core.ui.navigation.Screen.MangaDetails(mangaId = it.id, fromSource = false),
                    )
                },
                onMigrate = { screenModel.onEvent(MangaScreenEvent.ShowMigrateDialog(it)) },
                sourceManager = screenModel.sourceManager,
            )
        }

        is MangaScreenModel.Dialog.Migrate -> {
            MigrateMangaDialog(
                current = dialog.current,
                target = dialog.target,
                onClickTitle = {
                    navController.navigate(
                        ephyra.presentation.core.ui.navigation.Screen.MangaDetails(
                            mangaId = dialog.current.id,
                            fromSource = false,
                        ),
                    )
                },
                onDismissRequest = onDismissRequest,
            )
        }
        MangaScreenModel.Dialog.SettingsSheet -> ChapterSettingsDialog(
            basePreferences = screenModel.basePreferences,
            onDismissRequest = onDismissRequest,
            manga = successState.manga,
            onDownloadFilterChanged = { screenModel.onEvent(MangaScreenEvent.SetDownloadedFilter(it)) },
            onUnreadFilterChanged = { screenModel.onEvent(MangaScreenEvent.SetUnreadFilter(it)) },
            onBookmarkedFilterChanged = { screenModel.onEvent(MangaScreenEvent.SetBookmarkedFilter(it)) },
            onSortModeChanged = { screenModel.onEvent(MangaScreenEvent.SetSorting(it)) },
            onDisplayModeChanged = { screenModel.onEvent(MangaScreenEvent.SetDisplayMode(it)) },
            onSetAsDefault = { screenModel.onEvent(MangaScreenEvent.SetCurrentSettingsAsDefault(it)) },
            onResetToDefault = { screenModel.onEvent(MangaScreenEvent.ResetToDefaultSettings) },
            scanlatorFilterActive = successState.scanlatorFilterActive,
            onScanlatorFilterClicked = { showScanlatorsDialog = true },
        )
        MangaScreenModel.Dialog.TrackSheet -> {
            ephyra.feature.manga.track.TrackInfoDialog(
                manga = successState.manga,
                source = successState.source,
                onDismissRequest = onDismissRequest,
            )
        }
        MangaScreenModel.Dialog.FullCover -> {
            val sm = hiltViewModel<MangaCoverScreenModel>()
            LaunchedEffect(successState.manga.id) {
                sm.init(successState.manga.id)
            }
            LaunchedEffect(sm) {
                sm.effectFlow.collect { effect ->
                    when (effect) {
                        is MangaCoverEffect.StartShare -> {
                            val intent = effect.uri.toShareIntent(context, type = "image/*")
                            context.startActivity(intent)
                        }
                    }
                }
            }
            val manga by sm.state.collectAsStateWithLifecycle()
            if (manga != null) {
                val getContent = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
                    if (it == null) return@rememberLauncherForActivityResult
                    sm.onEvent(MangaCoverScreenEvent.EditCover(it))
                }
                var showCoverSearch by remember { mutableStateOf(value = false) }
                if (showCoverSearch) {
                    val coverSearchSm = hiltViewModel<CoverSearchScreenModel>()
                    LaunchedEffect(manga!!.title, successState.source.id) {
                        coverSearchSm.init(
                            mangaTitle = manga!!.title,
                            currentSourceId = successState.source.id,
                        )
                        coverSearchSm.search()
                    }
                    val coverSearchState by coverSearchSm.state.collectAsStateWithLifecycle()
                    CoverSearchDialog(
                        state = coverSearchState,
                        onCoverSelected = { cover ->
                            sm.onEvent(MangaCoverScreenEvent.SetCoverFromUrl(cover.thumbnailUrl, cover.sourceId))
                            showCoverSearch = false
                        },
                        onSetAsMetadataSource = { cover ->
                            screenModel.onEvent(MangaScreenEvent.SetMetadataSource(cover.sourceId, cover.mangaUrl))
                            showCoverSearch = false
                        },
                        onRefresh = { coverSearchSm.onEvent(CoverSearchScreenEvent.Refresh) },
                        onDismissRequest = { showCoverSearch = false },
                    )
                } else {
                    MangaCoverDialog(
                        manga = manga!!,
                        snackbarHostState = sm.snackbarHostState,
                        isCustomCover = remember(manga) { manga!!.hasCustomCover(screenModel.coverCache) },
                        onShareClick = { sm.onEvent(MangaCoverScreenEvent.ShareCover) },
                        onSaveClick = { sm.onEvent(MangaCoverScreenEvent.SaveCover) },
                        onEditClick = {
                            when (it) {
                                EditCoverAction.EDIT -> getContent.launch("image/*")
                                EditCoverAction.DELETE -> sm.onEvent(MangaCoverScreenEvent.DeleteCustomCover)
                                EditCoverAction.SEARCH -> {
                                    showCoverSearch = true
                                }
                            }
                        },
                        onDismissRequest = onDismissRequest,
                    )
                }
            } else {
                LoadingScreen(Modifier.systemBarsPadding())
            }
        }
        is MangaScreenModel.Dialog.SetFetchInterval -> {
            SetIntervalDialog(
                interval = dialog.manga.fetchInterval,
                nextUpdate = dialog.manga.expectedNextUpdate,
                onDismissRequest = onDismissRequest,
                onValueChanged = { interval: Int ->
                    screenModel.onEvent(MangaScreenEvent.SetFetchInterval(dialog.manga, interval))
                }
                    .takeIf { successState.isUpdateIntervalEnabled },
                appInfo = screenModel.appInfo,
            )
        }
        MangaScreenModel.Dialog.EditMetadata -> {
            val manga = successState.manga
            val authorityLabel = remember(manga.canonicalId) {
                manga.canonicalId?.let { ephyra.domain.manga.model.CanonicalId.toLabel(it) }
            }
            EditMetadataDialog(
                title = manga.title,
                author = manga.author,
                artist = manga.artist,
                description = manga.description,
                status = manga.status,
                genres = manga.genre ?: emptyList(),
                lockedFields = manga.lockedFields,
                hasAuthority = manga.canonicalId != null,
                authorityLabel = authorityLabel,
                onSaveTitle = { screenModel.onEvent(MangaScreenEvent.EditTitle(it)) },
                onSaveAuthor = { screenModel.onEvent(MangaScreenEvent.EditAuthor(it)) },
                onSaveArtist = { screenModel.onEvent(MangaScreenEvent.EditArtist(it)) },
                onSaveDescription = { screenModel.onEvent(MangaScreenEvent.EditDescription(it)) },
                onSaveStatus = { screenModel.onEvent(MangaScreenEvent.EditStatus(it)) },
                onSaveGenres = { screenModel.onEvent(MangaScreenEvent.EditGenres(it)) },
                onToggleLock = { screenModel.onEvent(MangaScreenEvent.ToggleLockedField(it)) },
                onSetAllLocks = { mask -> screenModel.onEvent(MangaScreenEvent.SetLockedFields(mask)) },
                onIdentify = if (manga.canonicalId == null) {
                    {
                        screenModel.onEvent(MangaScreenEvent.DismissDialog)
                        screenModel.onEvent(MangaScreenEvent.ResolveCanonicalId)
                    }
                } else {
                    {
                        screenModel.onEvent(MangaScreenEvent.DismissDialog)
                        screenModel.onEvent(MangaScreenEvent.RefreshFromAuthority)
                    }
                },
                onUnlinkAuthority = if (manga.canonicalId != null) {
                    {
                        screenModel.onEvent(MangaScreenEvent.DismissDialog)
                        screenModel.onEvent(MangaScreenEvent.UnlinkAuthority)
                    }
                } else {
                    null
                },
                onDismissRequest = onDismissRequest,
            )
        }
    }

    if (showScanlatorsDialog) {
        ScanlatorFilterDialog(
            availableScanlators = successState.availableScanlators,
            excludedScanlators = successState.excludedScanlators,
            onDismissRequest = { showScanlatorsDialog = false },
            onConfirm = { screenModel.onEvent(MangaScreenEvent.SetExcludedScanlators(it)) },
        )
    }
}

private fun continueReading(
    context: Context,
    navController: NavController,
    mediaViewerRegistry: MediaViewerRegistry,
    manga: Manga,
    unreadChapter: Chapter?,
) {
    unreadChapter?.let { openChapter(context, navController, mediaViewerRegistry, manga, it) }
}

private fun openChapter(
    context: Context,
    navController: NavController,
    mediaViewerRegistry: MediaViewerRegistry,
    manga: Manga,
    chapter: Chapter,
) {
    val contentItem = manga.toContentItem()
    val contentUnit = chapter.toContentUnit()
    val launched = mediaViewerRegistry.launch(navController, contentItem, contentUnit)
    if (!launched) {
        context.startActivity(ReaderActivity.newIntent(context, chapter.mangaId, chapter.id))
    }
}

private fun getMangaUrl(manga: Manga?, source: Source?): String? {
    val m = manga ?: return null
    val s = source as? HttpSource ?: return null

    return try {
        s.getMangaUrl(m.toSManga())
    } catch (_: Exception) {
        null
    }
}

private fun openMangaInWebView(navController: NavController, manga: Manga?, source: Source?) {
    getMangaUrl(manga, source)?.let { url ->
        navController.navigate(
            Screen.WebView(
                url = url,
                title = manga?.title,
                sourceId = source?.id,
            ),
        )
    }
}

private fun shareManga(context: Context, manga: Manga?, source: Source?) {
    try {
        getMangaUrl(manga, source)?.let { url ->
            val intent = url.toUri().toShareIntent(context, type = "text/plain")
            context.startActivity(intent)
        }
    } catch (e: Exception) {
        context.toast(e.message)
    }
}

private fun performSearch(
    navController: NavController,
    query: String,
    global: Boolean,
) {
    if (global) {
        navController.navigate(Screen.GlobalSearch(query))
        return
    }
    // TODO: implement logic to pass search query back if needed, or just navigate to search
    navController.navigate(Screen.GlobalSearch(query))
}

private fun performGenreSearch(
    navController: NavController,
    genreName: String,
) {
    // TODO: implement logic to pass genre search query back if needed
    performSearch(
        navController,
        genreName,
        global = false,
    )
}

private fun copyMangaUrl(context: Context, manga: Manga?, source: Source?) {
    val m = manga ?: return
    val s = source as? HttpSource ?: return
    val url = s.getMangaUrl(m.toSManga())
    context.copyToClipboard(url, url)
}
