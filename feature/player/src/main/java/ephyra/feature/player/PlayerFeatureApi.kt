package ephyra.feature.player

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import ephyra.presentation.core.feature.FeatureApi
import ephyra.presentation.core.ui.navigation.Screen
import javax.inject.Inject

/**
 * Exposes the video player Compose screen (VideoPlayerScreen) to the app's root navigation graph.
 */
class PlayerFeatureApi @Inject constructor() : FeatureApi {

    override fun register(
        navGraphBuilder: NavGraphBuilder,
        navController: NavHostController,
    ) {
        navGraphBuilder.composable<Screen.VideoPlayer> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.VideoPlayer>()
            VideoPlayerScreen(
                title = route.title,
                streamUrl = route.url,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
