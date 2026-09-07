package ephyra.feature.settings.screen

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.util.Result
import ephyra.domain.content.source.ScraperMetadata
import ephyra.domain.content.source.interactor.AddCustomSource
import ephyra.domain.content.source.interactor.GetAvailableSources
import ephyra.domain.content.source.interactor.RemoveCustomSource
import ephyra.domain.content.source.interactor.UnifiedSource
import ephyra.domain.content.source.interactor.UpdateCustomSource
import ephyra.presentation.core.udf.BaseUdfViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SourceManagementViewModel @Inject constructor(
    private val getAvailableSources: GetAvailableSources,
    private val addCustomSource: AddCustomSource,
    private val updateCustomSource: UpdateCustomSource,
    private val removeCustomSource: RemoveCustomSource,
) : BaseUdfViewModel<SourceManagementState, SourceManagementEvent, SourceManagementEffect>(SourceManagementState()) {

    init {
        loadSources()
    }

    override fun onEvent(event: SourceManagementEvent) {
        when (event) {
            SourceManagementEvent.LoadSources -> loadSources()
            is SourceManagementEvent.AddJsScraper -> addJsScraper(event.githubUrl, event.filename)
            is SourceManagementEvent.ImportJsScraper -> importJsScraper(event.filename, event.scriptContent)
            is SourceManagementEvent.AddHeuristicProfile -> addHeuristicProfile(event.baseUrl, event.displayName)
            is SourceManagementEvent.LinkScraperToUrl -> linkScraperToUrl(event.baseUrl, event.scraperFilename)
            is SourceManagementEvent.CheckAndUpdateScraper -> checkAndUpdateScraper(event.baseUrl)
            is SourceManagementEvent.ForceRediscover -> forceRediscover(event.baseUrl)
            is SourceManagementEvent.RenameScraper -> renameScraper(event.baseUrl, event.newFilename)
            is SourceManagementEvent.RemoveSource -> removeSource(event.baseUrl)
            is SourceManagementEvent.DisableSource -> disableSource(event.baseUrl)
            is SourceManagementEvent.UnlinkScraper -> unlinkScraper(event.baseUrl)
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

    fun addJsScraper(githubUrl: String, filename: String) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            val result = addCustomSource.addJsScraper(githubUrl, filename)
            updateState { it.copy(isLoading = false) }
            when (result) {
                is Result.Success -> {
                    loadSources()
                }
                is Result.Error -> {
                    val message = result.exception.message ?: "Failed to add scraper"
                    updateState { it.copy(error = message) }
                    emitEffect(SourceManagementEffect.ShowSnackbar(message))
                }
                else -> {}
            }
        }
    }

    fun importJsScraper(filename: String, scriptContent: String) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            val result = addCustomSource.importJsScraper(filename, scriptContent)
            updateState { it.copy(isLoading = false) }
            when (result) {
                is Result.Success -> {
                    loadSources()
                }
                is Result.Error -> {
                    val message = result.exception.message ?: "Failed to import scraper"
                    updateState { it.copy(error = message) }
                    emitEffect(SourceManagementEffect.ShowSnackbar(message))
                }
                else -> {}
            }
        }
    }

    fun addHeuristicProfile(baseUrl: String, displayName: String?) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            val result = addCustomSource.addHeuristicProfile(baseUrl, displayName)
            updateState { it.copy(isLoading = false) }
            when (result) {
                is Result.Success -> {
                    loadSources()
                }
                is Result.Error -> {
                    val message = result.exception.message ?: "Failed to add profile"
                    updateState { it.copy(error = message) }
                    emitEffect(SourceManagementEffect.ShowSnackbar(message))
                }
                else -> {}
            }
        }
    }

    fun linkScraperToUrl(baseUrl: String, scraperFilename: String) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            val result = addCustomSource.linkScraperToUrl(baseUrl, scraperFilename)
            updateState { it.copy(isLoading = false) }
            when (result) {
                is Result.Success -> {
                    loadSources()
                }
                is Result.Error -> {
                    val message = result.exception.message ?: "Failed to link scraper"
                    updateState { it.copy(error = message) }
                    emitEffect(SourceManagementEffect.ShowSnackbar(message))
                }
                else -> {}
            }
        }
    }

    fun checkAndUpdateScraper(baseUrl: String) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            val result = updateCustomSource.checkAndUpdateScraper(baseUrl)
            updateState { it.copy(isLoading = false) }
            when (result) {
                is Result.Success -> {
                    loadSources()
                }
                is Result.Error -> {
                    val message = result.exception.message ?: "Failed to update scraper"
                    updateState { it.copy(error = message) }
                    emitEffect(SourceManagementEffect.ShowSnackbar(message))
                }
                else -> {}
            }
        }
    }

    fun forceRediscover(baseUrl: String) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            val result = updateCustomSource.forceRediscover(baseUrl)
            updateState { it.copy(isLoading = false) }
            when (result) {
                is Result.Success -> {
                    loadSources()
                }
                is Result.Error -> {
                    val message = result.exception.message ?: "Failed to rediscover"
                    updateState { it.copy(error = message) }
                    emitEffect(SourceManagementEffect.ShowSnackbar(message))
                }
                else -> {}
            }
        }
    }

    fun renameScraper(baseUrl: String, newFilename: String) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            val result = updateCustomSource.renameScraper(baseUrl, newFilename)
            updateState { it.copy(isLoading = false) }
            when (result) {
                is Result.Success -> {
                    loadSources()
                }
                is Result.Error -> {
                    val message = result.exception.message ?: "Failed to rename scraper"
                    updateState { it.copy(error = message) }
                    emitEffect(SourceManagementEffect.ShowSnackbar(message))
                }
                else -> {}
            }
        }
    }

    fun removeSource(baseUrl: String) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            val result = removeCustomSource.removeSource(baseUrl)
            updateState { it.copy(isLoading = false) }
            when (result) {
                is Result.Success -> {
                    loadSources()
                }
                is Result.Error -> {
                    val message = result.exception.message ?: "Failed to remove source"
                    updateState { it.copy(error = message) }
                    emitEffect(SourceManagementEffect.ShowSnackbar(message))
                }
                else -> {}
            }
        }
    }

    fun disableSource(baseUrl: String) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            val result = removeCustomSource.disableSource(baseUrl)
            updateState { it.copy(isLoading = false) }
            when (result) {
                is Result.Success -> {
                    loadSources()
                }
                is Result.Error -> {
                    val message = result.exception.message ?: "Failed to disable source"
                    updateState { it.copy(error = message) }
                    emitEffect(SourceManagementEffect.ShowSnackbar(message))
                }
                else -> {}
            }
        }
    }

    fun unlinkScraper(baseUrl: String) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            val result = removeCustomSource.unlinkScraper(baseUrl)
            updateState { it.copy(isLoading = false) }
            when (result) {
                is Result.Success -> {
                    loadSources()
                }
                is Result.Error -> {
                    val message = result.exception.message ?: "Failed to unlink scraper"
                    updateState { it.copy(error = message) }
                    emitEffect(SourceManagementEffect.ShowSnackbar(message))
                }
                else -> {}
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
    val scraperMetadata: Map<String, ScraperMetadata> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

sealed interface SourceManagementEvent {
    data object LoadSources : SourceManagementEvent
    data class AddJsScraper(val githubUrl: String, val filename: String) : SourceManagementEvent
    data class ImportJsScraper(val filename: String, val scriptContent: String) : SourceManagementEvent
    data class AddHeuristicProfile(val baseUrl: String, val displayName: String?) : SourceManagementEvent
    data class LinkScraperToUrl(val baseUrl: String, val scraperFilename: String) : SourceManagementEvent
    data class CheckAndUpdateScraper(val baseUrl: String) : SourceManagementEvent
    data class ForceRediscover(val baseUrl: String) : SourceManagementEvent
    data class RenameScraper(val baseUrl: String, val newFilename: String) : SourceManagementEvent
    data class RemoveSource(val baseUrl: String) : SourceManagementEvent
    data class DisableSource(val baseUrl: String) : SourceManagementEvent
    data class UnlinkScraper(val baseUrl: String) : SourceManagementEvent
    data object ClearError : SourceManagementEvent
}

sealed interface SourceManagementEffect {
    data class ShowSnackbar(val message: String) : SourceManagementEffect
}
