package ephyra.feature.reader

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import ephyra.presentation.core.feature.FeatureApi
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import javax.inject.Inject

/**
 * Exposes the reader's Compose screens (such as BookReaderScreen) to the app's root navigation graph.
 */
class ReaderFeatureApi @Inject constructor() : FeatureApi {

    override fun register(
        navGraphBuilder: NavGraphBuilder,
        navController: NavHostController,
    ) {
        navGraphBuilder.composable(
            route = ScreenRoutes.BookReader.route,
            arguments = listOf(
                androidx.navigation.navArgument("title") {
                    type = androidx.navigation.NavType.StringType
                },
                androidx.navigation.navArgument("content") {
                    type = androidx.navigation.NavType.StringType
                },
            ),
        ) { backStackEntry ->
            val title = backStackEntry.arguments?.getString("title") ?: ""
            val content = backStackEntry.arguments?.getString("content") ?: ""
            val decodedTitle = java.net.URLDecoder.decode(title, "UTF-8")
            val decodedContent = java.net.URLDecoder.decode(content, "UTF-8")
            BookReaderScreen(
                title = decodedTitle,
                content = decodedContent,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
