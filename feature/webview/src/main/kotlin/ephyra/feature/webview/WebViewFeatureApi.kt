package ephyra.feature.webview

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import ephyra.presentation.core.feature.FeatureApi
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import javax.inject.Inject

class WebViewFeatureApi @Inject constructor() : FeatureApi {
    override fun register(
        navGraphBuilder: NavGraphBuilder,
        navController: NavHostController,
    ) {
        navGraphBuilder.composable(
            route = ScreenRoutes.WebView.route,
            arguments = listOf(
                androidx.navigation.navArgument("url") {
                    type = androidx.navigation.NavType.StringType
                },
                androidx.navigation.navArgument("title") { nullable = true },
                androidx.navigation.navArgument("sourceId") { nullable = true },
            ),
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url") ?: return@composable
            val title = backStackEntry.arguments?.getString("title")
            val sourceId = backStackEntry.arguments?.getString("sourceId")?.toLongOrNull()
            WebViewScreen(url, title, sourceId, navController)
        }
    }
}
