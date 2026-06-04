package ephyra.feature.settings.screen.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.feature.settings.screen.browse.components.ExtensionRepoConfirmDialog
import ephyra.feature.settings.screen.browse.components.ExtensionRepoConflictDialog
import ephyra.feature.settings.screen.browse.components.ExtensionRepoCreateDialog
import ephyra.feature.settings.screen.browse.components.ExtensionRepoDeleteDialog
import ephyra.feature.settings.screen.browse.components.ExtensionReposScreen
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.util.system.openInBrowser
import ephyra.presentation.core.util.system.toast
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ExtensionReposScreen(
    url: String? = null,
    navController: NavController = LocalNavController.current,
) {
    val context = LocalContext.current

    val viewModel = hiltViewModel<ExtensionReposViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(url) {
        url?.let { viewModel.onEvent(ExtensionReposScreenEvent.ShowDialog(RepoDialog.Confirm(it))) }
    }

    if (state is RepoScreenState.Loading) {
        LoadingScreen()
        return
    }

    val successState = state as RepoScreenState.Success

    ExtensionReposScreen(
        state = successState,
        onClickCreate = { viewModel.onEvent(ExtensionReposScreenEvent.ShowDialog(RepoDialog.Create)) },
        onOpenWebsite = { context.openInBrowser(it.website) },
        onClickDelete = { viewModel.onEvent(ExtensionReposScreenEvent.ShowDialog(RepoDialog.Delete(it))) },
        onClickRefresh = { viewModel.onEvent(ExtensionReposScreenEvent.RefreshRepos) },
        navigateUp = { navController.popBackStack() },
    )

    when (val dialog = successState.dialog) {
        null -> {}
        is RepoDialog.Create -> {
            ExtensionRepoCreateDialog(
                onDismissRequest = { viewModel.onEvent(ExtensionReposScreenEvent.DismissDialog) },
                onCreate = { viewModel.onEvent(ExtensionReposScreenEvent.CreateRepo(it)) },
                repoUrls = successState.repos.map { it.baseUrl }.toImmutableSet(),
                isAdding = successState.isAdding,
            )
        }

        is RepoDialog.Delete -> {
            ExtensionRepoDeleteDialog(
                onDismissRequest = { ViewModel.onEvent(ExtensionReposScreenEvent.DismissDialog) },
                onDelete = { ViewModel.onEvent(ExtensionReposScreenEvent.DeleteRepo(dialog.repo)) },
                repo = dialog.repo,
            )
        }

        is RepoDialog.Conflict -> {
            ExtensionRepoConflictDialog(
                onDismissRequest = { ViewModel.onEvent(ExtensionReposScreenEvent.DismissDialog) },
                onMigrate = { ViewModel.onEvent(ExtensionReposScreenEvent.ReplaceRepo(dialog.newRepo)) },
                oldRepo = dialog.oldRepo,
                newRepo = dialog.newRepo,
            )
        }

        is RepoDialog.Confirm -> {
            ExtensionRepoConfirmDialog(
                onDismissRequest = { ViewModel.onEvent(ExtensionReposScreenEvent.DismissDialog) },
                onCreate = { ViewModel.onEvent(ExtensionReposScreenEvent.CreateRepo(dialog.url)) },
                repo = dialog.url,
                isAdding = successState.isAdding,
            )
        }
    }

    LaunchedEffect(Unit) {
        ViewModel.events.collectLatest { event ->
            if (event is RepoEvent.LocalizedMessage) {
                context.toast(event.stringRes)
            }
        }
    }
}
