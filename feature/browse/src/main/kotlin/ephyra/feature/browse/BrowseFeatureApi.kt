package ephyra.feature.browse

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import ephyra.presentation.core.feature.FeatureApi
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import javax.inject.Inject

class BrowseFeatureApi @Inject constructor() : FeatureApi {
    override fun register(
        navGraphBuilder: NavGraphBuilder,
        navController: NavHostController,
    ) {
        // Browse main screen route
        navGraphBuilder.composable(ScreenRoutes.Browse.route) {
            BrowseTabScreen(navController = navController)
        }

        // Migrate Search Screen
        navGraphBuilder.composable(ScreenRoutes.MigrateSearch.route) { backStackEntry ->
            val mangaId = backStackEntry.arguments?.getString("mangaId")?.toLongOrNull() ?: return@composable
            ephyra.feature.browse.migration.search.MigrateSearchScreen(mangaId, navController)
        }

        // Migrate Source Search Screen
        navGraphBuilder.composable(
            route = ScreenRoutes.MigrateSourceSearch.route,
            arguments = listOf(
                androidx.navigation.navArgument("mangaId") {
                    type = androidx.navigation.NavType.LongType
                },
                androidx.navigation.navArgument("sourceId") {
                    type = androidx.navigation.NavType.LongType
                },
            ),
        ) { backStackEntry ->
            val mangaId = backStackEntry.arguments?.getLong("mangaId") ?: return@composable
            val sourceId = backStackEntry.arguments?.getLong("sourceId") ?: return@composable
            val query = backStackEntry.arguments?.getString("query")
            ephyra.feature.browse.migration.search.MigrateSourceSearchScreen(
                mangaId,
                sourceId,
                query,
                navController,
            )
        }

        // Sources Filter Screen
        navGraphBuilder.composable(ScreenRoutes.SourcesFilter.route) {
            ephyra.feature.browse.source.SourcesFilterScreen(navController)
        }

        // Extension Filter Screen
        navGraphBuilder.composable(ScreenRoutes.ExtensionFilter.route) {
            ephyra.feature.browse.extension.ExtensionFilterScreen(navController)
        }

        // Match Results Screen
        navGraphBuilder.composable(ScreenRoutes.MatchResults.route) {
            ephyra.feature.browse.source.authority.MatchResultsScreen(navController)
        }

        // Global Search Screen
        navGraphBuilder.composable(
            route = ScreenRoutes.GlobalSearch.route,
            arguments = listOf(androidx.navigation.navArgument("query") { nullable = true }),
        ) { backStackEntry ->
            val query = backStackEntry.arguments?.getString("query") ?: ""
            ephyra.feature.browse.source.globalsearch.GlobalSearchScreen(query, null, navController)
        }

        // Browse Source Screen
        navGraphBuilder.composable(
            route = ScreenRoutes.BrowseSource.route,
            arguments = listOf(
                androidx.navigation.navArgument("sourceId") {
                    type = androidx.navigation.NavType.LongType
                },
                androidx.navigation.navArgument("query") { nullable = true },
            ),
        ) { backStackEntry ->
            val sourceId = backStackEntry.arguments?.getLong("sourceId") ?: return@composable
            val query = backStackEntry.arguments?.getString("query")
            ephyra.feature.browse.source.browse.BrowseSourceScreen(sourceId, query, navController)
        }

        // Source Preferences Screen
        navGraphBuilder.composable(
            route = ScreenRoutes.SourcePreferences.route,
            arguments = listOf(
                androidx.navigation.navArgument("sourceId") {
                    type = androidx.navigation.NavType.LongType
                },
            ),
        ) { backStackEntry ->
            val sourceId = backStackEntry.arguments?.getLong("sourceId") ?: return@composable
            ephyra.feature.browse.extension.details.SourcePreferencesScreen(sourceId, navController)
        }

        // Extension Details Screen
        navGraphBuilder.composable(
            route = ScreenRoutes.ExtensionDetails.route,
            arguments = listOf(
                androidx.navigation.navArgument("pkgName") {
                    type = androidx.navigation.NavType.StringType
                },
            ),
        ) { backStackEntry ->
            val pkgName = backStackEntry.arguments?.getString("pkgName") ?: return@composable
            ephyra.feature.browse.extension.details.ExtensionDetailsScreen(pkgName, navController)
        }
    }
}
