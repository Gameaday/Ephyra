package ephyra.feature.browse.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.feature.browse.presentation.GlobalSearchContent
import ephyra.presentation.core.components.TabContent
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.Screen

/**
 * The Discover tab's search page: the app's single search front door (D1/D13).
 *
 * This hosts the same fan-out engine as the old standalone GlobalSearch destination
 * inside the tab chrome, so there is exactly one place to search. Results render
 * unmerged per source (v1: no dedup — duplicates cluster via the content's existing
 * ordering and read as availability, not noise). The field lives in the shared
 * [ephyra.presentation.core.components.TabbedScreen] toolbar, hoisted by
 * `BrowseTabScreen`, which also routes the submit that fires the fan-out.
 */
@Composable
fun unifiedSearchTab(
    viewModel: GlobalSearchViewModel,
    navController: NavController = LocalNavController.current,
): TabContent {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()

    return TabContent(
        titleRes = ephyra.app.core.common.R.string.label_search,
        searchEnabled = true,
        content = { contentPadding, _ ->
            GlobalSearchContent(
                items = state.filteredItems,
                contentPadding = contentPadding,
                getManga = { viewModel.getManga(it) },
                onClickSource = {
                    navController.navigate(Screen.BrowseSource(it.id, state.searchQuery))
                },
                onClickItem = { navController.navigate(Screen.MangaDetails(it.id, true)) },
                onLongClickItem = { navController.navigate(Screen.MangaDetails(it.id, true)) },
                suggestions = suggestions,
                onSuggestionClick = { query ->
                    viewModel.onEvent(SearchScreenEvent.UpdateSearchQuery(query))
                    viewModel.onEvent(SearchScreenEvent.Search)
                },
            )
        },
    )
}
