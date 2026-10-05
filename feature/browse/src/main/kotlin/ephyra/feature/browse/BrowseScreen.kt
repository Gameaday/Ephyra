package ephyra.feature.browse

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.feature.browse.source.SourcesViewModel
import ephyra.feature.browse.source.authority.discoverTab
import ephyra.feature.browse.source.sourcesTab
import ephyra.presentation.core.components.TabbedScreen
import ephyra.presentation.core.ui.AppReadySignal
import ephyra.presentation.core.ui.navigation.LocalNavController
import kotlinx.collections.immutable.persistentListOf

/**
 * The Discover tab: search + source catalog browsing, nothing else.
 *
 * Extension management and source migration used to be pager pages here, which made the
 * tab a four-way pager whose search bar changed meaning per page and whose hoisted
 * `ExtensionsViewModel` did repository network work on every Discover visit. Both are
 * settings-shaped management surfaces (RFC-0001 D8/D9) and now live behind
 * [ephyra.presentation.core.ui.navigation.ScreenRoutes.Extensions] and
 * [ephyra.presentation.core.ui.navigation.ScreenRoutes.SourceMigration].
 */
@Composable
fun BrowseTabScreen(
    navController: NavController = LocalNavController.current,
) {
    val context = LocalContext.current

    // Hoisted for the sources page's search bar
    val sourcesViewModel = hiltViewModel<SourcesViewModel>()
    val sourcesState by sourcesViewModel.state.collectAsStateWithLifecycle()

    val tabs = persistentListOf(
        discoverTab(navController),
        sourcesTab(sourcesViewModel, navController),
    )

    val state = rememberPagerState { tabs.size }

    // One search bar, one meaning: on the sources page it filters sources; the search
    // page owns its own field internally, so the bar hides there (null query).
    val currentQuery = when (state.currentPage) {
        1 -> sourcesState.searchQuery
        else -> null
    }

    val onQueryChange: (String?) -> Unit = { query ->
        if (state.currentPage == 1) {
            sourcesViewModel.search(query)
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
        (context as? AppReadySignal)?.signalReady()
    }
}
