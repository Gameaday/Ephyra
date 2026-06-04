package ephyra.feature.settings.screen

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.util.Result
import ephyra.domain.content.source.ScraperMetadata
import ephyra.domain.content.source.SourceProfile
import ephyra.domain.content.source.SourceType
import ephyra.domain.content.source.interactor.AddCustomSource
import ephyra.domain.content.source.interactor.GetAvailableSources
import ephyra.domain.content.source.interactor.RemoveCustomSource
import ephyra.domain.content.source.interactor.UpdateCustomSource
import ephyra.domain.content.source.model.UnifiedSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SourceManagementViewModel @Inject constructor(
    private val getAvailableSources: GetAvailableSources,
    private val addCustomSource: AddCustomSource,
    private val updateCustomSource: UpdateCustomSource,
    private val removeCustomSource: RemoveCustomSource,
) : ViewModel() {

    private val _sources = MutableStateFlow<List<UnifiedSource>>(emptyList())
    val sources: StateFlow<List<UnifiedSource>> = _sources

    private val _scraperMetadata = MutableStateFlow<Map<String, ScraperMetadata>>(emptyMap())
    val scraperMetadata: StateFlow<Map<String, ScraperMetadata>> = _scraperMetadata

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        loadSources()
    }

    fun loadSources() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            getAvailableSources().collectLatest { result ->
                _sources.value = result
                _isLoading.value = false
            }
        }
    }

    fun addJsScraper(githubUrl: String, filename: String) {
        viewModelScope.launch {
            _isLoading.value = true
            addCustomSource.addJsScraper(githubUrl, filename).onResult { result ->
                _isLoading.value = false
                when (result) {
                    is Result.Success -> {
                        loadSources()
                    }
                    is Result.Error -> {
                        _error.value = result.exception.message ?: "Failed to add scraper"
                    }
                }
            }
        }
    }

    fun importJsScraper(filename: String, scriptContent: String) {
        viewModelScope.launch {
            _isLoading.value = true
            addCustomSource.importJsScraper(filename, scriptContent).onResult { result ->
                _isLoading.value = false
                when (result) {
                    is Result.Success -> {
                        loadSources()
                    }
                    is Result.Error -> {
                        _error.value = result.exception.message ?: "Failed to import scraper"
                    }
                }
            }
        }
    }

    fun addHeuristicProfile(baseUrl: String, displayName: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            addCustomSource.addHeuristicProfile(baseUrl, displayName).onResult { result ->
                _isLoading.value = false
                when (result) {
                    is Result.Success -> {
                        loadSources()
                    }
                    is Result.Error -> {
                        _error.value = result.exception.message ?: "Failed to add profile"
                    }
                }
            }
        }
    }

    fun linkScraperToUrl(baseUrl: String, scraperFilename: String) {
        viewModelScope.launch {
            _isLoading.value = true
            addCustomSource.linkScraperToUrl(baseUrl, scraperFilename).onResult { result ->
                _isLoading.value = false
                when (result) {
                    is Result.Success -> {
                        loadSources()
                    }
                    is Result.Error -> {
                        _error.value = result.exception.message ?: "Failed to link scraper"
                    }
                }
            }
        }
    }

    fun checkAndUpdateScraper(baseUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            updateCustomSource.checkAndUpdateScraper(baseUrl).onResult { result ->
                _isLoading.value = false
                when (result) {
                    is Result.Success -> {
                        loadSources()
                    }
                    is Result.Error -> {
                        _error.value = result.exception.message ?: "Failed to update scraper"
                    }
                }
            }
        }
    }

    fun forceRediscover(baseUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            updateCustomSource.forceRediscover(baseUrl).onResult { result ->
                _isLoading.value = false
                when (result) {
                    is Result.Success -> {
                        loadSources()
                    }
                    is Result.Error -> {
                        _error.value = result.exception.message ?: "Failed to rediscover"
                    }
                }
            }
        }
    }

    fun renameScraper(baseUrl: String, newFilename: String) {
        viewModelScope.launch {
            _isLoading.value = true
            updateCustomSource.renameScraper(baseUrl, newFilename).onResult { result ->
                _isLoading.value = false
                when (result) {
                    is Result.Success -> {
                        loadSources()
                    }
                    is Result.Error -> {
                        _error.value = result.exception.message ?: "Failed to rename scraper"
                    }
                }
            }
        }
    }

    fun removeSource(baseUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            removeCustomSource.removeSource(baseUrl).onResult { result ->
                _isLoading.value = false
                when (result) {
                    is Result.Success -> {
                        loadSources()
                    }
                    is Result.Error -> {
                        _error.value = result.exception.message ?: "Failed to remove source"
                    }
                }
            }
        }
    }

    fun disableSource(baseUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            removeCustomSource.disableSource(baseUrl).onResult { result ->
                _isLoading.value = false
                when (result) {
                    is Result.Success -> {
                        loadSources()
                    }
                    is Result.Error -> {
                        _error.value = result.exception.message ?: "Failed to disable source"
                    }
                }
            }
        }
    }

    fun unlinkScraper(baseUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            removeCustomSource.unlinkScraper(baseUrl).onResult { result ->
                _isLoading.value = false
                when (result) {
                    is Result.Success -> {
                        loadSources()
                    }
                    is Result.Error -> {
                        _error.value = result.exception.message ?: "Failed to unlink scraper"
                    }
                }
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
