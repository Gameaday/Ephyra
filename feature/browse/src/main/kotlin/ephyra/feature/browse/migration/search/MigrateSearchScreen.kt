package ephyra.feature.browse.migration.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.feature.browse.presentation.MigrateSearchScreen
import ephyra.feature.browse.source.globalsearch.SearchScreenEvent
import ephyra.feature.browse.source.globalsearch.SearchViewModel
import ephyra.feature.migration.dialog.MigrateMangaDialog
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.Screen
import ephyra.presentation.core.ui.navigation.ScreenRoutes

@Composable
fun MigrateSearchScreen(
    mangaId: Long,
    navController: NavController = LocalNavController.current,
) {
    val ViewModel = hiltViewModel<MigrateSearchViewModel>()
    LaunchedEffect(mangaId) {
        ViewModel.init(mangaId)
    }
    val state by ViewModel.state.collectAsStateWithLifecycle()

    MigrateSearchScreen(
        state = state,
        fromSourceId = state.from?.source,
        navigateUp = { navController.popBackStack() },
        onChangeSearchQuery = { ViewModel.onEvent(SearchScreenEvent.UpdateSearchQuery(it)) },
        onSearch = { ViewModel.onEvent(SearchScreenEvent.Search) },
        getManga = { ViewModel.getManga(it) },
        onChangeSearchFilter = { ViewModel.onEvent(SearchScreenEvent.SetSourceFilter(it)) },
        onToggleResults = { ViewModel.onEvent(SearchScreenEvent.ToggleFilterResults) },
        onClickSource = {
            navController.navigate(
                ScreenRoutes.MigrateSourceSearch.createRoute(mangaId, it.id),
            )
        },
        onClickItem = {
            val migrateListEntry = navController.previousBackStackEntry
            if (migrateListEntry?.destination?.route?.startsWith("migration_list") == true) {
                migrateListEntry.savedStateHandle["match_override"] = mangaId to it.id
                navController.popBackStack()
            } else {
                ViewModel.onEvent(SearchScreenEvent.SetMigrateDialog(mangaId, it))
            }
        },
        onLongClickItem = {
            navController.navigate(Screen.MangaDetails(it.id, true))
        },
    )

    when (val dialog = state.dialog) {
        is SearchViewModel.Dialog.Migrate -> {
            MigrateMangaDialog(
                current = dialog.current,
                target = dialog.target,
                onClickTitle = {
                    navController.navigate(Screen.MangaDetails(dialog.target.id, true))
                },
                onDismissRequest = { ViewModel.onEvent(SearchScreenEvent.ClearDialog) },
                onComplete = {
                    navController.navigate(Screen.MangaDetails(dialog.target.id, true)) {
                        popUpTo(ScreenRoutes.MigrateSearch.route) { inclusive = true }
                    }
                },
            )
        }
        else -> {}
    }
}
