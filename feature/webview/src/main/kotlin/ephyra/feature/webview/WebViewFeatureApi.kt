package ephyra.feature.webview

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import ephyra.presentation.core.feature.FeatureApi
import ephyra.presentation.core.ui.navigation.Screen
import javax.inject.Inject

class WebViewFeatureApi @Inject constructor() : FeatureApi {
    override fun register(
        navGraphBuilder: NavGraphBuilder,
        navController: NavHostController,
    ) {
        navGraphBuilder.composable<Screen.WebView> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.WebView>()
            WebViewScreen(route.url, route.title, route.sourceId, navController)
        }
    }
}
