package ephyra.feature.upcoming

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import ephyra.presentation.core.feature.FeatureApi
import ephyra.presentation.core.feature.SafeFeatureContainer
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import javax.inject.Inject

class UpcomingFeatureApi @Inject constructor() : FeatureApi {
    override fun register(
        navGraphBuilder: NavGraphBuilder,
        navController: NavHostController,
    ) {
        navGraphBuilder.composable(ScreenRoutes.Upcoming.route) {
            SafeFeatureContainer(
                featureName = "Upcoming",
                viewModelClass = UpcomingScreenModel::class.java,
                onBack = { navController.popBackStack() },
            ) { viewModel ->
                UpcomingScreen(viewModel, navController)
            }
        }
    }
}
