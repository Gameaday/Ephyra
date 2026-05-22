package ephyra.presentation.core.feature

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController

/**
 * Interface that feature submodules implement to register their routes
 * dynamically with the main application's NavHost, decoupling compile-time dependencies.
 */
interface FeatureApi {
    fun register(
        navGraphBuilder: NavGraphBuilder,
        navController: NavHostController,
    )
}
