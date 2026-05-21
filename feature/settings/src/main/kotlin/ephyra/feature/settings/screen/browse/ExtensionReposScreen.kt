package ephyra.feature.settings.screen.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import ephyra.feature.settings.screen.browse.components.ExtensionRepoConfirmDialog
import ephyra.feature.settings.screen.browse.components.ExtensionRepoConflictDialog
import ephyra.feature.settings.screen.browse.components.ExtensionRepoCreateDialog
import ephyra.feature.settings.screen.browse.components.ExtensionRepoDeleteDialog
import ephyra.feature.settings.screen.browse.components.ExtensionReposScreen
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.core.util.system.openInBrowser
import ephyra.presentation.core.util.system.toast
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.collectLatest

import androidx.navigation.NavController
import ephyra.presentation.core.ui.navigation.LocalNavController

@Composable
fun ExtensionReposScreen(
    url: String? = null,
    navController: NavController = LocalNavController.current,
) {
    val context = LocalContext.current

    val screenModel = hiltViewModel<ExtensionReposScreenModel>()
    val state by screenModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(url) {
        url?.let { screenModel.onEvent(ExtensionReposScreenEvent.ShowDialog(RepoDialog.Confirm(it))) }
    }

    if (state is RepoScreenState.Loading) {
        LoadingScreen()
        return
    }

    val successState = state as RepoScreenState.Success

    ExtensionReposScreen(
        state = successState,
        onClickCreate = { screenModel.onEvent(ExtensionReposScreenEvent.ShowDialog(RepoDialog.Create)) },
        onOpenWebsite = { context.openInBrowser(it.website) },
        onClickDelete = { screenModel.onEvent(ExtensionReposScreenEvent.ShowDialog(RepoDialog.Delete(it))) },
        onClickRefresh = { screenModel.onEvent(ExtensionReposScreenEvent.RefreshRepos) },
        navigateUp = { navController.popBackStack() },
    )

        when (val dialog = successState.dialog) {
            null -> {}
            is RepoDialog.Create -> {
                ExtensionRepoCreateDialog(
                    onDismissRequest = { screenModel.onEvent(ExtensionReposScreenEvent.DismissDialog) },
                    onCreate = { screenModel.onEvent(ExtensionReposScreenEvent.CreateRepo(it)) },
                    repoUrls = successState.repos.map { it.baseUrl }.toImmutableSet(),
                )
            }

            is RepoDialog.Delete -> {
                ExtensionRepoDeleteDialog(
                    onDismissRequest = { screenModel.onEvent(ExtensionReposScreenEvent.DismissDialog) },
                    onDelete = { screenModel.onEvent(ExtensionReposScreenEvent.DeleteRepo(dialog.repo)) },
                    repo = dialog.repo,
                )
            }

            is RepoDialog.Conflict -> {
                ExtensionRepoConflictDialog(
                    onDismissRequest = { screenModel.onEvent(ExtensionReposScreenEvent.DismissDialog) },
                    onMigrate = { screenModel.onEvent(ExtensionReposScreenEvent.ReplaceRepo(dialog.newRepo)) },
                    oldRepo = dialog.oldRepo,
                    newRepo = dialog.newRepo,
                )
            }

            is RepoDialog.Confirm -> {
                ExtensionRepoConfirmDialog(
                    onDismissRequest = { screenModel.onEvent(ExtensionReposScreenEvent.DismissDialog) },
                    onCreate = { screenModel.onEvent(ExtensionReposScreenEvent.CreateRepo(dialog.url)) },
                    repo = dialog.url,
                )
            }
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                if (event is RepoEvent.LocalizedMessage) {
                    context.toast(event.stringRes)
                }
            }
        }
    }
}
