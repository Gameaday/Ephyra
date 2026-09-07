package ephyra.feature.browse.extension

import android.app.Application
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
import ephyra.presentation.core.udf.BaseUdfViewModel
import ephyra.presentation.core.ui.AppInfo
import kotlinx.coroutines.flow.collectLatest
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
    private val trustExtension: ephyra.domain.extension.interactor.TrustExtension,
    private val extensionManager: ephyra.domain.extension.service.ExtensionManager,
    private val appInfo: AppInfo,
) : BaseUdfViewModel<ExtensionsViewModel.State, ExtensionsScreenEvent, ExtensionsEffect>(
    State(catalogShortcutsEnabled = appInfo.catalogShortcutsEnabled),
) {

    init {
        loadSources()
        loadRepositories()
        loadAvailableExtensions()
    }

    private fun loadRepositories() {
        viewModelScope.launch {
            getExtensionRepo.subscribeAll().collectLatest { repos ->
                updateState { it.copy(repos = repos) }
            }
        }
    }

    private fun loadAvailableExtensions() {
        viewModelScope.launch {
            getExtensionsByType.subscribe().collectLatest { extensions ->
                updateState {
                    it.copy(
                        availableExtensions = extensions.available,
                        installedExtensions = extensions.updates + extensions.installed,
                        untrustedExtensions = extensions.untrusted,
                        failedExtensions = extensions.failed,
                    )
                }
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
                        updateState { it.copy(error = "Legacy source ${ext.name} updated to v${ext.versionName}") }
                        loadSources()
                    }
                }
            }
        }
    }

    fun addRepository(url: String) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            when (createExtensionRepo.await(url)) {
                CreateExtensionRepo.Result.Success -> {
                    updateExtensionRepo.awaitAll()
                    updateState { it.copy(isLoading = false) }
                }
                else -> {
                    updateState { it.copy(isLoading = false, error = "Failed to add repository") }
                }
            }
        }
    }

    fun deleteRepository(url: String) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            deleteExtensionRepo.await(url)
            updateState { it.copy(isLoading = false) }
        }
    }

    fun installExtension(extension: Extension.Available, selectedUrls: Set<String>? = null) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            val success = legacyExtensionTranspiler.transpileAndInstall(extension, selectedUrls)
            updateState { it.copy(isLoading = false) }
            if (success) {
                loadSources()
            } else {
                updateState { it.copy(error = "Failed to transpile and install extension") }
            }
        }
    }

    fun uninstallExtension(extension: Extension.Available) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            val pkgSuffix = extension.pkgName.substringAfterLast(".")
            val filename = "${pkgSuffix}_scraper.js"

            scraperUpdater.removeScraper(filename)
            extension.sources.forEach { source ->
                removeCustomSource.removeSource(source.baseUrl)
            }

            // Clean up version tracking
            legacyExtensionTranspiler.clearExtensionMetadata(extension.pkgName)

            updateState { it.copy(isLoading = false) }
            loadSources()
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
                is Result.Success -> loadSources()
                is Result.Error -> updateState {
                    it.copy(error = result.exception.message ?: "Failed to add scraper")
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
                is Result.Success -> loadSources()
                is Result.Error -> updateState {
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
            updateState { it.copy(isLoading = true) }
            val result = addCustomSource.addHeuristicProfile(baseUrl, displayName)
            updateState { it.copy(isLoading = false) }
            when (result) {
                is Result.Success -> loadSources()
                is Result.Error -> updateState {
                    it.copy(error = result.exception.message ?: "Failed to add profile")
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
                is Result.Success -> loadSources()
                is Result.Error -> updateState {
                    it.copy(error = result.exception.message ?: "Failed to link scraper")
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
                is Result.Success -> loadSources()
                is Result.Error -> updateState {
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
            updateState { it.copy(isLoading = true) }
            val result = updateCustomSource.forceRediscover(baseUrl)
            updateState { it.copy(isLoading = false) }
            when (result) {
                is Result.Success -> loadSources()
                is Result.Error -> updateState { it.copy(error = result.exception.message ?: "Failed to rediscover") }
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
                is Result.Success -> loadSources()
                is Result.Error -> updateState {
                    it.copy(error = result.exception.message ?: "Failed to remove source")
                }
                else -> {}
            }
        }
    }

    fun search(query: String?) {
        updateState { it.copy(searchQuery = query) }
    }

    /** Marks an untrusted extension as trusted for its current version+signature. */
    fun trustExtension(extension: Extension.Untrusted) {
        viewModelScope.launch {
            trustExtension.trust(extension.pkgName, extension.versionCode, extension.signatureHash)
        }
    }

    /** Uninstalls a broken/failed extension APK. */
    fun uninstallFailedExtension(pkgName: String) {
        extensionManager.uninstallExtensionByPkgName(pkgName)
    }

    fun clearError() {
        updateState { it.copy(error = null) }
    }

    override fun onEvent(event: ExtensionsScreenEvent) {
        when (event) {
            is ExtensionsScreenEvent.AddRepository -> addRepository(event.url)
            is ExtensionsScreenEvent.DeleteRepository -> deleteRepository(event.url)
            is ExtensionsScreenEvent.InstallExtension -> installExtension(event.extension, event.selectedUrls)
            is ExtensionsScreenEvent.UninstallExtension -> uninstallExtension(event.extension)
            ExtensionsScreenEvent.LoadSources -> loadSources()
            is ExtensionsScreenEvent.AddJsScraper -> addJsScraper(event.githubUrl, event.filename)
            is ExtensionsScreenEvent.ImportJsScraper -> importJsScraper(event.filename, event.scriptContent)
            is ExtensionsScreenEvent.AddHeuristicProfile -> addHeuristicProfile(event.baseUrl, event.displayName)
            is ExtensionsScreenEvent.LinkScraperToUrl -> linkScraperToUrl(event.baseUrl, event.scraperFilename)
            is ExtensionsScreenEvent.CheckAndUpdateScraper -> checkAndUpdateScraper(event.baseUrl)
            is ExtensionsScreenEvent.ForceRediscover -> forceRediscover(event.baseUrl)
            is ExtensionsScreenEvent.RemoveSource -> removeSource(event.baseUrl)
            is ExtensionsScreenEvent.Search -> search(event.query)
            is ExtensionsScreenEvent.TrustExtension -> trustExtension(event.extension)
            is ExtensionsScreenEvent.UninstallFailedExtension -> uninstallFailedExtension(event.pkgName)
            ExtensionsScreenEvent.ClearError -> clearError()
        }
    }

    data class State(
        val isLoading: Boolean = false,
        val sources: List<UnifiedSource> = emptyList(),
        val searchQuery: String? = null,
        val error: String? = null,
        val repos: List<ExtensionRepo> = emptyList(),
        val availableExtensions: List<Extension.Available> = emptyList(),
        val installedExtensions: List<Extension.Installed> = emptyList(),
        val untrustedExtensions: List<Extension.Untrusted> = emptyList(),
        val failedExtensions: List<Extension.Failed> = emptyList(),
        val catalogShortcutsEnabled: Boolean = false,
    )
}

sealed interface ExtensionsScreenEvent {
    data class AddRepository(val url: String) : ExtensionsScreenEvent
    data class DeleteRepository(val url: String) : ExtensionsScreenEvent
    data class InstallExtension(
        val extension: Extension.Available,
        val selectedUrls: Set<String>? = null,
    ) : ExtensionsScreenEvent
    data class UninstallExtension(val extension: Extension.Available) : ExtensionsScreenEvent
    data object LoadSources : ExtensionsScreenEvent
    data class AddJsScraper(val githubUrl: String, val filename: String) : ExtensionsScreenEvent
    data class ImportJsScraper(val filename: String, val scriptContent: String) : ExtensionsScreenEvent
    data class AddHeuristicProfile(val baseUrl: String, val displayName: String?) : ExtensionsScreenEvent
    data class LinkScraperToUrl(val baseUrl: String, val scraperFilename: String) : ExtensionsScreenEvent
    data class CheckAndUpdateScraper(val baseUrl: String) : ExtensionsScreenEvent
    data class ForceRediscover(val baseUrl: String) : ExtensionsScreenEvent
    data class RemoveSource(val baseUrl: String) : ExtensionsScreenEvent
    data class Search(val query: String?) : ExtensionsScreenEvent
    data class TrustExtension(val extension: Extension.Untrusted) : ExtensionsScreenEvent
    data class UninstallFailedExtension(val pkgName: String) : ExtensionsScreenEvent
    data object ClearError : ExtensionsScreenEvent
}

sealed interface ExtensionsEffect {
    data class ShowSnackbar(val message: String) : ExtensionsEffect
}
