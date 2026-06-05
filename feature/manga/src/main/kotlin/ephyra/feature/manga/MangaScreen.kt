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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import ephyra.presentation.core.components.ChangeCategoryDialog
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
        viewModelClass = MangaViewModel::class.java,
        onBack = navigateUp,
    ) { ViewModel ->
        MangaDetailsScreen(
            mangaId = mangaId,
            fromSource = fromSource,
            ViewModel = ViewModel,
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
    ViewModel: MangaViewModel,
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
        ViewModel.init(mangaId, fromSource)
    }

    val state by ViewModel.state.collectAsStateWithLifecycle()

    if (state is MangaViewModel.State.Loading) {
        LoadingScreen()
        return
    }

    val successState = state as MangaViewModel.State.Success
    val isHttpSource = remember { successState.source is HttpSource }

    LaunchedEffect(successState.manga, ViewModel.source) {
        if (isHttpSource) {
            try {
                withIOContext {
                    val url = getMangaUrl(ViewModel.manga, ViewModel.source)
                    onAssistUrlComputed(url)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to get manga URL" }
            }
        }
    }

    MangaScreen(
        state = successState,
        snackbarHostState = ViewModel.snackbarHostState,
        nextUpdate = successState.manga.expectedNextUpdate,
        isTabletUi = isTabletUi(),
        chapterSwipeStartAction = successState.chapterSwipeStartAction,
        chapterSwipeEndAction = successState.chapterSwipeEndAction,
        navigateUp = navigateUp,
        onChapterClicked = { openChapter(context, navController, mediaViewerRegistry, successState.manga, it) },
        onDownloadChapter = if (!successState.source.isLocalOrStub()) {
            { items, action -> ViewModel.onEvent(MangaScreenEvent.RunChapterDownloadActions(items, action)) }
        } else {
            null
        },
        onAddToLibraryClicked = {
            ViewModel.onEvent(MangaScreenEvent.ToggleFavorite())
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        },
        onWebViewClicked = if (isHttpSource) {
            {
                openMangaInWebView(
                    navController,
                    ViewModel.manga,
                    ViewModel.source,
                )
            }
        } else {
            null
        },
        onWebViewLongClicked = if (isHttpSource) {
            {
                copyMangaUrl(
                    context,
                    ViewModel.manga,
                    ViewModel.source,
                )
            }
        } else {
            null
        },
        onTrackingClicked = {
            if (!successState.hasLoggedInTrackers) {
                navController.navigate(ScreenRoutes.Settings.route)
            } else {
                ViewModel.onEvent(MangaScreenEvent.ShowTrackDialog)
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
        onFilterButtonClicked = { ViewModel.onEvent(MangaScreenEvent.ShowSettingsDialog) },
        onRefresh = { ViewModel.onEvent(MangaScreenEvent.FetchAllFromSource(manualFetch = true)) },
        onContinueReading = {
            continueReading(
                context,
                navController,
                mediaViewerRegistry,
                successState.manga,
                ViewModel.getNextUnreadChapter(),
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
        onCoverClicked = { ViewModel.onEvent(MangaScreenEvent.ShowCoverDialog) },
        onShareClicked = if (isHttpSource) {
            { shareManga(context, ViewModel.manga, ViewModel.source) }
        } else {
            null
        },
        onDownloadActionClicked = if (!successState.source.isLocalOrStub()) {
            { ViewModel.onEvent(MangaScreenEvent.RunDownloadAction(it)) }
        } else {
            null
        },
        onEditCategoryClicked = if (successState.manga.favorite) {
            { ViewModel.onEvent(MangaScreenEvent.ShowChangeCategoryDialog) }
        } else {
            null
        },
        onEditFetchIntervalClicked = if (successState.manga.favorite) {
            { ViewModel.onEvent(MangaScreenEvent.ShowSetFetchIntervalDialog) }
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
            { ViewModel.onEvent(MangaScreenEvent.ShowEditMetadataDialog) }
        } else {
            null
        },
        onMultiBookmarkClicked = { ch, b -> ViewModel.onEvent(MangaScreenEvent.BookmarkChapters(ch, b)) },
        onMultiMarkAsReadClicked = { ch, b -> ViewModel.onEvent(MangaScreenEvent.MarkChaptersRead(ch, b)) },
        onMarkPreviousAsReadClicked = { ViewModel.onEvent(MangaScreenEvent.MarkPreviousChapterRead(it)) },
        onMultiDeleteClicked = { ViewModel.onEvent(MangaScreenEvent.ShowDeleteChapterDialog(it)) },
        onChapterSwipe = { ch, sw -> ViewModel.onEvent(MangaScreenEvent.ChapterSwipe(ch, sw)) },
        onChapterSelected = { item, selected, fromLongPress ->
            ViewModel.onEvent(MangaScreenEvent.ToggleSelection(item, selected, fromLongPress))
        },
        onAllChapterSelected = { ViewModel.onEvent(MangaScreenEvent.ToggleAllSelection(it)) },
        onInvertSelection = { ViewModel.onEvent(MangaScreenEvent.InvertSelection) },
    )

    var showScanlatorsDialog by remember { mutableStateOf(value = false) }

    val onDismissRequest = { ViewModel.onEvent(MangaScreenEvent.DismissDialog) }
    when (val dialog = successState.dialog) {
        null -> {}
        is MangaViewModel.Dialog.ChangeCategory -> {
            ChangeCategoryDialog(
                initialSelection = dialog.initialSelection.toImmutableList(),
                onDismissRequest = onDismissRequest,
                onEditCategories = {
                    navController.navigate(ephyra.presentation.core.ui.navigation.Screen.Category)
                },
                onConfirm = { include, _ ->
                    ViewModel.onEvent(MangaScreenEvent.MoveMangaToCategoriesAndAddToLibrary(dialog.manga, include))
                },
            )
        }
        is MangaViewModel.Dialog.DeleteChapters -> {
            DeleteChaptersDialog(
                onDismissRequest = onDismissRequest,
                onConfirm = {
                    ViewModel.onEvent(MangaScreenEvent.ToggleAllSelection(selected = false))
                    ViewModel.onEvent(MangaScreenEvent.DeleteChapters(dialog.chapters))
                },
            )
        }

        is MangaViewModel.Dialog.DuplicateManga -> {
            DuplicateMangaDialog(
                duplicates = dialog.duplicates,
                onDismissRequest = onDismissRequest,
                onConfirm = { ViewModel.onEvent(MangaScreenEvent.ToggleFavorite(checkDuplicate = false)) },
                onOpenManga = {
                    navController.navigate(
                        ephyra.presentation.core.ui.navigation.Screen.MangaDetails(mangaId = it.id, fromSource = false),
                    )
                },
                onMigrate = { ViewModel.onEvent(MangaScreenEvent.ShowMigrateDialog(it)) },
                sourceManager = ViewModel.sourceManager,
            )
        }

        is MangaViewModel.Dialog.Migrate -> {
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
        MangaViewModel.Dialog.SettingsSheet -> ChapterSettingsDialog(
            basePreferences = ViewModel.basePreferences,
            onDismissRequest = onDismissRequest,
            manga = successState.manga,
            onDownloadFilterChanged = { ViewModel.onEvent(MangaScreenEvent.SetDownloadedFilter(it)) },
            onUnreadFilterChanged = { ViewModel.onEvent(MangaScreenEvent.SetUnreadFilter(it)) },
            onBookmarkedFilterChanged = { ViewModel.onEvent(MangaScreenEvent.SetBookmarkedFilter(it)) },
            onSortModeChanged = { ViewModel.onEvent(MangaScreenEvent.SetSorting(it)) },
            onDisplayModeChanged = { ViewModel.onEvent(MangaScreenEvent.SetDisplayMode(it)) },
            onSetAsDefault = { ViewModel.onEvent(MangaScreenEvent.SetCurrentSettingsAsDefault(it)) },
            onResetToDefault = { ViewModel.onEvent(MangaScreenEvent.ResetToDefaultSettings) },
            scanlatorFilterActive = successState.scanlatorFilterActive,
            onScanlatorFilterClicked = { showScanlatorsDialog = true },
        )
        MangaViewModel.Dialog.TrackSheet -> {
            ephyra.feature.manga.track.TrackInfoDialog(
                manga = successState.manga,
                source = successState.source,
                onDismissRequest = onDismissRequest,
            )
        }
        MangaViewModel.Dialog.FullCover -> {
            val sm = hiltViewModel<MangaCoverViewModel>()
            LaunchedEffect(successState.manga.id) {
                sm.init(successState.manga.id)
            }
            LaunchedEffect(sm) {
                sm.effects.collect { effect ->
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
                    val coverSearchSm = hiltViewModel<CoverSearchViewModel>()
                    LaunchedEffect(manga!!.title, successState.source.id) {
                        coverSearchSm.onEvent(
                            CoverSearchScreenEvent.Init(
                                mangaTitle = manga!!.title,
                                currentSourceId = successState.source.id,
                            ),
                        )
                        coverSearchSm.onEvent(CoverSearchScreenEvent.Search)
                    }
                    val coverSearchState by coverSearchSm.state.collectAsStateWithLifecycle()
                    CoverSearchDialog(
                        state = coverSearchState,
                        onCoverSelected = { cover ->
                            sm.onEvent(MangaCoverScreenEvent.SetCoverFromUrl(cover.thumbnailUrl, cover.sourceId))
                            showCoverSearch = false
                        },
                        onSetAsMetadataSource = { cover ->
                            ViewModel.onEvent(MangaScreenEvent.SetMetadataSource(cover.sourceId, cover.mangaUrl))
                            showCoverSearch = false
                        },
                        onRefresh = { coverSearchSm.onEvent(CoverSearchScreenEvent.Refresh) },
                        onDismissRequest = { showCoverSearch = false },
                    )
                } else {
                    MangaCoverDialog(
                        manga = manga!!,
                        snackbarHostState = sm.snackbarHostState,
                        isCustomCover = remember(manga) { manga!!.hasCustomCover(ViewModel.coverCache) },
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
        is MangaViewModel.Dialog.SetFetchInterval -> {
            SetIntervalDialog(
                interval = dialog.manga.fetchInterval,
                nextUpdate = dialog.manga.expectedNextUpdate,
                onDismissRequest = onDismissRequest,
                onValueChanged = { interval: Int ->
                    ViewModel.onEvent(MangaScreenEvent.SetFetchInterval(dialog.manga, interval))
                }
                    .takeIf { successState.isUpdateIntervalEnabled },
                appInfo = ViewModel.appInfo,
            )
        }
        MangaViewModel.Dialog.EditMetadata -> {
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
                onSaveTitle = { ViewModel.onEvent(MangaScreenEvent.EditTitle(it)) },
                onSaveAuthor = { ViewModel.onEvent(MangaScreenEvent.EditAuthor(it)) },
                onSaveArtist = { ViewModel.onEvent(MangaScreenEvent.EditArtist(it)) },
                onSaveDescription = { ViewModel.onEvent(MangaScreenEvent.EditDescription(it)) },
                onSaveStatus = { ViewModel.onEvent(MangaScreenEvent.EditStatus(it)) },
                onSaveGenres = { ViewModel.onEvent(MangaScreenEvent.EditGenres(it)) },
                onToggleLock = { ViewModel.onEvent(MangaScreenEvent.ToggleLockedField(it)) },
                onSetAllLocks = { mask -> ViewModel.onEvent(MangaScreenEvent.SetLockedFields(mask)) },
                onIdentify = if (manga.canonicalId == null) {
                    {
                        ViewModel.onEvent(MangaScreenEvent.DismissDialog)
                        ViewModel.onEvent(MangaScreenEvent.ResolveCanonicalId)
                    }
                } else {
                    {
                        ViewModel.onEvent(MangaScreenEvent.DismissDialog)
                        ViewModel.onEvent(MangaScreenEvent.RefreshFromAuthority)
                    }
                },
                onUnlinkAuthority = if (manga.canonicalId != null) {
                    {
                        ViewModel.onEvent(MangaScreenEvent.DismissDialog)
                        ViewModel.onEvent(MangaScreenEvent.UnlinkAuthority)
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
            onConfirm = { ViewModel.onEvent(MangaScreenEvent.SetExcludedScanlators(it)) },
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
