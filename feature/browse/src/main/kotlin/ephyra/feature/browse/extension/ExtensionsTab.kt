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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.feature.browse.presentation.ExtensionScreen
import ephyra.feature.browse.presentation.components.UniversalAddSourceDialog
import ephyra.presentation.core.components.AppBar
import ephyra.presentation.core.components.TabContent
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import kotlinx.collections.immutable.persistentListOf

@Composable
fun extensionsTab(
    extensionsViewModel: ExtensionsViewModel,
    navController: NavController = LocalNavController.current,
): TabContent {
    var showAddSourceDialog by remember { mutableStateOf(false) }

    return TabContent(
        titleRes = ephyra.app.core.common.R.string.label_source_management,
        badgeNumber = null,
        searchEnabled = true,
        actions = persistentListOf(
            AppBar.Action(
                title = "Add Source or Repo",
                icon = Icons.Outlined.AddLink,
                onClick = { showAddSourceDialog = true },
            ),
            AppBar.Action(
                title = "Manage Repositories",
                icon = Icons.Outlined.Storage,
                onClick = { navController.navigate(ScreenRoutes.ExtensionRepos.route) },
            ),
            AppBar.Action(
                title = "Refresh",
                icon = Icons.Outlined.Refresh,
                onClick = extensionsViewModel::refreshAll,
            ),
        ),
        content = { contentPadding, _ ->
            val state by extensionsViewModel.state.collectAsStateWithLifecycle()

            BackHandler(enabled = state.searchQuery != null) {
                extensionsViewModel.search(null)
            }

            ExtensionScreen(
                state = state,
                contentPadding = contentPadding,
                searchQuery = state.searchQuery,
                onAddJsScraper = extensionsViewModel::addJsScraper,
                onImportJsScraper = extensionsViewModel::importJsScraper,
                onAddHeuristic = extensionsViewModel::addHeuristicProfile,
                onLinkScraper = extensionsViewModel::linkScraperToUrl,
                onCheckUpdates = extensionsViewModel::checkAndUpdateScraper,
                onForceRediscover = extensionsViewModel::forceRediscover,
                onRemoveSource = extensionsViewModel::removeSource,
                onRefresh = extensionsViewModel::refreshAll,
                onAddRepository = extensionsViewModel::addRepository,
                onDeleteRepository = extensionsViewModel::deleteRepository,
                onInstallExtension = extensionsViewModel::installExtension,
                onUninstallExtension = extensionsViewModel::uninstallExtension,
                onTrustExtension = extensionsViewModel::trustExtension,
                onUninstallByPkgName = extensionsViewModel::uninstallFailedExtension,
                onUpdateExtension = extensionsViewModel::updateExtension,
                onUninstallInstalledExtension = extensionsViewModel::uninstallInstalledExtension,
                navController = navController,
            )

            if (showAddSourceDialog) {
                UniversalAddSourceDialog(
                    onDismissRequest = { showAddSourceDialog = false },
                    onAddRepo = { repoUrl ->
                        extensionsViewModel.addRepository(repoUrl)
                        showAddSourceDialog = false
                    },
                    onAddWebSource = { url, name ->
                        extensionsViewModel.addHeuristicProfile(url, name)
                        showAddSourceDialog = false
                    },
                )
            }
        },
    )
}
