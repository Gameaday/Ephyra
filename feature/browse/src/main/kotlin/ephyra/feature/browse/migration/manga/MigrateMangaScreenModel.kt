package ephyra.feature.browse.migration.manga

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.util.system.logcat
import ephyra.core.common.utils.mutate
import ephyra.domain.manga.interactor.GetFavorites
import ephyra.domain.manga.model.Manga
import ephyra.domain.source.service.SourceManager
import eu.kanade.tachiyomi.source.Source
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import javax.inject.Inject

@HiltViewModel
class MigrateMangaScreenModel @Inject constructor(
    private val sourceManager: SourceManager,
    private val getFavorites: GetFavorites,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _events: Channel<MigrationMangaEvent> = Channel()
    val events: Flow<MigrationMangaEvent> = _events.receiveAsFlow()

    private var isInitialized = false

    fun init(sourceId: Long) {
        if (isInitialized) return
        isInitialized = true

        viewModelScope.launch {
            _state.update { state ->
                state.copy(source = sourceManager.getOrStub(sourceId))
            }

            getFavorites.subscribe(sourceId)
                .catch {
                    logcat(LogPriority.ERROR, it)
                    _events.send(MigrationMangaEvent.FailedFetchingFavorites)
                    _state.update { state ->
                        state.copy(titleList = persistentListOf())
                    }
                }
                .map { manga ->
                    manga
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                        .toImmutableList()
                }
                .collectLatest { list ->
                    _state.update { it.copy(titleList = list) }
                }
        }
    }

    fun onEvent(event: MigrateMangaScreenEvent) {
        when (event) {
            is MigrateMangaScreenEvent.ToggleSelection -> toggleSelection(event.item)
            MigrateMangaScreenEvent.ClearSelection -> clearSelection()
        }
    }

    private fun toggleSelection(item: Manga) {
        _state.update { state ->
            val selection = state.selection.mutate { list ->
                if (!list.remove(item.id)) list.add(item.id)
            }
            state.copy(selection = selection)
        }
    }

    private fun clearSelection() {
        _state.update { it.copy(selection = emptySet()) }
    }

    @Immutable
    data class State(
        val source: Source? = null,
        val selection: Set<Long> = emptySet(),
        private val titleList: ImmutableList<Manga>? = null,
    ) {

        val titles: ImmutableList<Manga>
            get() = titleList ?: persistentListOf()

        val isLoading: Boolean
            get() = source == null || titleList == null

        val isEmpty: Boolean
            get() = titles.isEmpty()

        val selectionMode = selection.isNotEmpty()
    }
}

sealed interface MigrationMangaEvent {
    data object FailedFetchingFavorites : MigrationMangaEvent
}
