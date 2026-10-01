package ephyra.feature.browse.extension

import android.app.Application
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.preference.getAndSet
import ephyra.core.common.util.Result
import ephyra.core.common.util.lang.launchIO
import ephyra.core.common.util.system.logcat
import ephyra.domain.content.source.SourceType
import ephyra.domain.content.source.interactor.GetAvailableSources
import ephyra.domain.content.source.interactor.RemoveCustomSource
import ephyra.domain.content.source.interactor.UnifiedSource
import ephyra.domain.content.source.interactor.UpdateCustomSource
import ephyra.domain.extension.interactor.GetExtensionsByType
import ephyra.domain.extension.model.Extension
import ephyra.domain.extension.model.InstallStep
import ephyra.domain.extensionrepo.interactor.CreateExtensionRepo
import ephyra.domain.extensionrepo.interactor.DeleteExtensionRepo
import ephyra.domain.extensionrepo.interactor.GetExtensionRepo
import ephyra.domain.extensionrepo.interactor.UpdateExtensionRepo
import ephyra.domain.extensionrepo.model.ExtensionRepo
import ephyra.domain.source.service.SourcePreferences
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
    private val updateCustomSource: UpdateCustomSource,
    private val removeCustomSource: RemoveCustomSource,
    private val getExtensionRepo: GetExtensionRepo,
    private val createExtensionRepo: CreateExtensionRepo,
    private val deleteExtensionRepo: DeleteExtensionRepo,
    private val updateExtensionRepo: UpdateExtensionRepo,
    private val getExtensionsByType: GetExtensionsByType,
    private val preferenceStore: PreferenceStore,
    private val sourcePreferences: SourcePreferences,
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
        viewModelScope.launch {
            try {
                extensionManager.findAvailableExtensions()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to find available extensions on init" }
            }
        }
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
            }
        }
    }

    fun addRepository(url: String) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            when (val result = createExtensionRepo.await(url)) {
                CreateExtensionRepo.Result.Success -> {
                    updateExtensionRepo.awaitAll()
                    extensionManager.findAvailableExtensions()
                    extensionManager.reloadExtensions()
                    updateState { it.copy(isLoading = false) }
                }
                CreateExtensionRepo.Result.RepoAlreadyExists -> {
                    updateState { it.copy(isLoading = false, error = "Repository already exists") }
                }
                is CreateExtensionRepo.Result.DuplicateFingerprint -> {
                    updateState {
                        it.copy(
                            isLoading = false,
                            error = "Repository with matching signing key already exists: ${result.oldRepo.name}",
                        )
                    }
                }
                CreateExtensionRepo.Result.InvalidUrl -> {
                    updateState { it.copy(isLoading = false, error = "Invalid repository URL") }
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
            extensionManager.findAvailableExtensions()
            extensionManager.reloadExtensions()
            updateState { it.copy(isLoading = false) }
        }
    }

    fun installExtension(extension: Extension.Available, selectedUrls: Set<String>? = null) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            extensionManager.installExtension(extension).collect { step ->
                when (step) {
                    InstallStep.Pending, InstallStep.Downloading, InstallStep.Installing -> {
                        updateState { it.copy(isLoading = true) }
                    }
                    InstallStep.Installed -> {
                        updateState { it.copy(isLoading = false) }
                        extensionManager.reloadExtensions()
                        loadSources()
                    }
                    InstallStep.Error -> {
                        updateState { it.copy(isLoading = false, error = "Failed to install ${extension.name}") }
                    }
                    InstallStep.Idle -> {
                        updateState { it.copy(isLoading = false) }
                    }
                }
            }
        }
    }

    /**
     * Removes every source this extension backs.
     *
     * The scraper that used to be regenerated alongside the APK is gone, so there is no generated
     * artefact left to delete here — an uninstall now only has to retire the sources it owns and
     * the sandboxed APK itself.
     */
    fun uninstallExtension(extension: Extension.Available) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            extension.sources.forEach { source ->
                removeCustomSource.removeSource(source.baseUrl)
            }

            // Ensure APK is uninstalled from sandboxed private storage
            extensionManager.uninstallExtensionByPkgName(extension.pkgName)
            extensionManager.reloadExtensions()

            updateState { it.copy(isLoading = false) }
            loadSources()
        }
    }

    fun uninstallInstalledExtension(extension: Extension.Installed) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            extensionManager.uninstallExtension(extension)
            extensionManager.reloadExtensions()
            updateState { it.copy(isLoading = false) }
            loadSources()
        }
    }

    fun updateExtension(extension: Extension.Installed) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            extensionManager.updateExtension(extension).collect { step ->
                when (step) {
                    InstallStep.Pending, InstallStep.Downloading, InstallStep.Installing -> {
                        updateState { it.copy(isLoading = true) }
                    }
                    InstallStep.Installed -> {
                        updateState { it.copy(isLoading = false) }
                        extensionManager.reloadExtensions()
                        loadSources()
                    }
                    InstallStep.Error -> {
                        updateState { it.copy(isLoading = false, error = "Failed to update ${extension.name}") }
                    }
                    InstallStep.Idle -> {
                        updateState { it.copy(isLoading = false) }
                    }
                }
            }
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
            val matchingSource = state.value.sources.firstOrNull { it.baseUrl == baseUrl }
            if (matchingSource?.sourceType == SourceType.REMOTE_EXTENSION) {
                matchingSource.extensionId?.let { pkgName ->
                    extensionManager.uninstallExtensionByPkgName(pkgName)
                }
                sourcePreferences.disabledSources().getAndSet { it + matchingSource.id.toString() }
                updateState { it.copy(isLoading = false) }
                loadSources()
            } else {
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
    }

    fun search(query: String?) {
        updateState { it.copy(searchQuery = query) }
    }

    /** Marks an untrusted extension as trusted for its current version+signature. */
    fun trustExtension(extension: Extension.Untrusted) {
        viewModelScope.launch {
            extensionManager.trust(extension)
            loadSources()
        }
    }

    /** Uninstalls a broken/failed extension APK. */
    fun uninstallFailedExtension(pkgName: String) {
        extensionManager.uninstallExtensionByPkgName(pkgName)
    }

    fun refreshAll() {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            try {
                updateExtensionRepo.awaitAll()
                extensionManager.findAvailableExtensions()
                extensionManager.reloadExtensions()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to refresh extension repositories" }
            }
            loadSources()
            updateState { it.copy(isLoading = false) }
        }
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
            ExtensionsScreenEvent.RefreshAll -> refreshAll()
            is ExtensionsScreenEvent.ForceRediscover -> forceRediscover(event.baseUrl)
            is ExtensionsScreenEvent.RemoveSource -> removeSource(event.baseUrl)
            is ExtensionsScreenEvent.Search -> search(event.query)
            is ExtensionsScreenEvent.TrustExtension -> trustExtension(event.extension)
            is ExtensionsScreenEvent.UninstallFailedExtension -> {
                viewModelScope.launch {
                    extensionManager.uninstallExtensionByPkgName(event.pkgName)
                    extensionManager.reloadExtensions()
                    loadSources()
                }
            }
            is ExtensionsScreenEvent.UpdateExtension -> updateExtension(event.extension)
            is ExtensionsScreenEvent.UninstallInstalledExtension -> uninstallInstalledExtension(event.extension)
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
    data object RefreshAll : ExtensionsScreenEvent
    data class ForceRediscover(val baseUrl: String) : ExtensionsScreenEvent
    data class RemoveSource(val baseUrl: String) : ExtensionsScreenEvent
    data class Search(val query: String?) : ExtensionsScreenEvent
    data class TrustExtension(val extension: Extension.Untrusted) : ExtensionsScreenEvent
    data class UninstallFailedExtension(val pkgName: String) : ExtensionsScreenEvent
    data class UpdateExtension(val extension: Extension.Installed) : ExtensionsScreenEvent
    data class UninstallInstalledExtension(val extension: Extension.Installed) : ExtensionsScreenEvent
    data object ClearError : ExtensionsScreenEvent
}

sealed interface ExtensionsEffect {
    data class ShowSnackbar(val message: String) : ExtensionsEffect
}
