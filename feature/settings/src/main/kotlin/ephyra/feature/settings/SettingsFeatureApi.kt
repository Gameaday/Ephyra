package ephyra.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import ephyra.feature.settings.screen.SettingsAdvancedScreen
import ephyra.feature.settings.screen.SettingsAppearanceScreen
import ephyra.feature.settings.screen.SettingsBrowseScreen
import ephyra.feature.settings.screen.SettingsDataScreen
import ephyra.feature.settings.screen.SettingsDownloadScreen
import ephyra.feature.settings.screen.SettingsLibraryScreen
import ephyra.feature.settings.screen.SettingsReaderScreen
import ephyra.feature.settings.screen.SettingsSearchScreen
import ephyra.feature.settings.screen.SettingsSecurityScreen
import ephyra.feature.settings.screen.SettingsTrackingScreen
import ephyra.feature.settings.screen.about.AboutScreen
import ephyra.feature.settings.screen.about.OpenSourceLibraryLicenseScreen
import ephyra.feature.settings.screen.about.OpenSourceLicensesScreen
import ephyra.feature.settings.screen.advanced.ClearDatabaseScreen
import ephyra.feature.settings.screen.appearance.AppLanguageScreen
import ephyra.feature.settings.screen.browse.ExtensionReposScreen
import ephyra.feature.settings.screen.data.CreateBackupScreen
import ephyra.feature.settings.screen.data.RestoreBackupScreen
import ephyra.feature.settings.screen.debug.BackupSchemaScreen
import ephyra.feature.settings.screen.debug.DebugInfoScreen
import ephyra.feature.settings.screen.debug.WorkerInfoScreen
import ephyra.presentation.core.feature.FeatureApi
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import javax.inject.Inject

class SettingsFeatureApi @Inject constructor() : FeatureApi {
    override fun register(
        navGraphBuilder: NavGraphBuilder,
        navController: NavHostController,
    ) {
        navGraphBuilder.composable(ScreenRoutes.Settings.route) {
            SettingsScreen(null, navController)
        }

        navGraphBuilder.composable(ScreenRoutes.About.route) {
            AboutScreen(navController)
        }

        navGraphBuilder.composable(ScreenRoutes.OpenSourceLicenses.route) {
            OpenSourceLicensesScreen(navController)
        }

        navGraphBuilder.composable(
            route = ScreenRoutes.OpenSourceLibraryLicense.route,
            arguments = listOf(
                androidx.navigation.navArgument("name") {
                    type = androidx.navigation.NavType.StringType
                },
            ),
        ) { backStackEntry ->
            val name = backStackEntry.arguments?.getString("name") ?: return@composable
            OpenSourceLibraryLicenseScreen(name, navController)
        }

        navGraphBuilder.composable(ScreenRoutes.ClearDatabase.route) {
            ClearDatabaseScreen(navController)
        }

        navGraphBuilder.composable(ScreenRoutes.AppLanguage.route) {
            AppLanguageScreen(navController)
        }

        navGraphBuilder.composable(
            route = ScreenRoutes.ExtensionRepos.route,
            arguments = listOf(
                androidx.navigation.navArgument("url") {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                },
            ),
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url")
            ExtensionReposScreen(url, navController)
        }

        navGraphBuilder.composable(ScreenRoutes.CreateBackup.route) {
            CreateBackupScreen(navController)
        }

        navGraphBuilder.composable(
            route = ScreenRoutes.RestoreBackup.route,
            arguments = listOf(
                androidx.navigation.navArgument("uri") {
                    type = androidx.navigation.NavType.StringType
                },
            ),
        ) { backStackEntry ->
            val uri = backStackEntry.arguments?.getString("uri") ?: return@composable
            RestoreBackupScreen(uri, navController)
        }

        navGraphBuilder.composable(ScreenRoutes.BackupSchema.route) {
            BackupSchemaScreen(navController)
        }

        navGraphBuilder.composable(ScreenRoutes.DebugInfo.route) {
            DebugInfoScreen(navController)
        }

        navGraphBuilder.composable(ScreenRoutes.WorkerInfo.route) {
            WorkerInfoScreen(navController)
        }

        navGraphBuilder.composable(ScreenRoutes.SettingsSearch.route) {
            SettingsSearchScreen(navController)
        }

        navGraphBuilder.composable(ScreenRoutes.SettingsAppearance.route) {
            SettingsAppearanceScreen.Content()
        }

        navGraphBuilder.composable(ScreenRoutes.SettingsLibrary.route) {
            SettingsLibraryScreen.Content()
        }

        navGraphBuilder.composable(ScreenRoutes.SettingsReader.route) {
            SettingsReaderScreen.Content()
        }

        navGraphBuilder.composable(ScreenRoutes.SettingsDownloads.route) {
            SettingsDownloadScreen.Content()
        }

        navGraphBuilder.composable(ScreenRoutes.SettingsTracking.route) {
            SettingsTrackingScreen.Content()
        }

        navGraphBuilder.composable(ScreenRoutes.SettingsBrowse.route) {
            SettingsBrowseScreen.Content()
        }

        navGraphBuilder.composable(ScreenRoutes.SettingsData.route) {
            SettingsDataScreen.Content()
        }

        navGraphBuilder.composable(ScreenRoutes.SettingsSecurity.route) {
            SettingsSecurityScreen.Content()
        }

        navGraphBuilder.composable(ScreenRoutes.SettingsAdvanced.route) {
            SettingsAdvancedScreen.Content()
        }
    }
}
