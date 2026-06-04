package ephyra.feature.browse

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.feature.browse.extension.ExtensionsScreenModel
import ephyra.feature.browse.extension.extensionsTab
import ephyra.feature.browse.migration.sources.migrateSourceTab
import ephyra.feature.browse.source.SourcesScreenModel
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

    // Hoisted for sources tab's search bar
    val sourcesScreenModel = hiltViewModel<SourcesScreenModel>()
    val sourcesState by sourcesScreenModel.state.collectAsStateWithLifecycle()

    val tabs = persistentListOf(
        discoverTab(navController),
        sourcesTab(sourcesScreenModel, navController),
        extensionsTab(extensionsScreenModel, navController),
        migrateSourceTab(navController),
    )

    val state = rememberPagerState { tabs.size }

    val currentQuery = when (state.currentPage) {
        1 -> sourcesState.searchQuery
        2 -> extensionsState.searchQuery
        else -> null
    }

    val onQueryChange: (String?) -> Unit = { query ->
        when (state.currentPage) {
            1 -> sourcesScreenModel.search(query)
            2 -> extensionsScreenModel.search(query)
        }
    }

    TabbedScreen(
        titleRes = ephyra.app.core.common.R.string.label_discover,
        tabs = tabs,
        state = state,
        searchQuery = currentQuery,
        onChangeSearchQuery = onQueryChange,
    )
    LaunchedEffect(Unit) {
        BrowseTab.switchToExtensionTabChannel.receiveAsFlow()
            .collectLatest { state.scrollToPage(2) }
    }

    LaunchedEffect(Unit) {
        (context as? AppReadySignal)?.signalReady()
    }
}

object BrowseTab {
    val switchToExtensionTabChannel = kotlinx.coroutines.channels.Channel<Unit>(capacity = 1)
}
