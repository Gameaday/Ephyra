package ephyra.feature.browse.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.Screen
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import ephyra.presentation.core.util.ifSourcesLoaded

@Composable
fun GlobalSearchScreen(
    searchQuery: String = "",
    extensionFilter: String? = null,
    navController: NavController = LocalNavController.current,
) {
    if (!ifSourcesLoaded()) {
        LoadingScreen()
        return
    }

    val viewModel = hiltViewModel<GlobalSearchViewModel>()
    LaunchedEffect(searchQuery, extensionFilter) {
        viewModel.init(searchQuery, extensionFilter)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSingleLoadingScreen by remember {
        mutableStateOf(searchQuery.isNotEmpty() && !extensionFilter.isNullOrEmpty() && state.total == 1)
    }

    if (showSingleLoadingScreen) {
        LoadingScreen()

        LaunchedEffect(state.items) {
            when (val result = state.items.values.singleOrNull()) {
                SearchItemResult.Loading -> return@LaunchedEffect
                is SearchItemResult.Success -> {
                    val manga = result.result.singleOrNull()
                    if (manga != null) {
                        navController.navigate(Screen.MangaDetails(manga.id, true)) {
                            popUpTo(navController.currentBackStackEntry?.destination?.id ?: -1) { inclusive = true }
                        }
                    } else {
                        showSingleLoadingScreen = false
                    }
                }
                else -> showSingleLoadingScreen = false
            }
        }
    } else {
        ephyra.feature.browse.presentation.GlobalSearchScreen(
            state = state,
            navigateUp = { navController.popBackStack() },
            onChangeSearchQuery = { viewModel.onEvent(SearchScreenEvent.UpdateSearchQuery(it)) },
            onSearch = { viewModel.onEvent(SearchScreenEvent.Search) },
            getManga = { viewModel.getManga(it) },
            onChangeSearchFilter = { viewModel.onEvent(SearchScreenEvent.SetSourceFilter(it)) },
            onToggleResults = { viewModel.onEvent(SearchScreenEvent.ToggleFilterResults) },
            onClickSource = {
                navController.navigate(Screen.BrowseSource(it.id, state.searchQuery))
            },
            onClickItem = { navController.navigate(Screen.MangaDetails(it.id, true)) },
            onLongClickItem = { navController.navigate(Screen.MangaDetails(it.id, true)) },
            suggestions = viewModel.suggestions.collectAsStateWithLifecycle().value,
            onSuggestionClick = { query ->
                viewModel.onEvent(SearchScreenEvent.UpdateSearchQuery(query))
                viewModel.onEvent(SearchScreenEvent.Search)
            },
            mergedDuplicateCount = state.mergedResults.count { it.sourceIds.size > 1 },
        )
    }
}
