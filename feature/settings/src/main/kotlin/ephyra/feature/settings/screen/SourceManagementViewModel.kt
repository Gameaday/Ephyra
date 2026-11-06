package ephyra.feature.settings.screen

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.domain.content.source.interactor.GetAvailableSources
import ephyra.domain.content.source.interactor.UnifiedSource
import ephyra.presentation.core.udf.BaseUdfViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Projects the available sources for the Sources screen. ADR-0017 deleted the custom-source
 * mutations this ViewModel used to forward (rediscover / remove / disable): those operated on
 * the profile path, which no longer exists — the only sources are extension sources, and their
 * lifecycle belongs to the extension screens, not here.
 */
@HiltViewModel
class SourceManagementViewModel @Inject constructor(
    private val getAvailableSources: GetAvailableSources,
) : BaseUdfViewModel<SourceManagementState, SourceManagementEvent, SourceManagementEffect>(SourceManagementState()) {

    init {
        loadSources()
    }

    override fun onEvent(event: SourceManagementEvent) {
        when (event) {
            SourceManagementEvent.LoadSources -> loadSources()
            SourceManagementEvent.ClearError -> clearError()
        }
    }

    fun loadSources() {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true, error = null) }
            getAvailableSources().collectLatest { result ->
                updateState { it.copy(sources = result, isLoading = false) }
            }
        }
    }

    fun clearError() {
        updateState { it.copy(error = null) }
    }
}

@Immutable
data class SourceManagementState(
    val sources: List<UnifiedSource> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

sealed interface SourceManagementEvent {
    data object LoadSources : SourceManagementEvent
    data object ClearError : SourceManagementEvent
}

sealed interface SourceManagementEffect {
    data class ShowSnackbar(val message: String) : SourceManagementEffect
}
