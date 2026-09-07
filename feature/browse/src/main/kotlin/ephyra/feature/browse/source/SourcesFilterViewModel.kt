package ephyra.feature.browse.source

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.domain.source.interactor.GetLanguagesWithSources
import ephyra.domain.source.interactor.ToggleLanguage
import ephyra.domain.source.interactor.ToggleSource
import ephyra.domain.source.model.Source
import ephyra.domain.source.service.SourcePreferences
import ephyra.presentation.core.udf.BaseUdfViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.SortedMap
import javax.inject.Inject

@HiltViewModel
class SourcesFilterViewModel @Inject constructor(
    private val preferences: SourcePreferences,
    private val getLanguagesWithSources: GetLanguagesWithSources,
    private val toggleSource: ToggleSource,
    private val toggleLanguage: ToggleLanguage,
) : BaseUdfViewModel<SourcesFilterViewModel.State, SourcesFilterScreenEvent, Nothing>(State.Loading) {

    init {
        viewModelScope.launch {
            combine(
                getLanguagesWithSources.subscribe(),
                preferences.enabledLanguages().changes(),
                preferences.disabledSources().changes(),
            ) { a, b, c -> Triple(a, b, c) }
                .catch { throwable ->
                    updateState {
                        State.Error(
                            throwable = throwable,
                        )
                    }
                }
                .collectLatest { (languagesWithSources, enabledLanguages, disabledSources) ->
                    updateState {
                        State.Success(
                            items = languagesWithSources,
                            enabledLanguages = enabledLanguages,
                            disabledSources = disabledSources,
                        )
                    }
                }
        }
    }

    override fun onEvent(event: SourcesFilterScreenEvent) {
        when (event) {
            is SourcesFilterScreenEvent.ToggleSource -> toggleSource(event.source)
            is SourcesFilterScreenEvent.ToggleLanguage -> toggleLanguage(event.language)
        }
    }

    private fun toggleSource(source: Source) {
        viewModelScope.launch { toggleSource.await(source) }
    }

    private fun toggleLanguage(language: String) {
        viewModelScope.launch { toggleLanguage.await(language) }
    }

    sealed interface State {

        @Immutable
        data object Loading : State

        @Immutable
        data class Error(
            val throwable: Throwable,
        ) : State

        @Immutable
        data class Success(
            val items: SortedMap<String, List<Source>>,
            val enabledLanguages: Set<String>,
            val disabledSources: Set<String>,
        ) : State {

            val isEmpty: Boolean
                get() = items.isEmpty()

            val disabledSourceIds: Set<Long> by lazy {
                disabledSources.mapNotNullTo(HashSet()) { it.toLongOrNull() }
            }
        }
    }
}
