package ephyra.feature.browse.extension

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.feature.browse.presentation.ExtensionScreen
import ephyra.feature.browse.presentation.components.AddExtensionRepositoryDialog
import ephyra.presentation.core.components.AppBar
import ephyra.presentation.core.components.AppBarActions
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import kotlinx.collections.immutable.persistentListOf

/**
 * Extension/source management as a standalone destination under Settings.
 *
 * This content used to live as a pager page inside the Discover tab. Management is
 * settings-shaped (RFC-0001 D8): Discover keeps search + catalog browsing, and this
 * screen is reached from Settings or from the "updates available" chip on the Discover
 * search page. Keeping it a route also means the update chip can deep-link here
 * instead of the old `switchToExtensionTabChannel` page-scroll hack.
 */
@Composable
fun ExtensionsRouteScreen(
    navController: NavController = LocalNavController.current,
) {
    val viewModel = hiltViewModel<ExtensionsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAddSourceDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = state.searchQuery != null) {
        viewModel.search(null)
    }

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(ephyra.app.core.common.R.string.label_source_management),
                navigateUp = { navController.popBackStack() },
                actions = {
                    AppBarActions(
                        actions = persistentListOf(
                            AppBar.Action(
                                title = "Add Source or Repo",
                                icon = Icons.Outlined.AddLink,
                                onClick = { showAddSourceDialog = true },
                            ),
                            AppBar.Action(
                                title = "Manage Repositories",
                                icon = Icons.Outlined.Storage,
                                onClick = { navController.navigate(ScreenRoutes.ExtensionRepos.createRoute(null)) },
                            ),
                            AppBar.Action(
                                title = "Refresh",
                                icon = Icons.Outlined.Refresh,
                                onClick = viewModel::refreshAll,
                            ),
                        ),
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        ExtensionScreen(
            state = state,
            contentPadding = contentPadding,
            searchQuery = state.searchQuery,
            onForceRediscover = viewModel::forceRediscover,
            onRemoveSource = viewModel::removeSource,
            onRefresh = viewModel::refreshAll,
            onAddRepository = viewModel::addRepository,
            onDeleteRepository = viewModel::deleteRepository,
            onInstallExtension = viewModel::installExtension,
            onUninstallExtension = viewModel::uninstallExtension,
            onTrustExtension = viewModel::trustExtension,
            onUninstallByPkgName = viewModel::uninstallFailedExtension,
            onUpdateExtension = viewModel::updateExtension,
            onUninstallInstalledExtension = viewModel::uninstallInstalledExtension,
            navController = navController,
        )
        if (showAddSourceDialog) {
            AddExtensionRepositoryDialog(
                onDismissRequest = { showAddSourceDialog = false },
                onAddRepo = { repoUrl ->
                    viewModel.addRepository(repoUrl)
                    showAddSourceDialog = false
                },
            )
        }
    }
}
