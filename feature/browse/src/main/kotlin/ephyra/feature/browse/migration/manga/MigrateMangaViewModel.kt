package ephyra.feature.browse.migration.manga

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.util.system.logcat
import ephyra.domain.manga.interactor.GetFavorites
import ephyra.domain.manga.model.Manga
import ephyra.domain.source.service.SourceManager
import ephyra.presentation.core.udf.BaseUdfViewModel
import eu.kanade.tachiyomi.source.Source
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import logcat.LogPriority
import javax.inject.Inject

@HiltViewModel
class MigrateMangaViewModel @Inject constructor(
    private val sourceManager: SourceManager,
    private val getFavorites: GetFavorites,
) : BaseUdfViewModel<MigrateMangaViewModel.State, MigrateMangaScreenEvent, MigrateMangaViewModel.Effect>(State()) {

    val events = effects

    private var isInitialized = false

    fun init(sourceId: Long) {
        if (isInitialized) return
        isInitialized = true

        viewModelScope.launch {
            updateState { state ->
                state.copy(source = sourceManager.getOrStub(sourceId))
            }

            getFavorites.subscribe(sourceId)
                .catch {
                    logcat(LogPriority.ERROR, it)
                    emitEffect(Effect.FailedFetchingFavorites)
                    updateState { state ->
                        state.copy(titleList = persistentListOf())
                    }
                }
                .map { manga ->
                    manga
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                        .toImmutableList()
                }
                .collectLatest { list ->
                    updateState { it.copy(titleList = list) }
                }
        }
    }

    override fun onEvent(event: MigrateMangaScreenEvent) {
        when (event) {
            is MigrateMangaScreenEvent.ToggleSelection -> toggleSelection(event.item)
            MigrateMangaScreenEvent.ClearSelection -> clearSelection()
        }
    }

    private fun toggleSelection(item: Manga) {
        updateState { state ->
            val selection = state.selection.toMutableSet().apply {
                if (!remove(item.id)) add(item.id)
            }.toPersistentSet()
            state.copy(selection = selection)
        }
    }

    private fun clearSelection() {
        updateState { it.copy(selection = persistentSetOf()) }
    }

    @Immutable
    data class State(
        val source: Source? = null,
        val selection: ImmutableSet<Long> = persistentSetOf(),
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

    sealed interface Effect {
        data object FailedFetchingFavorites : Effect
    }
}
