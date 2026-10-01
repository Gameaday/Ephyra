package ephyra.feature.browse.source.sourcing

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.util.lang.launchIO
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
class ContentSourcingViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val localScanner: LocalContentScanner,
    private val orchestrator: ContentSourceOrchestrator,
    private val profileCache: SourceProfileCache,
    private val preferenceStore: PreferenceStore,
) : BaseUdfViewModel<
    ContentSourcingViewModel.State,
    ContentSourcingViewModel.Event,
    ContentSourcingViewModel.Effect,
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

            updateState { state ->
                state.copy(
                    isLoading = false,
                    learnedProfiles = learnedDomains.toImmutableList(),
                    repositories = repoList.toImmutableList(),
                )
            }
        }
    }

    override fun onEvent(event: Event) {
        when (event) {
            is Event.SelectTab -> updateState { it.copy(selectedTab = event.index) }
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

            is Event.UpdateInspectUrl -> updateState { it.copy(inspectUrl = event.url, inspectError = null) }
            is Event.InspectSource -> inspectSource(event.url)
            Event.SaveInspectedProfile -> saveInspectedProfile()
            Event.ClearInspection -> updateState {
                it.copy(
                    inspectUrl = "",
                    inspectedProfile = null,
                    inspectError = null,
                    isInspecting = false,
                )
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

    private fun inspectSource(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank() || (!trimmed.startsWith("http://") && !trimmed.startsWith("https://"))) {
            updateState { it.copy(inspectError = "Please enter a valid URL starting with http:// or https://") }
            return
        }

        viewModelScope.launchIO {
            try {
                updateState { it.copy(isInspecting = true, inspectError = null, inspectedProfile = null) }
                when (val result = orchestrator.discover(trimmed)) {
                    is ephyra.core.common.util.Result.Success -> {
                        updateState {
                            it.copy(
                                isInspecting = false,
                                inspectedProfile = result.data,
                                inspectError = null,
                            )
                        }
                    }
                    is ephyra.core.common.util.Result.Error -> {
                        updateState {
                            it.copy(
                                isInspecting = false,
                                inspectedProfile = null,
                                inspectError = result.exception.message ?: "Failed to inspect source layout",
                            )
                        }
                    }
                    else -> {
                        updateState { it.copy(isInspecting = false) }
                    }
                }
            } catch (e: Exception) {
                updateState {
                    it.copy(
                        isInspecting = false,
                        inspectedProfile = null,
                        inspectError = e.message ?: "An unexpected error occurred during inspection",
                    )
                }
            }
        }
    }

    private fun saveInspectedProfile() {
        val profile = state.value.inspectedProfile ?: return
        viewModelScope.launchIO {
            try {
                profileCache.save(profile)
                emitEffect(Effect.ShowSnackbar("Source '${profile.displayName}' saved to library sources"))
                loadData()
                updateState {
                    it.copy(
                        inspectedProfile = null,
                        inspectUrl = "",
                        inspectError = null,
                    )
                }
            } catch (e: Exception) {
                emitEffect(Effect.ShowSnackbar("Failed to save profile: ${e.message}"))
            }
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

        data class UpdateInspectUrl(val url: String) : Event
        data class InspectSource(val url: String) : Event
        data object SaveInspectedProfile : Event
        data object ClearInspection : Event
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
        val repositories: ImmutableList<RepositoryItem> = persistentListOf(),
        val learnedProfiles: ImmutableList<SourceProfile> = persistentListOf(),
        val networkConnectionString: String = "",
        val networkPath: String = "",
        val scanResults: ImmutableList<ContentItem> = persistentListOf(),
        val dialog: Dialog? = null,
        val repoName: String = "",
        val repoPath: String = "",
        val showNetworkForm: Boolean = false,
        val inspectUrl: String = "",
        val isInspecting: Boolean = false,
        val inspectedProfile: SourceProfile? = null,
        val inspectError: String? = null,
    )
}

@Immutable
data class RepositoryItem(
    val name: String,
    val path: String,
    val isNetwork: Boolean,
)
