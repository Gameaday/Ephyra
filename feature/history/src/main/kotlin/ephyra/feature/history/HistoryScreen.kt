package ephyra.feature.history

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.core.common.i18n.stringResource
import ephyra.domain.history.model.HistoryWithRelations
import ephyra.feature.history.components.HistoryDeleteAllDialog
import ephyra.feature.history.components.HistoryDeleteDialog
import ephyra.feature.history.components.HistoryItem
import ephyra.feature.manga.presentation.DuplicateMangaDialog
import ephyra.feature.migration.dialog.MigrateMangaDialog
import ephyra.feature.reader.ReaderActivity
import ephyra.presentation.core.components.AppBar
import ephyra.presentation.core.components.AppBarActions
import ephyra.presentation.core.components.AppBarTitle
import ephyra.presentation.core.components.FastScrollLazyColumn
import ephyra.presentation.core.components.ListGroupHeader
import ephyra.presentation.core.components.SearchToolbar
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.components.relativeDateText
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.screens.EmptyScreen
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.core.ui.AppReadySignal
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.NavigationEvents
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import ephyra.presentation.core.util.animateItemFastScroll
import ephyra.presentation.theme.TachiyomiPreviewTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import java.time.LocalDate

@Composable
fun HistoryScreen(
    state: HistoryScreenModel.State,
    snackbarHostState: SnackbarHostState,
    onSearchQueryChange: (String?) -> Unit,
    onClickCover: (mangaId: Long) -> Unit,
    onClickResume: (mangaId: Long, chapterId: Long) -> Unit,
    onClickFavorite: (mangaId: Long) -> Unit,
    onDialogChange: (HistoryScreenModel.Dialog?) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            SearchToolbar(
                titleContent = { AppBarTitle(stringResource(ephyra.app.core.common.R.string.history)) },
                searchQuery = state.searchQuery,
                onChangeSearchQuery = onSearchQueryChange,
                actions = {
                    AppBarActions(
                        persistentListOf(
                            AppBar.Action(
                                title = stringResource(ephyra.app.core.common.R.string.pref_clear_history),
                                icon = Icons.Outlined.DeleteSweep,
                                onClick = {
                                    onDialogChange(HistoryScreenModel.Dialog.DeleteAll)
                                },
                            ),
                        ),
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        state.list.let {
            if (it == null) {
                LoadingScreen(Modifier.padding(contentPadding))
            } else if (it.isEmpty()) {
                val msg = if (!state.searchQuery.isNullOrEmpty()) {
                    ephyra.app.core.common.R.string.no_results_found
                } else {
                    ephyra.app.core.common.R.string.information_no_recent_manga
                }
                EmptyScreen(
                    stringRes = msg,
                    modifier = Modifier.padding(contentPadding),
                )
            } else {
                HistoryScreenContent(
                    history = it,
                    contentPadding = contentPadding,
                    onClickCover = { history -> onClickCover(history.mangaId) },
                    onClickResume = { history -> onClickResume(history.mangaId, history.chapterId) },
                    onClickDelete = { item -> onDialogChange(HistoryScreenModel.Dialog.Delete(item)) },
                    onClickFavorite = { history -> onClickFavorite(history.mangaId) },
                )
            }
        }
    }
}

@Composable
private fun HistoryScreenContent(
    history: List<HistoryUiModel>,
    contentPadding: PaddingValues,
    onClickCover: (HistoryWithRelations) -> Unit,
    onClickResume: (HistoryWithRelations) -> Unit,
    onClickDelete: (HistoryWithRelations) -> Unit,
    onClickFavorite: (HistoryWithRelations) -> Unit,
) {
    FastScrollLazyColumn(
        contentPadding = contentPadding,
    ) {
        items(
            items = history,
            key = {
                when (it) {
                    is HistoryUiModel.Header -> "history-header-${it.date}"
                    is HistoryUiModel.Item -> "history-${it.item.id}"
                }
            },
            contentType = {
                when (it) {
                    is HistoryUiModel.Header -> "header"
                    is HistoryUiModel.Item -> "item"
                }
            },
        ) { item ->
            when (item) {
                is HistoryUiModel.Header -> {
                    ListGroupHeader(
                        modifier = Modifier.animateItemFastScroll(),
                        text = relativeDateText(item.date),
                    )
                }
                is HistoryUiModel.Item -> {
                    val value = item.item
                    HistoryItem(
                        modifier = Modifier.animateItemFastScroll(),
                        history = value,
                        onClickCover = { onClickCover(value) },
                        onClickResume = { onClickResume(value) },
                        onClickDelete = { onClickDelete(value) },
                        onClickFavorite = { onClickFavorite(value) },
                    )
                }
            }
        }
    }
}

sealed interface HistoryUiModel {
    data class Header(val date: LocalDate) : HistoryUiModel
    data class Item(val item: HistoryWithRelations) : HistoryUiModel
}

@Composable
fun HistoryTabScreen(
    navController: NavController = LocalNavController.current,
) {
    val context = LocalContext.current
    val screenModel = hiltViewModel<HistoryScreenModel>()
    val state by screenModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    HistoryScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onSearchQueryChange = { screenModel.onEvent(HistoryScreenEvent.UpdateSearchQuery(it)) },
        onClickCover = { navController.navigate(ephyra.presentation.core.ui.navigation.Screen.MangaDetails(mangaId = it, fromSource = false)) },
        onClickResume = { mangaId, chapterId ->
            screenModel.onEvent(HistoryScreenEvent.GetNextChapterForManga(mangaId, chapterId))
        },
        onDialogChange = { screenModel.onEvent(HistoryScreenEvent.SetDialog(it)) },
        onClickFavorite = { screenModel.onEvent(HistoryScreenEvent.AddFavoriteById(it)) },
    )

    val onDismissRequest = { screenModel.onEvent(HistoryScreenEvent.SetDialog(null)) }
    when (val dialog = state.dialog) {
        is HistoryScreenModel.Dialog.Delete -> {
            HistoryDeleteDialog(
                onDismissRequest = onDismissRequest,
                onDelete = { all ->
                    if (all) {
                        screenModel.onEvent(HistoryScreenEvent.RemoveAllForManga(dialog.history.mangaId))
                    } else {
                        screenModel.onEvent(HistoryScreenEvent.RemoveFromHistory(dialog.history))
                    }
                },
            )
        }

        is HistoryScreenModel.Dialog.DeleteAll -> {
            HistoryDeleteAllDialog(
                onDismissRequest = onDismissRequest,
                onDelete = { screenModel.onEvent(HistoryScreenEvent.RemoveAllHistory) },
            )
        }

        is HistoryScreenModel.Dialog.DuplicateManga -> {
            DuplicateMangaDialog(
                duplicates = dialog.duplicates,
                onDismissRequest = onDismissRequest,
                onConfirm = { screenModel.onEvent(HistoryScreenEvent.AddFavorite(dialog.manga)) },
                onOpenManga = { navController.navigate(ephyra.presentation.core.ui.navigation.Screen.MangaDetails(mangaId = it.id, fromSource = false)) },
                onMigrate = { screenModel.onEvent(HistoryScreenEvent.ShowMigrateDialog(dialog.manga, it)) },
                sourceManager = screenModel.sourceManager,
            )
        }

        is HistoryScreenModel.Dialog.ChangeCategory -> {
            ephyra.feature.category.components.ChangeCategoryDialog(
                initialSelection = dialog.initialSelection,
                onDismissRequest = onDismissRequest,
                onEditCategories = { navController.navigate(ephyra.presentation.core.ui.navigation.Screen.Category) },
                onConfirm = { include, _ ->
                    screenModel.onEvent(
                        HistoryScreenEvent.MoveMangaToCategoriesAndAddToLibrary(dialog.manga, include),
                    )
                },
            )
        }

        is HistoryScreenModel.Dialog.Migrate -> {
            MigrateMangaDialog(
                current = dialog.current,
                target = dialog.target,
                onClickTitle = {
                    navController.navigate(ephyra.presentation.core.ui.navigation.Screen.MangaDetails(mangaId = dialog.current.id, fromSource = false))
                },
                onDismissRequest = onDismissRequest,
            )
        }

        null -> {}
    }

    LaunchedEffect(state.list) {
        if (state.list != null) {
            (context as? AppReadySignal)?.signalReady()
        }
    }

    LaunchedEffect(Unit) {
        screenModel.events.collectLatest { e ->
            when (e) {
                HistoryScreenModel.Event.InternalError ->
                    snackbarHostState.showSnackbar(
                        context.stringResource(ephyra.app.core.common.R.string.internal_error),
                    )

                HistoryScreenModel.Event.HistoryCleared ->
                    snackbarHostState.showSnackbar(
                        context.stringResource(ephyra.app.core.common.R.string.clear_history_completed),
                    )

                is HistoryScreenModel.Event.OpenChapter -> {
                    val chapter = e.chapter
                    if (chapter != null) {
                        val intent = ReaderActivity.newIntent(context, chapter.mangaId, chapter.id)
                        context.startActivity(intent)
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        NavigationEvents.reselectEvent
            .filter { it == ScreenRoutes.History.route }
            .collect {
                val nextChapter = screenModel.getNextChapter()
                if (nextChapter != null) {
                    val intent = ReaderActivity.newIntent(context, nextChapter.mangaId, nextChapter.id)
                    context.startActivity(intent)
                } else {
                    snackbarHostState.showSnackbar(
                        context.stringResource(ephyra.app.core.common.R.string.no_next_chapter),
                    )
                }
            }
    }
}

@PreviewLightDark
@Composable
internal fun HistoryScreenPreviews(
    @PreviewParameter(HistoryScreenModelStateProvider::class)
    historyState: HistoryScreenModel.State,
) {
    TachiyomiPreviewTheme {
        HistoryScreen(
            state = historyState,
            snackbarHostState = SnackbarHostState(),
            onSearchQueryChange = {},
            onClickCover = {},
            onClickResume = { _, _ -> run {} },
            onDialogChange = {},
            onClickFavorite = {},
        )
    }
}
