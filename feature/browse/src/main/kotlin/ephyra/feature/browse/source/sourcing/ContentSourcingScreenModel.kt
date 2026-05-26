package ephyra.feature.browse.source.sourcing

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.util.lang.launchIO
import ephyra.data.sourcing.DynamicScraperUpdater
import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.service.LocalContentScanner
import ephyra.domain.content.source.ContentSourceOrchestrator
import ephyra.domain.content.source.SourceProfile
import ephyra.domain.content.source.SourceProfileCache
import ephyra.presentation.core.udf.BaseUdfViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContentSourcingScreenModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scraperUpdater: DynamicScraperUpdater,
    private val localScanner: LocalContentScanner,
    private val orchestrator: ContentSourceOrchestrator,
    private val profileCache: SourceProfileCache,
    private val preferenceStore: PreferenceStore,
) : BaseUdfViewModel<
    ContentSourcingScreenModel.State,
    ContentSourcingScreenModel.Event,
    ContentSourcingScreenModel.Effect,
    >(State()) {

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launchIO {
            // Load learned domains dynamically
            val learnedDomains = profileCache.getAllProfiledDomains().mapNotNull { url ->
                profileCache.get(url)
            }

            // Load registered scrapers from sandbox dynamically
            val scraperFiles = scraperUpdater.listScrapers()
            val scraperList = scraperFiles.map { name ->
                val hasUpdateUrl = preferenceStore.getString("scraper_url_$name", "").get().isNotBlank()
                ScraperItem(
                    name = name,
                    hasUpdatesUrl = hasUpdateUrl,
                    localContent = scraperUpdater.getScraperScript(name),
                )
            }

            // Load repositories
            val reposString = preferenceStore.getString("custom_repositories", "").get()
            val repoList = if (reposString.isBlank()) {
                emptyList()
            } else {
                reposString.split("||").mapNotNull {
                    val parts = it.split("::")
                    if (parts.size >= 2) {
                        RepositoryItem(
                            parts[0],
                            parts[1],
                            parts[1].startsWith("smb://") || parts[1].startsWith("nfs://"),
                        )
                    } else {
                        null
                    }
                }
            }

            // Load scraper mappings dynamically
            val mappingList = profileCache.getAllProfiledDomains().mapNotNull { domain ->
                val normalized = domain.removePrefix("https://").removePrefix("http://").removeSuffix("/").trim()
                val scraper = preferenceStore.getString("baseUrl_scraper_mapping_$normalized", "").get()
                if (scraper.isNotBlank()) {
                    ScraperMappingItem(baseUrl = domain, scraperName = scraper)
                } else {
                    null
                }
            }

            updateState { state ->
                state.copy(
                    isLoading = false,
                    learnedProfiles = learnedDomains.toImmutableList(),
                    scrapers = scraperList.toImmutableList(),
                    repositories = repoList.toImmutableList(),
                    scraperMappings = mappingList.toImmutableList(),
                )
            }
        }
    }

    override fun onEvent(event: Event) {
        when (event) {
            is Event.SelectTab -> updateState { it.copy(selectedTab = event.index) }
            is Event.UpdateGithubUrl -> updateState { it.copy(githubUrl = event.url) }
            is Event.UpdateScraperName -> updateState { it.copy(scraperName = event.name) }
            is Event.DownloadScraper -> downloadScraper(event.url, event.name)
            is Event.ImportLocalScraper -> importLocalScraper(event.name, event.content)
            is Event.CheckScraperUpdate -> checkScraperUpdate(event.name)
            is Event.UpdateNetworkConnection -> updateState { it.copy(networkConnectionString = event.conn) }
            is Event.UpdateNetworkPath -> updateState { it.copy(networkPath = event.path) }
            is Event.AddRepository -> addRepository(event.name, event.path)
            is Event.RemoveRepository -> removeRepository(event.name)
            is Event.ScanRepository -> scanRepository(event.item)
            is Event.ForceRediscover -> forceRediscover(event.baseUrl)
            is Event.DeleteProfile -> deleteProfile(event.baseUrl)
            Event.DismissDialog -> updateState { it.copy(dialog = null, scanResults = persistentListOf()) }

            is Event.UpdateRepoName -> updateState { it.copy(repoName = event.name) }
            is Event.UpdateRepoPath -> updateState { it.copy(repoPath = event.path) }
            is Event.UpdateShowNetworkForm -> updateState { it.copy(showNetworkForm = event.show) }
            is Event.UpdateImportScriptName -> updateState { it.copy(importScriptName = event.name) }
            is Event.UpdateImportScriptContent -> updateState { it.copy(importScriptContent = event.content) }
            is Event.ShowImportDialog -> updateState { it.copy(showImportDialog = event.show) }

            is Event.UpdateMapBaseUrl -> updateState { it.copy(mapBaseUrl = event.url) }
            is Event.UpdateMapScraperName -> updateState { it.copy(mapScraperName = event.name) }
            is Event.LinkBaseUrlToScraper -> linkBaseUrlToScraper(event.baseUrl, event.scraperName)
            is Event.RemoveScraperMapping -> removeScraperMapping(event.baseUrl)
        }
    }

    private fun downloadScraper(url: String, name: String) {
        viewModelScope.launchIO {
            try {
                updateState { it.copy(isLoading = true) }
                scraperUpdater.downloadScraper(url, name)
                emitEffect(Effect.ShowSnackbar("Scraper $name downloaded successfully"))
                loadData()
            } catch (e: Exception) {
                emitEffect(Effect.ShowSnackbar("Download failed: ${e.message}"))
            } finally {
                updateState { it.copy(isLoading = false, githubUrl = "", scraperName = "") }
            }
        }
    }

    private fun importLocalScraper(name: String, content: String) {
        viewModelScope.launchIO {
            try {
                updateState { it.copy(isLoading = true) }
                scraperUpdater.importLocalScraperScript(name, content)
                emitEffect(Effect.ShowSnackbar("Local script $name imported to sandbox"))
                loadData()
            } catch (e: Exception) {
                emitEffect(Effect.ShowSnackbar("Import failed: ${e.message}"))
            } finally {
                updateState {
                    it.copy(
                        isLoading = false,
                        importScriptName = "",
                        importScriptContent = "",
                        showImportDialog = false,
                    )
                }
            }
        }
    }

    private fun checkScraperUpdate(name: String) {
        viewModelScope.launchIO {
            try {
                updateState { it.copy(isLoading = true) }
                val updated = scraperUpdater.checkForUpdates(name)
                if (updated) {
                    emitEffect(Effect.ShowSnackbar("Scraper $name updated successfully"))
                } else {
                    emitEffect(Effect.ShowSnackbar("No updates found for $name"))
                }
                loadData()
            } catch (e: Exception) {
                emitEffect(Effect.ShowSnackbar("Update failed: ${e.message}"))
            } finally {
                updateState { it.copy(isLoading = false) }
            }
        }
    }

    private fun addRepository(name: String, path: String) {
        viewModelScope.launchIO {
            val isNetwork = path.startsWith("smb://") || path.startsWith("nfs://")
            if (isNetwork && !localScanner.testNetworkConnection(path)) {
                emitEffect(Effect.ShowSnackbar("Invalid network connection string format"))
                return@launchIO
            }

            val currentReposString = preferenceStore.getString("custom_repositories", "").get()
            val newRepoSegment = "$name::$path"
            val updatedString = if (currentReposString.isBlank()) {
                newRepoSegment
            } else {
                "$currentReposString||$newRepoSegment"
            }
            preferenceStore.getString("custom_repositories", "").set(updatedString)
            emitEffect(Effect.ShowSnackbar("Repository $name added"))
            loadData()
            updateState {
                it.copy(
                    networkConnectionString = "",
                    networkPath = "",
                    repoName = "",
                    repoPath = "",
                )
            }
        }
    }

    private fun removeRepository(name: String) {
        viewModelScope.launchIO {
            val currentReposString = preferenceStore.getString("custom_repositories", "").get()
            if (currentReposString.isBlank()) return@launchIO

            val updatedList = currentReposString.split("||").filterNot { it.startsWith("$name::") }
            preferenceStore.getString("custom_repositories", "").set(updatedList.joinToString("||"))
            emitEffect(Effect.ShowSnackbar("Repository $name removed"))
            loadData()
        }
    }

    private fun scanRepository(item: RepositoryItem) {
        viewModelScope.launchIO {
            try {
                updateState { it.copy(isLoading = true) }
                val scanResults = if (item.isNetwork) {
                    localScanner.scanNetworkDirectory(item.path, "Media/Scanner")
                } else {
                    val mockFile = com.hippo.unifile.UniFile.fromUri(context, android.net.Uri.parse(item.path))
                    if (mockFile != null) {
                        localScanner.scanDirectory(mockFile)
                    } else {
                        emptyList()
                    }
                }

                updateState { state ->
                    state.copy(
                        scanResults = scanResults.toImmutableList(),
                        dialog = Dialog.ScanComplete(item.name),
                    )
                }
            } catch (e: Exception) {
                emitEffect(Effect.ShowSnackbar("Scanning failed: ${e.message}"))
            } finally {
                updateState { it.copy(isLoading = false) }
            }
        }
    }

    private fun forceRediscover(baseUrl: String) {
        viewModelScope.launchIO {
            try {
                updateState { it.copy(isLoading = true) }
                orchestrator.rediscover(baseUrl)
                emitEffect(Effect.ShowSnackbar("Forced re-discovery completed for $baseUrl"))
                loadData()
            } catch (e: Exception) {
                emitEffect(Effect.ShowSnackbar("Discovery failed: ${e.message}"))
            } finally {
                updateState { it.copy(isLoading = false) }
            }
        }
    }

    private fun deleteProfile(baseUrl: String) {
        viewModelScope.launchIO {
            profileCache.invalidate(baseUrl)
            emitEffect(Effect.ShowSnackbar("Cleared learned profile for $baseUrl"))
            loadData()
        }
    }

    private fun linkBaseUrlToScraper(baseUrl: String, scraperName: String) {
        viewModelScope.launchIO {
            val normalized = normalizeUrl(baseUrl)
            preferenceStore.getString("baseUrl_scraper_mapping_$normalized", "").set(scraperName)
            emitEffect(Effect.ShowSnackbar("Mapped $baseUrl to $scraperName"))
            loadData()
            updateState { it.copy(mapBaseUrl = "", mapScraperName = "") }
        }
    }

    private fun removeScraperMapping(baseUrl: String) {
        viewModelScope.launchIO {
            val normalized = normalizeUrl(baseUrl)
            preferenceStore.getString("baseUrl_scraper_mapping_$normalized", "").delete()
            emitEffect(Effect.ShowSnackbar("Removed scraper mapping for $baseUrl"))
            loadData()
        }
    }

    private fun normalizeUrl(url: String): String {
        return url
            .removePrefix("https://")
            .removePrefix("http://")
            .removeSuffix("/")
            .trim()
    }

    sealed interface Event {
        data class SelectTab(val index: Int) : Event
        data class UpdateGithubUrl(val url: String) : Event
        data class UpdateScraperName(val name: String) : Event
        data class DownloadScraper(val url: String, val name: String) : Event
        data class ImportLocalScraper(val name: String, val content: String) : Event
        data class CheckScraperUpdate(val name: String) : Event

        data class UpdateNetworkConnection(val conn: String) : Event
        data class UpdateNetworkPath(val path: String) : Event
        data class AddRepository(val name: String, val path: String) : Event
        data class RemoveRepository(val name: String) : Event
        data class ScanRepository(val item: RepositoryItem) : Event

        data class ForceRediscover(val baseUrl: String) : Event
        data class DeleteProfile(val baseUrl: String) : Event
        data object DismissDialog : Event

        data class UpdateRepoName(val name: String) : Event
        data class UpdateRepoPath(val path: String) : Event
        data class UpdateShowNetworkForm(val show: Boolean) : Event
        data class UpdateImportScriptName(val name: String) : Event
        data class UpdateImportScriptContent(val content: String) : Event
        data class ShowImportDialog(val show: Boolean) : Event

        data class UpdateMapBaseUrl(val url: String) : Event
        data class UpdateMapScraperName(val name: String) : Event
        data class LinkBaseUrlToScraper(val baseUrl: String, val scraperName: String) : Event
        data class RemoveScraperMapping(val baseUrl: String) : Event
    }

    sealed interface Effect {
        data class ShowSnackbar(val message: String) : Effect
    }

    sealed interface Dialog {
        data class ScanComplete(val repoName: String) : Dialog
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val selectedTab: Int = 0,
        val scrapers: ImmutableList<ScraperItem> = persistentListOf(),
        val repositories: ImmutableList<RepositoryItem> = persistentListOf(),
        val learnedProfiles: ImmutableList<SourceProfile> = persistentListOf(),
        val githubUrl: String = "",
        val scraperName: String = "",
        val networkConnectionString: String = "",
        val networkPath: String = "",
        val scanResults: ImmutableList<ContentItem> = persistentListOf(),
        val dialog: Dialog? = null,
        val repoName: String = "",
        val repoPath: String = "",
        val showNetworkForm: Boolean = false,
        val importScriptName: String = "",
        val importScriptContent: String = "",
        val showImportDialog: Boolean = false,
        val mapBaseUrl: String = "",
        val mapScraperName: String = "",
        val scraperMappings: ImmutableList<ScraperMappingItem> = persistentListOf(),
    )
}

@Immutable
data class ScraperItem(
    val name: String,
    val hasUpdatesUrl: Boolean,
    val localContent: String?,
)

@Immutable
data class RepositoryItem(
    val name: String,
    val path: String,
    val isNetwork: Boolean,
)

@Immutable
data class ScraperMappingItem(
    val baseUrl: String,
    val scraperName: String,
)
