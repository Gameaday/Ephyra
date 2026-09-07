package ephyra.feature.browse.migration.sources

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.util.lang.launchIO
import ephyra.core.common.util.system.logcat
import ephyra.domain.source.interactor.GetSourcesWithFavoriteCount
import ephyra.domain.source.interactor.SetMigrateSorting
import ephyra.domain.source.model.Source
import ephyra.domain.source.service.SourcePreferences
import ephyra.presentation.core.udf.BaseUdfViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority
import javax.inject.Inject

@HiltViewModel
class MigrateSourceViewModel @Inject constructor(
    preferences: SourcePreferences,
    private val getSourcesWithFavoriteCount: GetSourcesWithFavoriteCount,
    private val setMigrateSorting: SetMigrateSorting,
) : BaseUdfViewModel<MigrateSourceViewModel.State, MigrateSourceScreenEvent, MigrateSourceViewModel.Effect>(State()) {

    val channel = effects

    init {
        viewModelScope.launchIO {
            getSourcesWithFavoriteCount.subscribe()
                .catch {
                    logcat(LogPriority.ERROR, it)
                    emitEffect(Effect.FailedFetchingSourcesWithCount)
                }
                .collectLatest { sources ->
                    updateState {
                        it.copy(
                            isLoading = false,
                            items = sources.toImmutableList(),
                        )
                    }
                }
        }

        preferences.migrationSortingDirection().changes()
            .onEach { dir -> updateState { state -> state.copy(sortingDirection = dir) } }
            .launchIn(viewModelScope)

        preferences.migrationSortingMode().changes()
            .onEach { mode -> updateState { state -> state.copy(sortingMode = mode) } }
            .launchIn(viewModelScope)
    }

    override fun onEvent(event: MigrateSourceScreenEvent) {
        when (event) {
            MigrateSourceScreenEvent.ToggleSortingMode -> toggleSortingMode()
            MigrateSourceScreenEvent.ToggleSortingDirection -> toggleSortingDirection()
        }
    }

    private fun toggleSortingMode() {
        with(currentState) {
            val newMode = when (sortingMode) {
                SetMigrateSorting.Mode.ALPHABETICAL -> SetMigrateSorting.Mode.TOTAL
                SetMigrateSorting.Mode.TOTAL -> SetMigrateSorting.Mode.ALPHABETICAL
            }

            setMigrateSorting.await(newMode, sortingDirection)
        }
    }

    private fun toggleSortingDirection() {
        with(currentState) {
            val newDirection = when (sortingDirection) {
                SetMigrateSorting.Direction.ASCENDING -> SetMigrateSorting.Direction.DESCENDING
                SetMigrateSorting.Direction.DESCENDING -> SetMigrateSorting.Direction.ASCENDING
            }

            setMigrateSorting.await(sortingMode, newDirection)
        }
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val items: ImmutableList<Pair<Source, Long>> = persistentListOf(),
        val sortingMode: SetMigrateSorting.Mode = SetMigrateSorting.Mode.ALPHABETICAL,
        val sortingDirection: SetMigrateSorting.Direction = SetMigrateSorting.Direction.ASCENDING,
    ) {
        val isEmpty = items.isEmpty()
    }

    sealed interface Effect {
        data object FailedFetchingSourcesWithCount : Effect
    }
}
