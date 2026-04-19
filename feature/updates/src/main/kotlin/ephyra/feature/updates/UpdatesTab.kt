package ephyra.feature.updates

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import ephyra.core.common.i18n.stringResource
import ephyra.feature.download.DownloadQueueScreen
import ephyra.feature.manga.MangaScreen
import ephyra.feature.reader.ReaderActivity
import ephyra.feature.upcoming.UpcomingScreen
import ephyra.feature.updates.UpdatesScreenModel.Event
import ephyra.i18n.MR
import ephyra.presentation.core.R
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.ui.AppReadySignal
import ephyra.presentation.core.ui.BottomNavController
import ephyra.presentation.core.util.Tab
import kotlinx.coroutines.flow.collectLatest

data object UpdatesTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_updates_enter)
            return TabOptions(
                index = 1u,
                title = stringResource(MR.strings.label_recent_updates),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        navigator.push(DownloadQueueScreen)
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<UpdatesScreenModel>()
        val settingsScreenModel = koinScreenModel<UpdatesSettingsScreenModel>()
        val state by screenModel.state.collectAsStateWithLifecycle()

        UpdateScreen(
            state = state,
            snackbarHostState = screenModel.snackbarHostState,
            lastUpdated = screenModel.lastUpdated,
            isRefreshing = state.isLibraryUpdating,
            onClickCover = { item -> navigator.push(MangaScreen(item.update.mangaId)) },
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
            onCalendarClicked = { navigator.push(UpcomingScreen()) },
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
                    Event.InternalError -> screenModel.snackbarHostState.showSnackbar(
                        context.stringResource(MR.strings.internal_error),
                    )

                    is Event.LibraryUpdateTriggered -> {
                        val msg = if (event.started) {
                            MR.strings.updating_library
                        } else {
                            MR.strings.update_already_running
                        }
                        screenModel.snackbarHostState.showSnackbar(context.stringResource(msg))
                    }
                }
            }
        }

        LaunchedEffect(state.selectionMode) {
            (navigator.lastItem as? BottomNavController)?.showBottomNav(!state.selectionMode)
        }

        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) {
                (context as? AppReadySignal)?.signalReady()
            }
        }
        DisposableEffect(Unit) {
            screenModel.onEvent(UpdatesScreenEvent.ResetNewUpdatesCount)

            onDispose {
                screenModel.onEvent(UpdatesScreenEvent.ResetNewUpdatesCount)
            }
        }
    }
}
