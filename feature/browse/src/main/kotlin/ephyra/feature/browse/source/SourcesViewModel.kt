package ephyra.feature.browse.source

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.util.Result
import ephyra.core.common.util.system.logcat
import ephyra.domain.content.source.interactor.AddCustomSource
import ephyra.domain.source.interactor.GetEnabledSources
import ephyra.domain.source.interactor.ToggleSource
import ephyra.domain.source.interactor.ToggleSourcePin
import ephyra.domain.source.model.Pin
import ephyra.domain.source.model.Source
import ephyra.feature.browse.presentation.SourceUiModel
import ephyra.presentation.core.components.SEARCH_DEBOUNCE_MILLIS
import ephyra.presentation.core.udf.BaseUdfViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import java.util.TreeMap
import javax.inject.Inject

@HiltViewModel
@OptIn(kotlinx.coroutines.FlowPreview::class)
class SourcesViewModel @Inject constructor(
    private val getEnabledSources: GetEnabledSources,
    private val toggleSource: ToggleSource,
    private val toggleSourcePin: ToggleSourcePin,
    private val addCustomSource: AddCustomSource,
) : BaseUdfViewModel<SourcesViewModel.State, SourcesScreenEvent, SourcesViewModel.Effect>(State()) {

    init {
        viewModelScope.launch {
            combine(
                getEnabledSources.subscribe(),
                state.map { it.searchQuery }
                    .distinctUntilChanged()
                    .debounce(SEARCH_DEBOUNCE_MILLIS),
            ) { sources, query ->
                if (query.isNullOrBlank()) {
                    sources
                } else {
                    sources.filter { source ->
                        source.name.contains(query, ignoreCase = true) ||
                            source.lang.contains(query, ignoreCase = true)
                    }
                }
            }
                .catch {
                    logcat(LogPriority.ERROR, it)
                    emitEffect(Effect.FailedFetchingSources)
                }
                .collectLatest(::collectLatestSources)
        }
    }

    private fun collectLatestSources(sources: List<Source>) {
        updateState { state ->
            val map = TreeMap<String, MutableList<Source>> { d1, d2 ->
                // Sources without a lang defined will be placed at the end
                when {
                    d1 == LAST_USED_KEY && d2 != LAST_USED_KEY -> -1
                    d2 == LAST_USED_KEY && d1 != LAST_USED_KEY -> 1
                    d1 == PINNED_KEY && d2 != PINNED_KEY -> -1
                    d2 == PINNED_KEY && d1 != PINNED_KEY -> 1
                    d1 == "" && d2 != "" -> 1
                    d2 == "" && d1 != "" -> -1
                    else -> d1.compareTo(d2)
                }
            }
            val byLang = sources.groupByTo(map) {
                when {
                    it.isUsedLast -> LAST_USED_KEY
                    Pin.Actual in it.pin -> PINNED_KEY
                    else -> it.lang
                }
            }

            state.copy(
                isLoading = false,
                items = buildList(sources.size + byLang.size) {
                    byLang.forEach { (key, langSources) ->
                        add(SourceUiModel.Header(key))
                        langSources.mapTo(this) { SourceUiModel.Item(it) }
                    }
                }.toImmutableList(),
            )
        }
    }

    fun search(query: String?) {
        updateState {
            it.copy(searchQuery = query)
        }
    }

    override fun onEvent(event: SourcesScreenEvent) {
        when (event) {
            is SourcesScreenEvent.ToggleSource -> toggleSource(event.source)
            is SourcesScreenEvent.TogglePin -> togglePin(event.source)
            is SourcesScreenEvent.ShowSourceDialog -> showSourceDialog(event.source)
            SourcesScreenEvent.CloseDialog -> closeDialog()
            is SourcesScreenEvent.Search -> search(event.query)
            is SourcesScreenEvent.AddWebSource -> addWebSource(event.url, event.name)
        }
    }

    fun addWebSource(url: String, name: String? = null) {
        viewModelScope.launch {
            try {
                val result = addCustomSource.addHeuristicProfile(url, name)
                if (result is Result.Success) {
                    emitEffect(Effect.WebSourceAdded(result.data.displayName))
                } else if (result is Result.Error) {
                    emitEffect(Effect.WebSourceAddFailed(result.exception.message ?: "Failed to discover source"))
                }
            } catch (e: Exception) {
                emitEffect(Effect.WebSourceAddFailed(e.message ?: "Failed to discover source"))
            }
        }
    }

    private fun toggleSource(source: Source) {
        viewModelScope.launch { toggleSource.await(source) }
    }

    private fun togglePin(source: Source) {
        viewModelScope.launch { toggleSourcePin.await(source) }
    }

    private fun showSourceDialog(source: Source) {
        updateState { it.copy(dialog = Dialog(source)) }
    }

    private fun closeDialog() {
        updateState { it.copy(dialog = null) }
    }

    sealed interface Effect {
        data object FailedFetchingSources : Effect
        data class WebSourceAdded(val name: String) : Effect
        data class WebSourceAddFailed(val error: String) : Effect
    }

    data class Dialog(val source: Source)

    @Immutable
    data class State(
        val dialog: Dialog? = null,
        val isLoading: Boolean = true,
        val items: ImmutableList<SourceUiModel> = persistentListOf(),
        val searchQuery: String? = null,
    ) {
        val isEmpty = items.isEmpty()
    }

    companion object {
        const val PINNED_KEY = "pinned"
        const val LAST_USED_KEY = "last_used"
    }
}
