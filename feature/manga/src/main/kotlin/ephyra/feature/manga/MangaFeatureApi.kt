package ephyra.feature.manga

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import ephyra.presentation.core.feature.FeatureApi
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import javax.inject.Inject

class MangaFeatureApi @Inject constructor() : FeatureApi {
    override fun register(
        navGraphBuilder: NavGraphBuilder,
        navController: NavHostController,
    ) {
        navGraphBuilder.composable(
            route = ScreenRoutes.MangaDetails.route,
            arguments = listOf(
                androidx.navigation.navArgument("mangaId") {
                    type = androidx.navigation.NavType.LongType
                },
                androidx.navigation.navArgument("fromSource") {
                    type = androidx.navigation.NavType.BoolType
                },
            ),
        ) { backStackEntry ->
            val mangaId = backStackEntry.arguments?.getLong("mangaId") ?: return@composable
            val fromSource = backStackEntry.arguments?.getBoolean("fromSource") ?: false
            MangaDetailsScreen(
                mangaId = mangaId,
                fromSource = fromSource,
                navController = navController,
                navigateUp = { navController.popBackStack() },
            )
        }

        navGraphBuilder.composable(
            route = ScreenRoutes.MangaNotes.route,
            arguments = listOf(
                androidx.navigation.navArgument("mangaId") {
                    type = androidx.navigation.NavType.LongType
                },
            ),
        ) { backStackEntry ->
            val mangaId = backStackEntry.arguments?.getLong("mangaId") ?: return@composable
            ephyra.feature.manga.notes.MangaNotesScreen(mangaId, navController)
        }
    }
}
