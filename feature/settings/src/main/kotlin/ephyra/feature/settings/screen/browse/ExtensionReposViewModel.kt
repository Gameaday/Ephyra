package ephyra.feature.settings.screen.browse

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.extensionrepo.interactor.CreateExtensionRepo
import ephyra.domain.extensionrepo.interactor.DeleteExtensionRepo
import ephyra.domain.extensionrepo.interactor.GetExtensionRepo
import ephyra.domain.extensionrepo.interactor.ReplaceExtensionRepo
import ephyra.domain.extensionrepo.interactor.UpdateExtensionRepo
import ephyra.domain.extensionrepo.model.ExtensionRepo
import ephyra.presentation.core.udf.BaseUdfViewModel
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExtensionReposViewModel @Inject constructor(
    private val getExtensionRepo: GetExtensionRepo,
    private val createExtensionRepo: CreateExtensionRepo,
    private val deleteExtensionRepo: DeleteExtensionRepo,
    private val replaceExtensionRepo: ReplaceExtensionRepo,
    private val updateExtensionRepo: UpdateExtensionRepo,
    private val extensionManager: ExtensionManager,
) : BaseUdfViewModel<RepoScreenState, ExtensionReposScreenEvent, RepoEvent>(RepoScreenState.Loading) {

    val events: Flow<RepoEvent>
        get() = effects

    init {
        viewModelScope.launch {
            getExtensionRepo.subscribeAll()
                .collectLatest { repos ->
                    updateState { oldState ->
                        val currentSuccess = oldState as? RepoScreenState.Success
                        RepoScreenState.Success(
                            repos = repos.toImmutableSet(),
                            oldRepos = currentSuccess?.oldRepos,
                            dialog = currentSuccess?.dialog,
                            isAdding = currentSuccess?.isAdding ?: false,
                        )
                    }
                }
        }
    }

    override fun onEvent(event: ExtensionReposScreenEvent) {
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
        updateState {
            when (it) {
                RepoScreenState.Loading -> it
                is RepoScreenState.Success -> it.copy(isAdding = true)
            }
        }
        viewModelScope.launch {
            try {
                when (val result = createExtensionRepo.await(baseUrl)) {
                    CreateExtensionRepo.Result.Success -> {
                        extensionManager.findAvailableExtensions()
                        updateState { oldState ->
                            when (oldState) {
                                RepoScreenState.Loading -> oldState
                                is RepoScreenState.Success -> oldState.copy(isAdding = false, dialog = null)
                            }
                        }
                    }
                    CreateExtensionRepo.Result.InvalidUrl -> {
                        emitEffect(RepoEvent.InvalidUrl)
                        updateState { oldState ->
                            when (oldState) {
                                RepoScreenState.Loading -> oldState
                                is RepoScreenState.Success -> oldState.copy(isAdding = false)
                            }
                        }
                    }
                    CreateExtensionRepo.Result.RepoAlreadyExists -> {
                        emitEffect(RepoEvent.RepoAlreadyExists)
                        updateState { oldState ->
                            when (oldState) {
                                RepoScreenState.Loading -> oldState
                                is RepoScreenState.Success -> oldState.copy(isAdding = false)
                            }
                        }
                    }
                    is CreateExtensionRepo.Result.DuplicateFingerprint -> {
                        updateState { oldState ->
                            when (oldState) {
                                RepoScreenState.Loading -> oldState
                                is RepoScreenState.Success -> oldState.copy(
                                    isAdding = false,
                                    dialog = RepoDialog.Conflict(result.oldRepo, result.newRepo),
                                )
                            }
                        }
                    }
                    else -> {
                        updateState { oldState ->
                            when (oldState) {
                                RepoScreenState.Loading -> oldState
                                is RepoScreenState.Success -> oldState.copy(isAdding = false)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                updateState { oldState ->
                    when (oldState) {
                        RepoScreenState.Loading -> oldState
                        is RepoScreenState.Success -> oldState.copy(isAdding = false)
                    }
                }
            }
        }
    }

    /**
     * Inserts a repo to the database, replace a matching repo with the same signing key fingerprint if found.
     *
     * @param newRepo The repo to insert
     */
    private fun replaceRepo(newRepo: ExtensionRepo) {
        viewModelScope.launch {
            replaceExtensionRepo.await(newRepo)
        }
    }

    /**
     * Refreshes information for each repository.
     */
    private fun refreshRepos() {
        val status = currentState

        if (status is RepoScreenState.Success) {
            viewModelScope.launch {
                updateExtensionRepo.awaitAll()
            }
        }
    }

    /**
     * Deletes the given repo from the database
     */
    private fun deleteRepo(baseUrl: String) {
        viewModelScope.launch {
            deleteExtensionRepo.await(baseUrl)
            extensionManager.findAvailableExtensions()
        }
    }

    private fun showDialog(dialog: RepoDialog) {
        updateState {
            when (it) {
                RepoScreenState.Loading -> it
                is RepoScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    private fun dismissDialog() {
        updateState {
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
        val isAdding: Boolean = false,
    ) : RepoScreenState() {

        val isEmpty: Boolean
            get() = repos.isEmpty()
    }
}
