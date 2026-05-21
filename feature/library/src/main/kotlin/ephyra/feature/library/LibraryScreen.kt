package ephyra.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.util.fastAll
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.util.lang.launchIO
import ephyra.domain.category.model.Category
import ephyra.domain.library.model.LibraryManga
import ephyra.domain.manga.model.Manga
import ephyra.feature.library.presentation.DeleteLibraryMangaDialog
import ephyra.feature.library.presentation.LibrarySettingsDialog
import ephyra.feature.library.presentation.components.LibraryContent
import ephyra.feature.library.presentation.components.LibraryToolbar
import ephyra.feature.manga.presentation.components.LibraryBottomActionMenu
import ephyra.feature.reader.ReaderActivity
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.screens.EmptyScreen
import ephyra.presentation.core.screens.EmptyScreenAction
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.core.ui.AppReadySignal
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.NavigationEvents
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import ephyra.presentation.core.util.manga.DownloadAction
import ephyra.source.local.isLocal
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    navController: NavController = LocalNavController.current,
    searchQuery: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val screenModel = hiltViewModel<LibraryScreenModel>()
    val settingsScreenModel = hiltViewModel<LibrarySettingsScreenModel>()
    val state by screenModel.state.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    val onClickRefresh: (Category?) -> Boolean = { category ->
        val started = screenModel.libraryUpdateScheduler.startNow(category)
        scope.launch {
            val msgRes = when {
                !started -> ephyra.app.core.common.R.string.update_already_running
                category != null -> ephyra.app.core.common.R.string.updating_category
                else -> ephyra.app.core.common.R.string.updating_library
            }
            snackbarHostState.showSnackbar(context.stringResource(msgRes))
        }
        started
    }

    Scaffold(
        topBar = { scrollBehavior ->
            val title = state.getToolbarTitle(
                defaultTitle = stringResource(ephyra.app.core.common.R.string.label_library),
                defaultCategoryTitle = stringResource(ephyra.app.core.common.R.string.label_default),
                page = state.coercedActiveCategoryIndex,
            )
            LibraryToolbar(
                hasActiveFilters = state.hasActiveFilters,
                selectedCount = state.selection.size,
                title = title,
                onClickUnselectAll = { screenModel.clearSelection() },
                onClickSelectAll = { screenModel.selectAll() },
                onClickInvertSelection = { screenModel.invertSelection() },
                onClickFilter = { screenModel.showSettingsDialog() },
                onClickRefresh = { onClickRefresh(state.activeCategory) },
                onClickGlobalUpdate = { onClickRefresh(null) },
                onClickOpenRandomManga = {
                    scope.launch {
                        val randomItem = screenModel.getRandomLibraryItemForCurrentCategory()
                        if (randomItem != null) {
                            navController.navigate(ScreenRoutes.MangaDetails.createRoute(randomItem.libraryManga.manga.id, fromSource = false))
                        } else {
                            snackbarHostState.showSnackbar(
                                context.stringResource(ephyra.app.core.common.R.string.information_no_entries_found),
                            )
                        }
                    }
                },
                searchQuery = state.searchQuery,
                onSearchQueryChange = { screenModel.search(it) },
                // For scroll overlay when no tab
                scrollBehavior = scrollBehavior.takeIf { !state.showCategoryTabs },
            )
        },
        bottomBar = {
            LibraryBottomActionMenu(
                visible = state.selectionMode,
                onChangeCategoryClicked = { screenModel.openChangeCategoryDialog() },
                onMarkAsReadClicked = { screenModel.markReadSelection(read = true) },
                onMarkAsUnreadClicked = { screenModel.markReadSelection(read = false) },
                onDownloadClicked = { action: DownloadAction ->
                    screenModel.performDownloadAction(action)
                }.takeIf { state.selectedManga.fastAll { !it.isLocal() } },
                onDeleteClicked = { screenModel.openDeleteMangaDialog() },
                onMigrateClicked = {
                    screenModel.clearSelection()
                    // TODO: MigrationConfigScreen doesn't have a route yet
                    // navController.navigate(...)
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        when {
            state.isLoading -> {
                LoadingScreen(Modifier.padding(contentPadding))
            }

            state.searchQuery.isNullOrEmpty() && !state.hasActiveFilters && state.isLibraryEmpty -> {
                val handler = LocalUriHandler.current
                EmptyScreen(
                    stringRes = ephyra.app.core.common.R.string.information_empty_library,
                    modifier = Modifier.padding(contentPadding),
                    actions = persistentListOf(
                        EmptyScreenAction(
                            stringRes = ephyra.app.core.common.R.string.getting_started_guide,
                            icon = Icons.AutoMirrored.Outlined.HelpOutline,
                            onClick = { handler.openUri("https://ephyra.app/docs/guides/getting-started") },
                        ),
                    ),
                )
            }

            else -> {
                LibraryContent(
                    categories = state.displayedCategories,
                    searchQuery = state.searchQuery,
                    selection = state.selection,
                    contentPadding = contentPadding,
                    currentPage = state.coercedActiveCategoryIndex,
                    hasActiveFilters = state.hasActiveFilters,
                    showPageTabs = state.showCategoryTabs || !state.searchQuery.isNullOrEmpty(),
                    deadSourceCount = state.deadSourceCount,
                    degradedSourceCount = state.degradedSourceCount,
                    onChangeCurrentPage = { screenModel.updateActiveCategoryIndex(it) },
                    onClickManga = { navController.navigate(ScreenRoutes.MangaDetails.createRoute(it, false)) },
                    onContinueReadingClicked = { it: LibraryManga ->
                        scope.launchIO {
                            val chapter = screenModel.getNextUnreadChapter(it.manga)
                            if (chapter != null) {
                                context.startActivity(
                                    ReaderActivity.newIntent(context, chapter.mangaId, chapter.id),
                                )
                            } else {
                                snackbarHostState.showSnackbar(
                                    context.stringResource(ephyra.app.core.common.R.string.no_next_chapter),
                                )
                            }
                        }
                        Unit
                    }.takeIf { state.showMangaContinueButton },
                    onToggleSelection = { cat, manga ->
                        screenModel.toggleSelection(cat, manga)
                    },
                    onToggleRangeSelection = { category, manga ->
                        screenModel.toggleRangeSelection(category, manga)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onRefresh = { onClickRefresh(state.activeCategory) },
                    onGlobalSearchClicked = {
                        navController.navigate(
                            ScreenRoutes.GlobalSearch.createRoute(screenModel.state.value.searchQuery),
                        )
                    },
                    onClickHealthFilter = { screenModel.enableHealthFilter() },
                    getItemCountForCategory = { state.getItemCountForCategory(it) },
                    getDisplayMode = { screenModel.getDisplayMode() },
                    getColumnsForOrientation = { screenModel.getColumnsForOrientation(it) },
                    getItemsForCategory = { state.getItemsForCategory(it) },
                )
            }
        }
    }

    val onDismissRequest = { screenModel.closeDialog() }
    when (val dialog = state.dialog) {
        is LibraryScreenModel.Dialog.SettingsSheet -> run {
            LibrarySettingsDialog(
                onDismissRequest = onDismissRequest,
                screenModel = settingsScreenModel,
                category = state.activeCategory,
            )
        }

        is LibraryScreenModel.Dialog.ChangeCategory -> {
            ephyra.feature.category.components.ChangeCategoryDialog(
                initialSelection = dialog.initialSelection,
                onDismissRequest = onDismissRequest,
                onEditCategories = {
                    screenModel.clearSelection()
                    navController.navigate(ScreenRoutes.Category.route)
                },
                onConfirm = { include, exclude ->
                    screenModel.clearSelection()
                    screenModel.setMangaCategories(dialog.manga, include, exclude)
                },
            )
        }

        is LibraryScreenModel.Dialog.DeleteManga -> {
            DeleteLibraryMangaDialog(
                containsLocalManga = dialog.manga.any(Manga::isLocal),
                onDismissRequest = onDismissRequest,
                onConfirm = { deleteManga, deleteChapter ->
                    screenModel.removeMangas(dialog.manga, deleteManga, deleteChapter)
                    screenModel.clearSelection()
                },
            )
        }

        null -> {}
    }

    BackHandler(enabled = state.selectionMode || (state.searchQuery != null)) {
        when {
            state.selectionMode -> screenModel.clearSelection()
            state.searchQuery != null -> screenModel.search(null)
        }
    }

    LaunchedEffect(state.selectionMode, state.dialog) {
        // TODO: We need a way to communicate to the host about bottom nav visibility
        // (context as? MainActivity)?.showBottomNav(!state.selectionMode)
    }

    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            (context as? AppReadySignal)?.signalReady()
        }
    }

    searchQuery?.let {
        LaunchedEffect(it) {
            screenModel.search(it)
        }
    }

    LaunchedEffect(Unit) {
        NavigationEvents.reselectEvent
            .filter { it == ScreenRoutes.Library.route }
            .collect { screenModel.showSettingsDialog() }
    }
}
