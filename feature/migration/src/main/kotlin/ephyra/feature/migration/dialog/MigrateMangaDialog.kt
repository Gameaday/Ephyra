package ephyra.feature.migration.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastForEach
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.hasCustomCover
import ephyra.domain.manga.service.CoverCache
import ephyra.domain.migration.models.MigrationFlag
import ephyra.domain.migration.usecases.MigrateMangaUseCase
import ephyra.domain.source.service.SourcePreferences
import ephyra.presentation.core.components.LabeledCheckbox
import ephyra.presentation.core.components.material.padding
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.core.udf.BaseUdfViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.collections.toMutableSet

private fun MigrationFlag.getLabel(): Int {
    return when (this) {
        MigrationFlag.CHAPTER -> ephyra.app.core.common.R.string.chapters
        MigrationFlag.CATEGORY -> ephyra.app.core.common.R.string.categories
        MigrationFlag.CUSTOM_COVER -> ephyra.app.core.common.R.string.custom_cover
        MigrationFlag.NOTES -> ephyra.app.core.common.R.string.action_notes
        MigrationFlag.REMOVE_DOWNLOAD -> ephyra.app.core.common.R.string.delete_downloaded
    }
}

@Composable
fun MigrateMangaDialog(
    current: Manga,
    target: Manga,
    onClickTitle: () -> Unit,
    onDismissRequest: () -> Unit,
    onComplete: () -> Unit = onDismissRequest,
) {
    val viewModel = hiltViewModel<MigrateDialogViewModel>()
    LaunchedEffect(current, target) {
        viewModel.onEvent(MigrateDialogEvent.Init(current, target))
    }
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                MigrateDialogEffect.MigrationCompleted -> onComplete()
            }
        }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.isMigrated) return

    if (state.isMigrating) {
        LoadingScreen(
            modifier = Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)),
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(ephyra.app.core.common.R.string.migration_dialog_what_to_include))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                state.applicableFlags.fastForEach { flag ->
                    LabeledCheckbox(
                        label = stringResource(flag.getLabel()),
                        checked = flag in state.selectedFlags,
                        onCheckedChange = { viewModel.onEvent(MigrateDialogEvent.ToggleSelection(flag)) },
                    )
                }
            }
        },
        confirmButton = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            ) {
                TextButton(
                    onClick = {
                        onDismissRequest()
                        onClickTitle()
                    },
                ) {
                    Text(text = stringResource(ephyra.app.core.common.R.string.action_show_manga))
                }

                Spacer(modifier = Modifier.weight(1f))

                TextButton(
                    onClick = {
                        viewModel.onEvent(MigrateDialogEvent.Migrate(replace = false))
                    },
                ) {
                    Text(text = stringResource(ephyra.app.core.common.R.string.copy))
                }
                TextButton(
                    onClick = {
                        viewModel.onEvent(MigrateDialogEvent.Migrate(replace = true))
                    },
                ) {
                    Text(text = stringResource(ephyra.app.core.common.R.string.migrate))
                }
            }
        },
    )
}

sealed interface MigrateDialogEvent {
    data class Init(val current: Manga, val target: Manga) : MigrateDialogEvent
    data class ToggleSelection(val flag: MigrationFlag) : MigrateDialogEvent
    data class Migrate(val replace: Boolean) : MigrateDialogEvent
}

sealed interface MigrateDialogEffect {
    data object MigrationCompleted : MigrateDialogEffect
}

@HiltViewModel
class MigrateDialogViewModel @Inject constructor(
    private val sourcePreference: SourcePreferences,
    private val coverCache: CoverCache,
    private val downloadManager: DownloadManager,
    private val migrateMangaUseCase: MigrateMangaUseCase,
) : BaseUdfViewModel<MigrateDialogViewModel.State, MigrateDialogEvent, MigrateDialogEffect>(State()) {

    override fun onEvent(event: MigrateDialogEvent) {
        when (event) {
            is MigrateDialogEvent.Init -> initManga(event.current, event.target)
            is MigrateDialogEvent.ToggleSelection -> toggleSelectionInternal(event.flag)
            is MigrateDialogEvent.Migrate -> migrateMangaInternal(event.replace)
        }
    }

    fun init(current: Manga, target: Manga) = onEvent(MigrateDialogEvent.Init(current, target))

    fun toggleSelection(flag: MigrationFlag) = onEvent(MigrateDialogEvent.ToggleSelection(flag))

    suspend fun migrateManga(replace: Boolean) = migrateMangaInternal(replace)

    private fun initManga(current: Manga, target: Manga) {
        val applicableFlags = buildList {
            MigrationFlag.entries.forEach {
                val applicable = when (it) {
                    MigrationFlag.CHAPTER -> true
                    MigrationFlag.CATEGORY -> true
                    MigrationFlag.CUSTOM_COVER -> current.hasCustomCover(coverCache)
                    MigrationFlag.NOTES -> current.notes.isNotBlank()
                    MigrationFlag.REMOVE_DOWNLOAD -> downloadManager.getDownloadCount(current) > 0
                }
                if (applicable) add(it)
            }
        }
        val selectedFlags = sourcePreference.migrationFlags().getSync()
        updateState {
            State(
                current = current,
                target = target,
                applicableFlags = applicableFlags,
                selectedFlags = selectedFlags,
            )
        }
    }

    private fun toggleSelectionInternal(flag: MigrationFlag) {
        updateState {
            val selectedFlags = it.selectedFlags.toMutableSet()
                .apply { if (contains(flag)) remove(flag) else add(flag) }
                .toSet()
            it.copy(selectedFlags = selectedFlags)
        }
    }

    private fun migrateMangaInternal(replace: Boolean) {
        viewModelScope.launch {
            val currentState = state.value
            val current = currentState.current ?: return@launch
            val target = currentState.target ?: return@launch
            sourcePreference.migrationFlags().set(currentState.selectedFlags)
            updateState { it.copy(isMigrating = true) }
            migrateMangaUseCase(current, target, replace)
            updateState { it.copy(isMigrating = false, isMigrated = true) }
            emitEffect(MigrateDialogEffect.MigrationCompleted)
        }
    }

    data class State(
        val current: Manga? = null,
        val target: Manga? = null,
        val applicableFlags: List<MigrationFlag> = emptyList(),
        val selectedFlags: Set<MigrationFlag> = emptySet(),
        val isMigrating: Boolean = false,
        val isMigrated: Boolean = false,
    )
}
