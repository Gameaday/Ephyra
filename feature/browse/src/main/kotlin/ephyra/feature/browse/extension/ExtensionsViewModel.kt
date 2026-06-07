package ephyra.feature.browse.extension

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.util.Result
import ephyra.core.common.util.lang.launchIO
import ephyra.core.common.util.system.logcat
import ephyra.domain.content.source.ScraperScriptUpdater
import ephyra.domain.content.source.SourceType
import ephyra.domain.content.source.interactor.AddCustomSource
import ephyra.domain.content.source.interactor.GetAvailableSources
import ephyra.domain.content.source.interactor.RemoveCustomSource
import ephyra.domain.content.source.interactor.UnifiedSource
import ephyra.domain.content.source.interactor.UpdateCustomSource
import ephyra.domain.extension.interactor.GetExtensionsByType
import ephyra.domain.extension.model.Extension
import ephyra.domain.extension.service.ExtensionTranspiler
import ephyra.domain.extensionrepo.interactor.CreateExtensionRepo
import ephyra.domain.extensionrepo.interactor.DeleteExtensionRepo
import ephyra.domain.extensionrepo.interactor.GetExtensionRepo
import ephyra.domain.extensionrepo.interactor.UpdateExtensionRepo
import ephyra.domain.extensionrepo.model.ExtensionRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import javax.inject.Inject

@HiltViewModel
class ExtensionsViewModel @Inject constructor(
    private val context: Application,
    private val getAvailableSources: GetAvailableSources,
    private val addCustomSource: AddCustomSource,
    private val updateCustomSource: UpdateCustomSource,
    private val removeCustomSource: RemoveCustomSource,
    private val getExtensionRepo: GetExtensionRepo,
    private val createExtensionRepo: CreateExtensionRepo,
    private val deleteExtensionRepo: DeleteExtensionRepo,
    private val updateExtensionRepo: UpdateExtensionRepo,
    private val getExtensionsByType: GetExtensionsByType,
    private val legacyExtensionTranspiler: ExtensionTranspiler,
    private val scraperUpdater: ScraperScriptUpdater,
    private val preferenceStore: PreferenceStore,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        loadSources()
        loadRepositories()
        loadAvailableExtensions()
    }

    private fun loadRepositories() {
        viewModelScope.launch {
            getExtensionRepo.subscribeAll().collectLatest { repos ->
                _state.update { it.copy(repos = repos) }
            }
        }
    }

    private fun loadAvailableExtensions() {
        viewModelScope.launch {
            getExtensionsByType.subscribe().collectLatest { extensions ->
                _state.update { it.copy(availableExtensions = extensions.available) }
                checkForTranspiledExtensionUpdates(extensions.available)
            }
        }
    }

    private fun checkForTranspiledExtensionUpdates(available: List<Extension.Available>) {
        viewModelScope.launch {
            available.forEach { ext ->
                val installedVersionCode = preferenceStore.getLong(
                    "transpiled_extension_versioncode_${ext.pkgName}",
                    0L,
                ).get()
                if (installedVersionCode > 0L && ext.versionCode > installedVersionCode) {
                    val pkgSuffix = ext.pkgName.substringAfterLast(".")
                    val filename = "${pkgSuffix}_scraper.js"

                    // Find currently mapped custom source URLs by checking PreferenceStore mapping directly
                    val previouslySelectedUrls = ext.sources.filter { source ->
                        val normalized = source.baseUrl
                            .removePrefix("https://")
                            .removePrefix("http://")
                            .removeSuffix("/")
                            .trim()
                        val mappingKey = "baseUrl_scraper_mapping_$normalized"
                        preferenceStore.getString(mappingKey, "").get() == filename
                    }.map { it.baseUrl }.toSet()

                    // Automatically update/re-transpile in the background!
                    logcat(LogPriority.INFO) { "Updating legacy extension ${ext.name} to v${ext.versionName}" }
                    val success = legacyExtensionTranspiler.transpileAndInstall(ext, previouslySelectedUrls)
                    if (success) {
                        _state.update { it.copy(error = "Legacy source ${ext.name} updated to v${ext.versionName}") }
                        loadSources()
                    }
                }
            }
        }
    }

    fun addRepository(url: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            when (createExtensionRepo.await(url)) {
                CreateExtensionRepo.Result.Success -> {
                    updateExtensionRepo.awaitAll()
                    _state.update { it.copy(isLoading = false) }
                }
                else -> {
                    _state.update { it.copy(isLoading = false, error = "Failed to add repository") }
                }
            }
        }
    }

    fun deleteRepository(url: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            deleteExtensionRepo.await(url)
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun installExtension(extension: Extension.Available, selectedUrls: Set<String>? = null) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val success = legacyExtensionTranspiler.transpileAndInstall(extension, selectedUrls)
            _state.update { it.copy(isLoading = false) }
            if (success) {
                loadSources()
            } else {
                _state.update { it.copy(error = "Failed to transpile and install extension") }
            }
        }
    }

    fun uninstallExtension(extension: Extension.Available) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val pkgSuffix = extension.pkgName.substringAfterLast(".")
            val filename = "${pkgSuffix}_scraper.js"

            scraperUpdater.removeScraper(filename)
            extension.sources.forEach { source ->
                removeCustomSource.removeSource(source.baseUrl)
            }

            // Clean up version tracking
            legacyExtensionTranspiler.clearExtensionMetadata(extension.pkgName)

            _state.update { it.copy(isLoading = false) }
            loadSources()
        }
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
        val repos: List<ExtensionRepo> = emptyList(),
        val availableExtensions: List<Extension.Available> = emptyList(),
    )
}
