package ephyra.feature.settings.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.util.Result
import ephyra.domain.content.source.ScraperMetadata
import ephyra.domain.content.source.SourceProfile
import ephyra.domain.content.source.SourceType
import ephyra.domain.content.source.interactor.AddCustomSource
import ephyra.domain.content.source.interactor.GetAvailableSources
import ephyra.domain.content.source.interactor.RemoveCustomSource
import ephyra.domain.content.source.interactor.UnifiedSource
import ephyra.domain.content.source.interactor.UpdateCustomSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
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
            val result = addCustomSource.addJsScraper(githubUrl, filename)
            _isLoading.value = false
            when (result) {
                is Result.Success -> {
                    loadSources()
                }
                is Result.Error -> {
                    _error.value = result.exception.message ?: "Failed to add scraper"
                }
                else -> {}
            }
        }
    }

    fun importJsScraper(filename: String, scriptContent: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = addCustomSource.importJsScraper(filename, scriptContent)
            _isLoading.value = false
            when (result) {
                is Result.Success -> {
                    loadSources()
                }
                is Result.Error -> {
                    _error.value = result.exception.message ?: "Failed to import scraper"
                }
                else -> {}
            }
        }
    }

    fun addHeuristicProfile(baseUrl: String, displayName: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = addCustomSource.addHeuristicProfile(baseUrl, displayName)
            _isLoading.value = false
            when (result) {
                is Result.Success -> {
                    loadSources()
                }
                is Result.Error -> {
                    _error.value = result.exception.message ?: "Failed to add profile"
                }
                else -> {}
            }
        }
    }

    fun linkScraperToUrl(baseUrl: String, scraperFilename: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = addCustomSource.linkScraperToUrl(baseUrl, scraperFilename)
            _isLoading.value = false
            when (result) {
                is Result.Success -> {
                    loadSources()
                }
                is Result.Error -> {
                    _error.value = result.exception.message ?: "Failed to link scraper"
                }
                else -> {}
            }
        }
    }

    fun checkAndUpdateScraper(baseUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = updateCustomSource.checkAndUpdateScraper(baseUrl)
            _isLoading.value = false
            when (result) {
                is Result.Success -> {
                    loadSources()
                }
                is Result.Error -> {
                    _error.value = result.exception.message ?: "Failed to update scraper"
                }
                else -> {}
            }
        }
    }

    fun forceRediscover(baseUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = updateCustomSource.forceRediscover(baseUrl)
            _isLoading.value = false
            when (result) {
                is Result.Success -> {
                    loadSources()
                }
                is Result.Error -> {
                    _error.value = result.exception.message ?: "Failed to rediscover"
                }
                else -> {}
            }
        }
    }

    fun renameScraper(baseUrl: String, newFilename: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = updateCustomSource.renameScraper(baseUrl, newFilename)
            _isLoading.value = false
            when (result) {
                is Result.Success -> {
                    loadSources()
                }
                is Result.Error -> {
                    _error.value = result.exception.message ?: "Failed to rename scraper"
                }
                else -> {}
            }
        }
    }

    fun removeSource(baseUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = removeCustomSource.removeSource(baseUrl)
            _isLoading.value = false
            when (result) {
                is Result.Success -> {
                    loadSources()
                }
                is Result.Error -> {
                    _error.value = result.exception.message ?: "Failed to remove source"
                }
                else -> {}
            }
        }
    }

    fun disableSource(baseUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = removeCustomSource.disableSource(baseUrl)
            _isLoading.value = false
            when (result) {
                is Result.Success -> {
                    loadSources()
                }
                is Result.Error -> {
                    _error.value = result.exception.message ?: "Failed to disable source"
                }
                else -> {}
            }
        }
    }

    fun unlinkScraper(baseUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = removeCustomSource.unlinkScraper(baseUrl)
            _isLoading.value = false
            when (result) {
                is Result.Success -> {
                    loadSources()
                }
                is Result.Error -> {
                    _error.value = result.exception.message ?: "Failed to unlink scraper"
                }
                else -> {}
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
