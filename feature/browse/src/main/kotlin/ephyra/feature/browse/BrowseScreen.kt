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
import ephyra.feature.browse.source.globalsearch.GlobalSearchViewModel
import ephyra.feature.browse.source.globalsearch.SearchScreenEvent
import ephyra.feature.browse.source.globalsearch.unifiedSearchTab
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

    // Hoisted for the search page's toolbar field
    val searchViewModel = hiltViewModel<GlobalSearchViewModel>()
    val searchState by searchViewModel.state.collectAsStateWithLifecycle()

    // Hoisted for the sources page's search bar
    val sourcesViewModel = hiltViewModel<SourcesViewModel>()
    val sourcesState by sourcesViewModel.state.collectAsStateWithLifecycle()

    val tabs = persistentListOf(
        unifiedSearchTab(searchViewModel, navController),
        sourcesTab(sourcesViewModel, navController),
    )

    val state = rememberPagerState { tabs.size }

    // One toolbar field, one owner per page: page 0 is the unified search (network
    // fan-out on submit only — D5), page 1 filters the sources list locally.
    val currentQuery = when (state.currentPage) {
        0 -> searchState.searchQuery
        1 -> sourcesState.searchQuery
        else -> null
    }

    val onQueryChange: (String?) -> Unit = { query ->
        when (state.currentPage) {
            0 -> searchViewModel.onEvent(SearchScreenEvent.UpdateSearchQuery(query))
            1 -> sourcesViewModel.search(query)
        }
    }

    val onSearch: (String) -> Unit = {
        if (state.currentPage == 0) {
            searchViewModel.onEvent(SearchScreenEvent.Search)
        }
    }

    TabbedScreen(
        titleRes = ephyra.app.core.common.R.string.label_discover,
        tabs = tabs,
        state = state,
        searchQuery = currentQuery,
        onChangeSearchQuery = onQueryChange,
        onSearch = onSearch,
    )

    LaunchedEffect(Unit) {
        (context as? AppReadySignal)?.signalReady()
    }
}
