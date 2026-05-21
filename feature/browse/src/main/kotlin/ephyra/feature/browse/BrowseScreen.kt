package ephyra.feature.browse

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.feature.browse.extension.ExtensionsScreenModel
import ephyra.feature.browse.extension.extensionsTab
import ephyra.feature.browse.migration.sources.migrateSourceTab
import ephyra.feature.browse.source.authority.discoverTab
import ephyra.feature.browse.source.sourcesTab
import ephyra.presentation.core.components.TabbedScreen
import ephyra.presentation.core.ui.AppReadySignal
import ephyra.presentation.core.ui.navigation.LocalNavController
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow

@Composable
fun BrowseTabScreen(
    navController: NavController = LocalNavController.current,
) {
    val context = LocalContext.current

    // Hoisted for extensions tab's search bar
    val extensionsScreenModel = hiltViewModel<ExtensionsScreenModel>()
    val extensionsState by extensionsScreenModel.state.collectAsStateWithLifecycle()

    val tabs = persistentListOf(
        discoverTab(navController),
        sourcesTab(navController),
        extensionsTab(extensionsScreenModel, navController),
        migrateSourceTab(navController),
    )

    val state = rememberPagerState { tabs.size }

    TabbedScreen(
        titleRes = ephyra.app.core.common.R.string.label_discover,
        tabs = tabs,
        state = state,
        searchQuery = extensionsState.searchQuery,
        onChangeSearchQuery = extensionsScreenModel::search,
    )
    LaunchedEffect(Unit) {
        BrowseTab.switchToExtensionTabChannel.receiveAsFlow()
            .collectLatest { state.scrollToPage(2) }
    }

    LaunchedEffect(Unit) {
        (context as? AppReadySignal)?.signalReady()
    }
}
