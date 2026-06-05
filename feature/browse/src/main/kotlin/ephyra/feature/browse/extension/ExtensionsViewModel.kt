package ephyra.feature.browse.extension

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.util.Result
import ephyra.core.common.util.lang.launchIO
import ephyra.domain.content.source.SourceType
import ephyra.domain.content.source.interactor.AddCustomSource
import ephyra.domain.content.source.interactor.GetAvailableSources
import ephyra.domain.content.source.interactor.RemoveCustomSource
import ephyra.domain.content.source.interactor.UnifiedSource
import ephyra.domain.content.source.interactor.UpdateCustomSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExtensionsViewModel @Inject constructor(
    private val context: Application,
    private val getAvailableSources: GetAvailableSources,
    private val addCustomSource: AddCustomSource,
    private val updateCustomSource: UpdateCustomSource,
    private val removeCustomSource: RemoveCustomSource,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        loadSources()
    }

    fun loadSources() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            getAvailableSources().collectLatest { result ->
                _state.update { it.copy(sources = result, isLoading = false) }
            }
        }
    }

    fun addJsScraper(githubUrl: String, filename: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = addCustomSource.addJsScraper(githubUrl, filename)
            _state.update { it.copy(isLoading = false) }
            when (result) {
                is Result.Success -> loadSources()
                is Result.Error -> _state.update {
                    it.copy(error = result.exception.message ?: "Failed to add scraper")
                }
                else -> {}
            }
        }
    }

    fun importJsScraper(filename: String, scriptContent: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = addCustomSource.importJsScraper(filename, scriptContent)
            _state.update { it.copy(isLoading = false) }
            when (result) {
                is Result.Success -> loadSources()
                is Result.Error -> _state.update {
                    it.copy(
                        error =
                        result.exception.message ?: "Failed to import scraper",
                    )
                }
                else -> {}
            }
        }
    }

    fun addHeuristicProfile(baseUrl: String, displayName: String?) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = addCustomSource.addHeuristicProfile(baseUrl, displayName)
            _state.update { it.copy(isLoading = false) }
            when (result) {
                is Result.Success -> loadSources()
                is Result.Error -> _state.update {
                    it.copy(error = result.exception.message ?: "Failed to add profile")
                }
                else -> {}
            }
        }
    }

    fun linkScraperToUrl(baseUrl: String, scraperFilename: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = addCustomSource.linkScraperToUrl(baseUrl, scraperFilename)
            _state.update { it.copy(isLoading = false) }
            when (result) {
                is Result.Success -> loadSources()
                is Result.Error -> _state.update {
                    it.copy(error = result.exception.message ?: "Failed to link scraper")
                }
                else -> {}
            }
        }
    }

    fun checkAndUpdateScraper(baseUrl: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = updateCustomSource.checkAndUpdateScraper(baseUrl)
            _state.update { it.copy(isLoading = false) }
            when (result) {
                is Result.Success -> loadSources()
                is Result.Error -> _state.update {
                    it.copy(
                        error =
                        result.exception.message ?: "Failed to update scraper",
                    )
                }
                else -> {}
            }
        }
    }

    fun forceRediscover(baseUrl: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = updateCustomSource.forceRediscover(baseUrl)
            _state.update { it.copy(isLoading = false) }
            when (result) {
                is Result.Success -> loadSources()
                is Result.Error -> _state.update { it.copy(error = result.exception.message ?: "Failed to rediscover") }
                else -> {}
            }
        }
    }

    fun removeSource(baseUrl: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = removeCustomSource.removeSource(baseUrl)
            _state.update { it.copy(isLoading = false) }
            when (result) {
                is Result.Success -> loadSources()
                is Result.Error -> _state.update {
                    it.copy(error = result.exception.message ?: "Failed to remove source")
                }
                else -> {}
            }
        }
    }

    fun search(query: String?) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    data class State(
        val isLoading: Boolean = false,
        val sources: List<UnifiedSource> = emptyList(),
        val searchQuery: String? = null,
        val error: String? = null,
    )
}
