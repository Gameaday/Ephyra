package ephyra.feature.category

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import ephyra.presentation.core.feature.FeatureApi
import javax.inject.Inject

class CategoryFeatureApi @Inject constructor() : FeatureApi {
    override fun register(
        navGraphBuilder: NavGraphBuilder,
        navController: NavHostController,
    ) {
        navGraphBuilder.composable<ephyra.presentation.core.ui.navigation.Screen.Category> {
            CategoryScreen(navController)
        }
    }
}
