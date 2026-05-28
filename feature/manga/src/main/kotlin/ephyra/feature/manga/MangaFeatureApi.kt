package ephyra.feature.manga

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import ephyra.presentation.core.feature.FeatureApi
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import ephyra.presentation.core.ui.viewer.MediaViewerRegistry
import javax.inject.Inject

class MangaFeatureApi @Inject constructor(
    private val mediaViewerRegistry: MediaViewerRegistry,
) : FeatureApi {
    override fun register(
        navGraphBuilder: NavGraphBuilder,
        navController: NavHostController,
    ) {
        navGraphBuilder.composable<ephyra.presentation.core.ui.navigation.Screen.MangaDetails> { backStackEntry ->
            val route = backStackEntry.toRoute<ephyra.presentation.core.ui.navigation.Screen.MangaDetails>()
            MangaDetailsScreen(
                mangaId = route.mangaId,
                fromSource = route.fromSource,
                navController = navController,
                navigateUp = { navController.popBackStack() },
                mediaViewerRegistry = mediaViewerRegistry,
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
