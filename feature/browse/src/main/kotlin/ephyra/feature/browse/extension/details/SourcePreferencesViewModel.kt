package ephyra.feature.browse.extension.details

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.domain.source.service.SourceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SourcePreferencesViewModel @Inject constructor(
    val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val sourceManager: SourceManager,
) : ViewModel() {

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val sourceTitle: String = "",
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var isInitialized = false

    init {
        val navSourceId: Long? = savedStateHandle.get<Long>("sourceId")
            ?: savedStateHandle.get<String>("sourceId")?.toLongOrNull()
        if (navSourceId != null && navSourceId > 0L) {
            init(navSourceId)
        }
    }

    fun init(sourceId: Long) {
        if (isInitialized && _state.value.sourceTitle.isNotEmpty()) return
        isInitialized = true
        savedStateHandle["sourceId"] = sourceId

        viewModelScope.launch {
            sourceManager.isInitialized.first { it }
            val source = sourceManager.getOrStub(sourceId)
            _state.update {
                it.copy(
                    isLoading = false,
                    sourceTitle = source.name.ifEmpty { source.toString() },
                )
            }
        }
    }

    fun getConfigurableSource(sourceId: Long): eu.kanade.tachiyomi.source.ConfigurableSource? {
        return (
            sourceManager.get(
                sourceId,
            ) ?: sourceManager.getOrStub(sourceId)
            ) as? eu.kanade.tachiyomi.source.ConfigurableSource
    }
}
