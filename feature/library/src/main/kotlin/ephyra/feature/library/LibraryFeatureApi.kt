package ephyra.feature.library

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import ephyra.presentation.core.feature.FeatureApi
import javax.inject.Inject

class LibraryFeatureApi @Inject constructor() : FeatureApi {
    override fun register(
        navGraphBuilder: NavGraphBuilder,
        navController: NavHostController,
    ) {
        navGraphBuilder.composable<ephyra.presentation.core.ui.navigation.Screen.Library> {
            LibraryScreen(navController = navController)
        }
    }
}
