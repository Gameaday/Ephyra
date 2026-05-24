package ephyra.feature.player

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import ephyra.presentation.core.feature.FeatureApi
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import javax.inject.Inject

/**
 * Exposes the video player Compose screen (VideoPlayerScreen) to the app's root navigation graph.
 */
class PlayerFeatureApi @Inject constructor() : FeatureApi {

    override fun register(
        navGraphBuilder: NavGraphBuilder,
        navController: NavHostController,
    ) {
        navGraphBuilder.composable(
            route = ScreenRoutes.VideoPlayer.route,
            arguments = listOf(
                androidx.navigation.navArgument("title") {
                    type = androidx.navigation.NavType.StringType
                },
                androidx.navigation.navArgument("url") {
                    type = androidx.navigation.NavType.StringType
                },
            ),
        ) { backStackEntry ->
            val title = backStackEntry.arguments?.getString("title") ?: ""
            val url = backStackEntry.arguments?.getString("url") ?: ""
            val decodedTitle = java.net.URLDecoder.decode(title, "UTF-8")
            val decodedUrl = java.net.URLDecoder.decode(url, "UTF-8")
            VideoPlayerScreen(
                title = decodedTitle,
                streamUrl = decodedUrl,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
