package ephyra.feature.settings.screen.browse

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.util.lang.launchIO
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.extensionrepo.interactor.CreateExtensionRepo
import ephyra.domain.extensionrepo.interactor.DeleteExtensionRepo
import ephyra.domain.extensionrepo.interactor.GetExtensionRepo
import ephyra.domain.extensionrepo.interactor.ReplaceExtensionRepo
import ephyra.domain.extensionrepo.interactor.UpdateExtensionRepo
import ephyra.domain.extensionrepo.model.ExtensionRepo
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class ExtensionReposScreenModel @Inject constructor(
    private val getExtensionRepo: GetExtensionRepo,
    private val createExtensionRepo: CreateExtensionRepo,
    private val deleteExtensionRepo: DeleteExtensionRepo,
    private val replaceExtensionRepo: ReplaceExtensionRepo,
    private val updateExtensionRepo: UpdateExtensionRepo,
    private val extensionManager: ExtensionManager,
) : ViewModel() {

    private val _state = MutableStateFlow<RepoScreenState>(RepoScreenState.Loading)
    val state: StateFlow<RepoScreenState> = _state.asStateFlow()

    private val _events: Channel<RepoEvent> = Channel(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launchIO {
            getExtensionRepo.subscribeAll()
                .collectLatest { repos ->
                    _state.update {
                        RepoScreenState.Success(
                            repos = repos.toImmutableSet(),
                        )
                    }
                }
        }
    }

    fun onEvent(event: ExtensionReposScreenEvent) {
        when (event) {
            is ExtensionReposScreenEvent.CreateRepo -> createRepo(event.baseUrl)
            is ExtensionReposScreenEvent.ReplaceRepo -> replaceRepo(event.newRepo)
            ExtensionReposScreenEvent.RefreshRepos -> refreshRepos()
            is ExtensionReposScreenEvent.DeleteRepo -> deleteRepo(event.baseUrl)
            is ExtensionReposScreenEvent.ShowDialog -> showDialog(event.dialog)
            ExtensionReposScreenEvent.DismissDialog -> dismissDialog()
        }
    }

    /**
     * Creates and adds a new repo to the database.
     *
     * @param baseUrl The baseUrl of the repo to create.
     */
    private fun createRepo(baseUrl: String) {
        viewModelScope.launchIO {
            when (val result = createExtensionRepo.await(baseUrl)) {
                CreateExtensionRepo.Result.Success -> extensionManager.findAvailableExtensions()
                CreateExtensionRepo.Result.InvalidUrl -> _events.send(RepoEvent.InvalidUrl)
                CreateExtensionRepo.Result.RepoAlreadyExists -> _events.send(RepoEvent.RepoAlreadyExists)
                is CreateExtensionRepo.Result.DuplicateFingerprint -> {
                    showDialog(RepoDialog.Conflict(result.oldRepo, result.newRepo))
                }

                else -> {}
            }
        }
    }

    /**
     * Inserts a repo to the database, replace a matching repo with the same signing key fingerprint if found.
     *
     * @param newRepo The repo to insert
     */
    private fun replaceRepo(newRepo: ExtensionRepo) {
        viewModelScope.launchIO {
            replaceExtensionRepo.await(newRepo)
        }
    }

    /**
     * Refreshes information for each repository.
     */
    private fun refreshRepos() {
        val status = state.value

        if (status is RepoScreenState.Success) {
            viewModelScope.launchIO {
                updateExtensionRepo.awaitAll()
            }
        }
    }

    /**
     * Deletes the given repo from the database
     */
    private fun deleteRepo(baseUrl: String) {
        viewModelScope.launchIO {
            deleteExtensionRepo.await(baseUrl)
            extensionManager.findAvailableExtensions()
        }
    }

    private fun showDialog(dialog: RepoDialog) {
        _state.update {
            when (it) {
                RepoScreenState.Loading -> it
                is RepoScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    private fun dismissDialog() {
        _state.update {
            when (it) {
                RepoScreenState.Loading -> it
                is RepoScreenState.Success -> it.copy(dialog = null)
            }
        }
    }
}

sealed class RepoEvent {
    sealed class LocalizedMessage(val stringRes: Int) : RepoEvent()
    data object InvalidUrl : LocalizedMessage(ephyra.app.core.common.R.string.invalid_repo_name)
    data object RepoAlreadyExists : LocalizedMessage(ephyra.app.core.common.R.string.error_repo_exists)
}

sealed class RepoDialog {
    data object Create : RepoDialog()
    data class Delete(val repo: String) : RepoDialog()
    data class Conflict(val oldRepo: ExtensionRepo, val newRepo: ExtensionRepo) : RepoDialog()
    data class Confirm(val url: String) : RepoDialog()
}

sealed class RepoScreenState {

    @Immutable
    data object Loading : RepoScreenState()

    @Immutable
    data class Success(
        val repos: ImmutableSet<ExtensionRepo>,
        val oldRepos: ImmutableSet<String>? = null,
        val dialog: RepoDialog? = null,
    ) : RepoScreenState() {

        val isEmpty: Boolean
            get() = repos.isEmpty()
    }
}
