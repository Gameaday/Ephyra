package ephyra.feature.reader

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import ephyra.presentation.core.feature.FeatureApi
import ephyra.presentation.core.ui.navigation.Screen
import javax.inject.Inject

/**
 * Exposes the reader's Compose screens (such as BookReaderScreen) to the app's root navigation graph.
 */
class ReaderFeatureApi @Inject constructor() : FeatureApi {

    override fun register(
        navGraphBuilder: NavGraphBuilder,
        navController: NavHostController,
    ) {
        navGraphBuilder.composable<Screen.BookReader> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.BookReader>()
            BookReaderScreen(
                title = route.title,
                content = route.content,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
