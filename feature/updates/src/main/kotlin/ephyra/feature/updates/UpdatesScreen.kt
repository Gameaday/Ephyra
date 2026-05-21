package ephyra.feature.updates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.domain.download.model.Download
import ephyra.feature.manga.presentation.components.ChapterDownloadAction
import ephyra.feature.manga.presentation.components.MangaBottomActionMenu
import ephyra.feature.reader.ReaderActivity
import ephyra.presentation.core.components.AppBar
import ephyra.presentation.core.components.AppBarActions
import ephyra.presentation.core.components.FastScrollLazyColumn
import ephyra.presentation.core.components.material.PullRefresh
import ephyra.presentation.core.components.material.Scaffold
import ephyra.core.common.i18n.stringResource
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.screens.EmptyScreen
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.core.theme.active
import ephyra.presentation.core.ui.AppReadySignal
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.NavigationEvents
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import java.time.LocalDate
import kotlin.collections.isNotEmpty
import kotlin.compareTo

@Composable
fun UpdateScreen(
    state: UpdatesScreenModel.State,
    snackbarHostState: SnackbarHostState,
    lastUpdated: Long,
    isRefreshing: Boolean,
    onClickCover: (UpdatesItem) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onCalendarClicked: () -> Unit,
    onUpdateLibrary: () -> Unit,
    onDownloadChapter: (List<UpdatesItem>, ChapterDownloadAction) -> Unit,
    onMultiBookmarkClicked: (List<UpdatesItem>, bookmark: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<UpdatesItem>, read: Boolean) -> Unit,
    onMultiDeleteClicked: (List<UpdatesItem>) -> Unit,
    onUpdateSelected: (UpdatesItem, Boolean, Boolean) -> Unit,
    onOpenChapter: (UpdatesItem) -> Unit,
    onFilterClicked: () -> Unit,
    hasActiveFilters: Boolean,
) {
    BackHandler(enabled = state.selectionMode) {
        onSelectAll(false)
    }

    Scaffold(
        topBar = { scrollBehavior ->
            UpdatesAppBar(
                onCalendarClicked = { onCalendarClicked() },
                onUpdateLibrary = { onUpdateLibrary() },
                onFilterClicked = { onFilterClicked() },
                hasFilters = hasActiveFilters,
                actionModeCounter = state.selected.size,
                onSelectAll = { onSelectAll(true) },
                onInvertSelection = { onInvertSelection() },
                onCancelActionMode = { onSelectAll(false) },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            UpdatesBottomBar(
                selected = state.selected,
                onDownloadChapter = onDownloadChapter,
                onMultiBookmarkClicked = onMultiBookmarkClicked,
                onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
                onMultiDeleteClicked = onMultiDeleteClicked,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        when {
            state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
            state.items.isEmpty() -> EmptyScreen(
                stringRes = ephyra.app.core.common.R.string.information_no_recent,
                modifier = Modifier.padding(contentPadding),
            )
            else -> {
                PullRefresh(
                    refreshing = isRefreshing,
                    onRefresh = { onUpdateLibrary() },
                    enabled = !state.selectionMode,
                    indicatorPadding = contentPadding,
                ) {
                    FastScrollLazyColumn(
                        contentPadding = contentPadding,
                    ) {
                        updatesLastUpdatedItem(lastUpdated)

                        updatesUiItems(
                            uiModels = state.getUiModel(),
                            selectionMode = state.selectionMode,
                            onUpdateSelected = onUpdateSelected,
                            onClickCover = onClickCover,
                            onClickUpdate = onOpenChapter,
                            onDownloadChapter = onDownloadChapter,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdatesAppBar(
    onCalendarClicked: () -> Unit,
    onUpdateLibrary: () -> Unit,
    onFilterClicked: () -> Unit,
    hasFilters: Boolean,
    // For action mode
    actionModeCounter: Int,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onCancelActionMode: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
) {
    AppBar(
        modifier = modifier,
        title = stringResource(ephyra.app.core.common.R.string.label_recent_updates),
        actions = {
            AppBarActions(
                persistentListOf(
                    AppBar.Action(
                        title = stringResource(ephyra.app.core.common.R.string.action_filter),
                        icon = Icons.Outlined.FilterList,
                        iconTint = if (hasFilters) MaterialTheme.colorScheme.active else LocalContentColor.current,
                        onClick = onFilterClicked,
                    ),
                    AppBar.Action(
                        title = stringResource(ephyra.app.core.common.R.string.action_view_upcoming),
                        icon = Icons.Outlined.CalendarMonth,
                        onClick = onCalendarClicked,
                    ),
                    AppBar.Action(
                        title = stringResource(ephyra.app.core.common.R.string.action_update_library),
                        icon = Icons.Outlined.Refresh,
                        onClick = onUpdateLibrary,
                    ),
                ),
            )
        },
        actionModeCounter = actionModeCounter,
        onCancelActionMode = onCancelActionMode,
        actionModeActions = {
            AppBarActions(
                persistentListOf(
                    AppBar.Action(
                        title = stringResource(ephyra.app.core.common.R.string.action_select_all),
                        icon = Icons.Outlined.SelectAll,
                        onClick = onSelectAll,
                    ),
                    AppBar.Action(
                        title = stringResource(ephyra.app.core.common.R.string.action_select_inverse),
                        icon = Icons.Outlined.FlipToBack,
                        onClick = onInvertSelection,
                    ),
                ),
            )
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun UpdatesBottomBar(
    selected: List<UpdatesItem>,
    onDownloadChapter: (List<UpdatesItem>, ChapterDownloadAction) -> Unit,
    onMultiBookmarkClicked: (List<UpdatesItem>, bookmark: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<UpdatesItem>, read: Boolean) -> Unit,
    onMultiDeleteClicked: (List<UpdatesItem>) -> Unit,
) {
    MangaBottomActionMenu(
        visible = selected.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
        onBookmarkClicked = {
            onMultiBookmarkClicked.invoke(selected, true)
        }.takeIf { selected.fastAny { !it.update.bookmark } },
        onRemoveBookmarkClicked = {
            onMultiBookmarkClicked.invoke(selected, false)
        }.takeIf { selected.fastAll { it.update.bookmark } },
        onMarkAsReadClicked = {
            onMultiMarkAsReadClicked(selected, true)
        }.takeIf { selected.fastAny { !it.update.read } },
        onMarkAsUnreadClicked = {
            onMultiMarkAsReadClicked(selected, false)
        }.takeIf { selected.fastAny { it.update.read || it.update.lastPageRead > 0L } },
        onDownloadClicked = {
            onDownloadChapter(selected, ChapterDownloadAction.START)
        }.takeIf {
            selected.fastAny { it.downloadStateProvider() != Download.State.DOWNLOADED }
        },
        onDeleteClicked = {
            onMultiDeleteClicked(selected)
        }.takeIf { selected.fastAny { it.downloadStateProvider() == Download.State.DOWNLOADED } },
    )
}

sealed interface UpdatesUiModel {
    data class Header(val date: LocalDate) : UpdatesUiModel
    data class Item(val item: UpdatesItem) : UpdatesUiModel
}

@Composable
fun UpdatesScreen(
    navController: NavController = LocalNavController.current,
) {
    val context = LocalContext.current
    val screenModel = hiltViewModel<UpdatesScreenModel>()
    val settingsScreenModel = hiltViewModel<UpdatesSettingsScreenModel>()
    val state by screenModel.state.collectAsStateWithLifecycle()

    UpdateScreen(
        state = state,
        snackbarHostState = screenModel.snackbarHostState,
        lastUpdated = screenModel.lastUpdated,
        isRefreshing = state.isLibraryUpdating,
        onClickCover = { item -> navController.navigate(ScreenRoutes.MangaDetails.createRoute(item.update.mangaId, false)) },
        onSelectAll = { screenModel.onEvent(UpdatesScreenEvent.ToggleAllSelection(it)) },
        onInvertSelection = { screenModel.onEvent(UpdatesScreenEvent.InvertSelection) },
        onUpdateLibrary = { screenModel.onEvent(UpdatesScreenEvent.UpdateLibrary) },
        onDownloadChapter = { items, action ->
            screenModel.onEvent(UpdatesScreenEvent.DownloadChapters(items, action))
        },
        onMultiBookmarkClicked = { items, bookmark ->
            screenModel.onEvent(UpdatesScreenEvent.BookmarkUpdates(items, bookmark))
        },
        onMultiMarkAsReadClicked = { items, read ->
            screenModel.onEvent(UpdatesScreenEvent.MarkUpdatesRead(items, read))
        },
        onMultiDeleteClicked = { items ->
            screenModel.onEvent(UpdatesScreenEvent.ShowConfirmDeleteChapters(items))
        },
        onUpdateSelected = { item, selected, fromLongPress ->
            screenModel.onEvent(UpdatesScreenEvent.ToggleSelection(item, selected, fromLongPress))
        },
        onOpenChapter = {
            val intent = ReaderActivity.newIntent(context, it.update.mangaId, it.update.chapterId)
            context.startActivity(intent)
        },
        onCalendarClicked = {
            navController.navigate(ScreenRoutes.Upcoming.route)
        },
        onFilterClicked = { screenModel.onEvent(UpdatesScreenEvent.ShowFilterDialog) },
        hasActiveFilters = state.hasActiveFilters,
    )

    val onDismissDialog = { screenModel.onEvent(UpdatesScreenEvent.SetDialog(null)) }
    when (val dialog = state.dialog) {
        is UpdatesScreenModel.Dialog.DeleteConfirmation -> {
            UpdatesDeleteConfirmationDialog(
                onDismissRequest = onDismissDialog,
                onConfirm = { screenModel.onEvent(UpdatesScreenEvent.DeleteChapters(dialog.toDelete)) },
            )
        }

        is UpdatesScreenModel.Dialog.FilterSheet -> {
            UpdatesFilterDialog(
                onDismissRequest = onDismissDialog,
                screenModel = settingsScreenModel,
            )
        }

        null -> {}
    }

    LaunchedEffect(Unit) {
        screenModel.events.collectLatest { event ->
            when (event) {
                UpdatesScreenModel.Event.InternalError -> screenModel.snackbarHostState.showSnackbar(
                    context.stringResource(ephyra.app.core.common.R.string.internal_error),
                )

                is UpdatesScreenModel.Event.LibraryUpdateTriggered -> {
                    val msg = if (event.started) {
                        ephyra.app.core.common.R.string.updating_library
                    } else {
                        ephyra.app.core.common.R.string.update_already_running
                    }
                    screenModel.snackbarHostState.showSnackbar(context.stringResource(msg))
                }
            }
        }
    }

    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            (context as? AppReadySignal)?.signalReady()
        }
    }

    LaunchedEffect(Unit) {
        NavigationEvents.reselectEvent
            .filter { it == ScreenRoutes.Updates.route }
            .collect { navController.navigate(ScreenRoutes.DownloadQueue.route) }
    }

    DisposableEffect(Unit) {
        screenModel.onEvent(UpdatesScreenEvent.ResetNewUpdatesCount)

        onDispose {
            screenModel.onEvent(UpdatesScreenEvent.ResetNewUpdatesCount)
        }
    }
}
