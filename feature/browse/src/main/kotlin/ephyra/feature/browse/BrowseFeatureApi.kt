package ephyra.feature.browse

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import ephyra.presentation.core.feature.FeatureApi
import ephyra.presentation.core.ui.navigation.Screen
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import javax.inject.Inject

class BrowseFeatureApi @Inject constructor() : FeatureApi {
    override fun register(
        navGraphBuilder: NavGraphBuilder,
        navController: NavHostController,
    ) {
        // Browse main screen route
        navGraphBuilder.composable<Screen.Browse> {
            BrowseTabScreen(navController = navController)
        }

        // Migrate Manga Screen
        navGraphBuilder.composable(
            route = ScreenRoutes.MigrateManga.route,
            arguments = listOf(
                androidx.navigation.navArgument("sourceId") {
                    type = androidx.navigation.NavType.LongType
                },
            ),
        ) { backStackEntry ->
            val sourceId = backStackEntry.arguments?.getLong("sourceId") ?: return@composable
            ephyra.feature.browse.migration.manga.MigrateMangaScreen(sourceId, navController)
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

        // Extensions (source/extension management), standalone route — Settings entry point.
        navGraphBuilder.composable(ScreenRoutes.Extensions.route) {
            ephyra.feature.browse.extension.ExtensionsRouteScreen(navController)
        }

        // Source migration, standalone route — Settings entry point.
        navGraphBuilder.composable(ScreenRoutes.SourceMigration.route) {
            ephyra.feature.browse.migration.sources.MigrateSourceRouteScreen(navController)
        }

        // Sources Filter Screen
        navGraphBuilder.composable(ScreenRoutes.SourcesFilter.route) {
            ephyra.feature.browse.source.SourcesFilterScreen(navController)
        }

        // Extension Filter Screen
        navGraphBuilder.composable(ScreenRoutes.ExtensionFilter.route) {
            ephyra.feature.browse.extension.ExtensionFilterScreen(navController)
        }

        // Global Search Screen
        //
        // Both this and the source results below provide an animated-visibility scope, because both
        // list manga covers that are the shared element when a series is opened from them. Without
        // the scope the covers here cannot participate in the shared transition, and the series
        // page's copy of the cover would then have no counterpart — which, since that pair's
        // container motion is a deliberate no-op, leaves the transition with nothing to animate.
        // `MainActivity.motionRoutePair` reads `hostsSharedCover`, which names the same set.
        navGraphBuilder.composable<Screen.GlobalSearch> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.GlobalSearch>()
            androidx.compose.runtime.CompositionLocalProvider(
                ephyra.presentation.core.ui.navigation.LocalNavAnimatedVisibilityScope provides this@composable,
            ) {
                ephyra.feature.browse.source.globalsearch.GlobalSearchScreen(route.query ?: "", null, navController)
            }
        }

        // Browse Source Screen
        navGraphBuilder.composable<Screen.BrowseSource> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.BrowseSource>()
            androidx.compose.runtime.CompositionLocalProvider(
                ephyra.presentation.core.ui.navigation.LocalNavAnimatedVisibilityScope provides this@composable,
            ) {
                ephyra.feature.browse.source.browse.BrowseSourceScreen(route.sourceId, route.query, navController)
            }
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
        navGraphBuilder.composable<Screen.ExtensionDetails> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.ExtensionDetails>()
            ephyra.feature.browse.extension.details.ExtensionDetailsScreen(route.pkgName, navController)
        }
    }
}
