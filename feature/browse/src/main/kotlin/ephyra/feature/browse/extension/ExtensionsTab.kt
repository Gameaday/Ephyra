package ephyra.feature.browse.extension

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.feature.browse.presentation.ExtensionScreen
import ephyra.presentation.core.components.TabContent
import ephyra.presentation.core.ui.navigation.LocalNavController
import kotlinx.collections.immutable.persistentListOf

@Composable
fun extensionsTab(
    extensionsViewModel: ExtensionsViewModel,
    navController: NavController = LocalNavController.current,
): TabContent {
    return TabContent(
        titleRes = ephyra.app.core.common.R.string.label_source_management,
        badgeNumber = null,
        searchEnabled = true,
        actions = persistentListOf(),
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
                onRefresh = extensionsViewModel::loadSources,
                onAddRepository = extensionsViewModel::addRepository,
                onDeleteRepository = extensionsViewModel::deleteRepository,
                onInstallExtension = extensionsViewModel::installExtension,
                onUninstallExtension = extensionsViewModel::uninstallExtension,
                navController = navController,
            )
        },
    )
}
