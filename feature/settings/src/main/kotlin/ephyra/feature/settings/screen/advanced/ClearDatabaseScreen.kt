package ephyra.feature.settings.screen.advanced

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastMap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.util.lang.launchIO
import ephyra.core.common.util.lang.toLong
import ephyra.core.common.util.lang.withNonCancellableContext
import ephyra.domain.history.interactor.RemoveResettedHistory
import ephyra.domain.manga.interactor.DeleteNonLibraryManga
import ephyra.domain.source.interactor.GetSourcesWithNonLibraryManga
import ephyra.domain.source.model.Source
import ephyra.domain.source.model.SourceWithCount
import ephyra.presentation.core.components.AppBar
import ephyra.presentation.core.components.AppBarActions
import ephyra.presentation.core.components.LazyColumnWithAction
import ephyra.presentation.core.components.SourceIcon
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.components.material.padding
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.screens.EmptyScreen
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.core.udf.BaseUdfViewModel
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.util.selectedBackground
import ephyra.presentation.core.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
fun ClearDatabaseScreen(
    navController: NavController = LocalNavController.current,
) {
    val context = LocalContext.current
    val model = hiltViewModel<ClearDatabaseViewModel>()
    val state by model.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        model.effects.collectLatest { effect ->
            when (effect) {
                ClearDatabaseEffect.DatabaseCleared -> {
                    context.toast(ephyra.app.core.common.R.string.clear_database_completed)
                }
            }
        }
    }

    when (val s = state) {
        is ClearDatabaseViewModel.State.Loading -> LoadingScreen()
        is ClearDatabaseViewModel.State.Ready -> {
            if (s.showConfirmation) {
                var keepReadManga by remember { mutableStateOf(true) }
                AlertDialog(
                    title = {
                        Text(text = stringResource(ephyra.app.core.common.R.string.are_you_sure))
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                        ) {
                            Text(text = stringResource(ephyra.app.core.common.R.string.clear_database_text))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(ephyra.app.core.common.R.string.clear_db_exclude_read),
                                    modifier = Modifier.weight(1f),
                                )
                                Switch(
                                    checked = keepReadManga,
                                    onCheckedChange = { keepReadManga = it },
                                )
                            }
                            if (!keepReadManga) {
                                Text(
                                    text = stringResource(
                                        ephyra.app.core.common.R.string.clear_database_history_warning,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    },
                    onDismissRequest = { model.onEvent(ClearDatabaseEvent.HideConfirmation) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                model.onEvent(ClearDatabaseEvent.RemoveManga(keepReadManga))
                            },
                        ) {
                            Text(text = stringResource(ephyra.app.core.common.R.string.action_ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { model.onEvent(ClearDatabaseEvent.HideConfirmation) }) {
                            Text(text = stringResource(ephyra.app.core.common.R.string.action_cancel))
                        }
                    },
                )
            }

            Scaffold(
                topBar = { scrollBehavior ->
                    AppBar(
                        title = stringResource(ephyra.app.core.common.R.string.pref_clear_database),
                        navigateUp = { navController.popBackStack() },
                        actions = {
                            if (s.items.isNotEmpty()) {
                                AppBarActions(
                                    actions = persistentListOf(
                                        AppBar.Action(
                                            title = stringResource(ephyra.app.core.common.R.string.action_select_all),
                                            icon = Icons.Outlined.SelectAll,
                                            onClick = { model.onEvent(ClearDatabaseEvent.SelectAll) },
                                        ),
                                        AppBar.Action(
                                            title = stringResource(
                                                ephyra.app.core.common.R.string.action_select_inverse,
                                            ),
                                            icon = Icons.Outlined.FlipToBack,
                                            onClick = { model.onEvent(ClearDatabaseEvent.InvertSelection) },
                                        ),
                                    ),
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior,
                    )
                },
            ) { contentPadding ->
                if (s.items.isEmpty()) {
                    EmptyScreen(
                        message = stringResource(ephyra.app.core.common.R.string.database_clean),
                        modifier = Modifier.padding(contentPadding),
                    )
                } else {
                    LazyColumnWithAction(
                        contentPadding = contentPadding,
                        actionLabel = stringResource(ephyra.app.core.common.R.string.action_delete),
                        actionEnabled = s.selection.isNotEmpty(),
                        onClickAction = { model.onEvent(ClearDatabaseEvent.ShowConfirmation) },
                    ) {
                        items(s.items, key = { it.id }) { sourceWithCount ->
                            ClearDatabaseItem(
                                source = sourceWithCount.source,
                                count = sourceWithCount.count,
                                isSelected = s.selection.contains(sourceWithCount.id),
                                onClickSelect = {
                                    model.onEvent(ClearDatabaseEvent.ToggleSelection(sourceWithCount.source))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClearDatabaseItem(
    source: Source,
    count: Long,
    isSelected: Boolean,
    onClickSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .selectedBackground(isSelected)
            .clickable(onClick = onClickSelect)
            .padding(horizontal = 8.dp)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourceIcon(source = source)
        Column(
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f),
        ) {
            Text(
                text = source.visualName,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(text = stringResource(ephyra.app.core.common.R.string.clear_database_source_item_count, count))
        }
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onClickSelect() },
        )
    }
}

@HiltViewModel
class ClearDatabaseViewModel @Inject constructor(
    private val getSourcesWithNonLibraryManga: GetSourcesWithNonLibraryManga,
    private val deleteNonLibraryManga: DeleteNonLibraryManga,
    private val removeResettedHistory: RemoveResettedHistory,
) : BaseUdfViewModel<ClearDatabaseViewModel.State, ClearDatabaseEvent, ClearDatabaseEffect>(State.Loading) {

    init {
        viewModelScope.launch {
            getSourcesWithNonLibraryManga.subscribe()
                .collectLatest { list ->
                    updateState { old ->
                        val items = list.sortedBy { it.name }
                        when (old) {
                            State.Loading -> State.Ready(items)
                            is State.Ready -> old.copy(items = items)
                        }
                    }
                }
        }
    }

    override fun onEvent(event: ClearDatabaseEvent) {
        when (event) {
            is ClearDatabaseEvent.ToggleSelection -> toggleSelection(event.source)
            ClearDatabaseEvent.ClearSelection -> clearSelection()
            ClearDatabaseEvent.SelectAll -> selectAll()
            ClearDatabaseEvent.InvertSelection -> invertSelection()
            ClearDatabaseEvent.ShowConfirmation -> showConfirmation()
            ClearDatabaseEvent.HideConfirmation -> hideConfirmation()
            is ClearDatabaseEvent.RemoveManga -> {
                viewModelScope.launch {
                    removeMangaBySourceId(event.keepReadManga)
                    clearSelection()
                    hideConfirmation()
                    emitEffect(ClearDatabaseEffect.DatabaseCleared)
                }
            }
        }
    }

    suspend fun removeMangaBySourceId(keepReadManga: Boolean) = withNonCancellableContext {
        val ready = currentState as? State.Ready ?: return@withNonCancellableContext
        deleteNonLibraryManga.await(ready.selection, keepReadManga.toLong())
        removeResettedHistory.await()
    }

    fun toggleSelection(source: Source) = updateState { state ->
        if (state !is State.Ready) return@updateState state
        val mutableList = state.selection.toMutableList()
        if (mutableList.contains(source.id)) {
            mutableList.remove(source.id)
        } else {
            mutableList.add(source.id)
        }
        state.copy(selection = mutableList)
    }

    fun clearSelection() = updateState { state ->
        if (state !is State.Ready) return@updateState state
        state.copy(selection = emptyList())
    }

    fun selectAll() = updateState { state ->
        if (state !is State.Ready) return@updateState state
        state.copy(selection = state.items.fastMap { it.id })
    }

    fun invertSelection() = updateState { state ->
        if (state !is State.Ready) return@updateState state
        state.copy(
            selection = state.items
                .fastMap { it.id }
                .filterNot { it in state.selection },
        )
    }

    fun showConfirmation() = updateState { state ->
        if (state !is State.Ready) return@updateState state
        state.copy(showConfirmation = true)
    }

    fun hideConfirmation() = updateState { state ->
        if (state !is State.Ready) return@updateState state
        state.copy(showConfirmation = false)
    }

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data class Ready(
            val items: List<SourceWithCount>,
            val selection: List<Long> = emptyList(),
            val showConfirmation: Boolean = false,
        ) : State
    }
}

sealed interface ClearDatabaseEvent {
    data class ToggleSelection(val source: Source) : ClearDatabaseEvent
    data object ClearSelection : ClearDatabaseEvent
    data object SelectAll : ClearDatabaseEvent
    data object InvertSelection : ClearDatabaseEvent
    data object ShowConfirmation : ClearDatabaseEvent
    data object HideConfirmation : ClearDatabaseEvent
    data class RemoveManga(val keepReadManga: Boolean) : ClearDatabaseEvent
}

sealed interface ClearDatabaseEffect {
    data object DatabaseCleared : ClearDatabaseEffect
}
