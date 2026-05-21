package ephyra.feature.browse.migration.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import ephyra.feature.browse.presentation.MigrateSearchScreen
import ephyra.feature.browse.source.globalsearch.SearchScreenModel
import ephyra.feature.browse.source.globalsearch.SearchScreenEvent
import ephyra.feature.migration.dialog.MigrateMangaDialog
import androidx.navigation.NavController
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.ScreenRoutes

@Composable
fun MigrateSearchScreen(
    mangaId: Long,
    navController: NavController = LocalNavController.current,
) {
    val screenModel = hiltViewModel<MigrateSearchScreenModel>()
    LaunchedEffect(mangaId) {
        screenModel.init(mangaId)
    }
    val state by screenModel.state.collectAsStateWithLifecycle()

    MigrateSearchScreen(
        state = state,
        fromSourceId = state.from?.source,
        navigateUp = { navController.popBackStack() },
        onChangeSearchQuery = { screenModel.onEvent(SearchScreenEvent.UpdateSearchQuery(it)) },
        onSearch = { screenModel.onEvent(SearchScreenEvent.Search) },
        getManga = { screenModel.getManga(it) },
        onChangeSearchFilter = { screenModel.onEvent(SearchScreenEvent.SetSourceFilter(it)) },
        onToggleResults = { screenModel.onEvent(SearchScreenEvent.ToggleFilterResults) },
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
                screenModel.onEvent(SearchScreenEvent.SetMigrateDialog(mangaId, it))
            }
        },
        onLongClickItem = {
            navController.navigate(ScreenRoutes.MangaDetails.createRoute(it.id, true))
        },
    )

    when (val dialog = state.dialog) {
        is SearchScreenModel.Dialog.Migrate -> {
            MigrateMangaDialog(
                current = dialog.current,
                target = dialog.target,
                onClickTitle = {
                    navController.navigate(ScreenRoutes.MangaDetails.createRoute(dialog.target.id, true))
                },
                onDismissRequest = { screenModel.onEvent(SearchScreenEvent.ClearDialog) },
                onComplete = {
                    navController.navigate(ScreenRoutes.MangaDetails.createRoute(dialog.target.id, true)) {
                        popUpTo(ScreenRoutes.MigrateSearch.route) { inclusive = true }
                    }
                },
            )
        }
        else -> {}
    }
}
