package ephyra.app.ui.main

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import ephyra.app.BuildConfig
import ephyra.app.data.notification.NotificationReceiver
import ephyra.app.extension.api.ExtensionApi
import ephyra.app.startup.StartupDiagnosticOverlay
import ephyra.app.startup.StartupTracker
import ephyra.app.ui.home.HomeScreen
import ephyra.app.util.system.isDebugBuildType
import ephyra.app.util.system.isNightlyBuildType
import ephyra.app.util.system.isPreviewBuildType
import ephyra.app.util.system.updaterEnabled
import ephyra.core.common.Constants
import ephyra.core.common.util.lang.launchIO
import ephyra.core.common.util.system.logcat
import ephyra.core.download.DownloadCache
import ephyra.core.migration.Migrator
import ephyra.data.cache.ChapterCache
import ephyra.data.updater.AppUpdateChecker
import ephyra.domain.base.BasePreferences
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.release.interactor.GetApplicationRelease
import ephyra.domain.source.interactor.GetIncognitoState
import ephyra.presentation.core.components.DownloadedOnlyBannerBackgroundColor
import ephyra.presentation.core.components.IncognitoModeBannerBackgroundColor
import ephyra.presentation.core.components.IndexingBannerBackgroundColor
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.ui.AppInfo
import ephyra.presentation.core.ui.AppReadySignal
import ephyra.presentation.core.ui.activity.BaseActivity
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import ephyra.presentation.core.util.collectAsState
import ephyra.presentation.core.util.system.openInBrowser
import ephyra.presentation.core.util.view.setComposeContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import logcat.LogPriority
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : BaseActivity(), AppReadySignal {

    @Inject lateinit var libraryPreferences: LibraryPreferences

    @Inject lateinit var preferences: BasePreferences

    @Inject lateinit var downloadCache: DownloadCache

    @Inject lateinit var chapterCache: ChapterCache

    @Inject lateinit var getIncognitoState: GetIncognitoState

    @Inject lateinit var uiPreferences: ephyra.domain.ui.UiPreferences

    @Inject lateinit var privacyPreferences: ephyra.core.common.core.security.PrivacyPreferences

    @Inject lateinit var storagePreferences: ephyra.domain.storage.service.StoragePreferences

    @Inject lateinit var extensionApi: ExtensionApi

    @Inject lateinit var appUpdateChecker: AppUpdateChecker

    @Inject lateinit var appInfo: AppInfo

    var ready = false

    override fun signalReady() {
        registerSecureActivity(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val isLaunch = savedInstanceState == null
        val splashScreen = if (isLaunch) installSplashScreen() else null

        super.onCreate(savedInstanceState)
        StartupTracker.complete(StartupTracker.Phase.ACTIVITY_CREATED)

        if (!isTaskRoot) {
            splashScreen?.setKeepOnScreenCondition { false }
            finish()
            return
        }

        setComposeContent {
            LaunchedEffect(Unit) {
                StartupTracker.complete(StartupTracker.Phase.COMPOSE_STARTED)
            }
            val navController = rememberNavController()
            androidx.compose.runtime.CompositionLocalProvider(
                ephyra.presentation.core.util.LocalUiPreferences provides uiPreferences,
                ephyra.presentation.core.util.LocalPrivacyPreferences provides privacyPreferences,
                LocalNavController provides navController,
            ) {
                var didMigration by remember { mutableStateOf<Boolean?>(null) }
                LaunchedEffect(Unit) {
                    val result = try {
                        withTimeoutOrNull(MIGRATION_TIMEOUT_MS) {
                            Migrator.awaitAndRelease()
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        StartupTracker.recordError(StartupTracker.Phase.MIGRATOR_COMPLETE, e)
                        Migrator.release()
                        false
                    }
                    didMigration = result ?: false
                    StartupTracker.complete(StartupTracker.Phase.MIGRATOR_COMPLETE)
                }

                val context = LocalContext.current
                val incognito by getIncognitoState.subscribe(null).collectAsStateWithLifecycle(initialValue = false)
                val downloadOnly by preferences.downloadedOnly().collectAsState()
                val indexing by downloadCache.isInitializing.collectAsStateWithLifecycle()

                val isSystemInDarkTheme = isSystemInDarkTheme()
                val statusBarBackgroundColor = when {
                    indexing -> IndexingBannerBackgroundColor
                    downloadOnly -> DownloadedOnlyBannerBackgroundColor
                    incognito -> IncognitoModeBannerBackgroundColor
                    else -> MaterialTheme.colorScheme.surface
                }
                LaunchedEffect(isSystemInDarkTheme, statusBarBackgroundColor) {
                    val lightStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK)
                    val darkStyle = SystemBarStyle.dark(Color.TRANSPARENT)
                    enableEdgeToEdge(
                        statusBarStyle = if (statusBarBackgroundColor.luminance() > 0.5) lightStyle else darkStyle,
                        navigationBarStyle = if (isSystemInDarkTheme) darkStyle else lightStyle,
                    )
                }

                Box(
                    modifier = Modifier.windowInsetsPadding(
                        WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal),
                    ),
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = ScreenRoutes.Home.route,
                    ) {
                        composable(ScreenRoutes.Home.route) { HomeScreen(navController) }

                        composable(
                            route = ScreenRoutes.MangaDetails.route,
                            arguments = listOf(
                                androidx.navigation.navArgument("mangaId") {
                                    type = androidx.navigation.NavType.LongType
                                },
                                androidx.navigation.navArgument("fromSource") {
                                    type =
                                        androidx.navigation.NavType.BoolType
                                },
                            ),
                        ) { backStackEntry ->
                            val mangaId = backStackEntry.arguments?.getLong("mangaId") ?: return@composable
                            val fromSource = backStackEntry.arguments?.getBoolean("fromSource") ?: false
                            ephyra.feature.manga.MangaDetailsScreen(
                                mangaId = mangaId,
                                fromSource = fromSource,
                                navigateUp = { navController.popBackStack() },
                            )
                        }

                        composable(ScreenRoutes.Onboarding.route) {
                            ephyra.feature.more.OnboardingScreen(navController)
                        }
                        composable(ScreenRoutes.DownloadQueue.route) {
                            ephyra.feature.download.DownloadQueueScreen(navController)
                        }
                        composable(ScreenRoutes.Stats.route) { ephyra.feature.stats.StatsScreen(navController) }
                        composable(ScreenRoutes.Upcoming.route) {
                            ephyra.feature.upcoming.UpcomingScreen(navController)
                        }
                        composable(ScreenRoutes.Category.route) {
                            ephyra.feature.category.CategoryScreen(navController)
                        }
                        composable(ScreenRoutes.Settings.route) {
                            ephyra.feature.settings.SettingsScreen(null, navController)
                        }
                        composable(ScreenRoutes.About.route) {
                            ephyra.feature.settings.screen.about.AboutScreen(navController)
                        }

                        composable(ScreenRoutes.MigrationConfig.route) { backStackEntry ->
                            val mangaIdsStr = backStackEntry.arguments?.getString("mangaIds") ?: return@composable
                            val mangaIds = mangaIdsStr.split(",").mapNotNull { it.toLongOrNull() }
                            ephyra.feature.migration.config.MigrationConfigScreen(mangaIds, navController)
                        }

                        composable(
                            route = ScreenRoutes.MigrationList.route,
                            arguments = listOf(
                                androidx.navigation.navArgument("mangaIds") {
                                    type =
                                        androidx.navigation.NavType.StringType
                                },
                                androidx.navigation.navArgument("query") { nullable = true },
                            ),
                        ) { backStackEntry ->
                            val mangaIdsStr = backStackEntry.arguments?.getString("mangaIds") ?: return@composable
                            val mangaIds = mangaIdsStr.split(",").mapNotNull { it.toLongOrNull() }
                            val query = backStackEntry.arguments?.getString("query")
                            ephyra.feature.migration.list.MigrationListScreen(mangaIds, query, navController)
                        }

                        composable(ScreenRoutes.MigrateSearch.route) { backStackEntry ->
                            val mangaId =
                                backStackEntry.arguments?.getString("mangaId")?.toLongOrNull() ?: return@composable
                            ephyra.feature.browse.migration.search.MigrateSearchScreen(mangaId, navController)
                        }

                        composable(
                            route = ScreenRoutes.MigrateSourceSearch.route,
                            arguments = listOf(
                                androidx.navigation.navArgument("mangaId") {
                                    type = androidx.navigation.NavType.LongType
                                },
                                androidx.navigation.navArgument("sourceId") {
                                    type =
                                        androidx.navigation.NavType.LongType
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

                        composable(ScreenRoutes.SourcesFilter.route) {
                            ephyra.feature.browse.source.SourcesFilterScreen(navController)
                        }
                        composable(ScreenRoutes.ExtensionFilter.route) {
                            ephyra.feature.browse.extension.ExtensionFilterScreen(navController)
                        }
                        composable(ScreenRoutes.MatchResults.route) {
                            ephyra.feature.browse.source.authority.MatchResultsScreen(navController)
                        }
                        composable(ScreenRoutes.ClearDatabase.route) {
                            ephyra.feature.settings.screen.advanced.ClearDatabaseScreen(navController)
                        }
                        composable(ScreenRoutes.AppLanguage.route) {
                            ephyra.feature.settings.screen.appearance.AppLanguageScreen(navController)
                        }
                        composable(ScreenRoutes.ExtensionRepos.route) { backStackEntry ->
                            val url = backStackEntry.arguments?.getString("url")
                            ephyra.feature.settings.screen.browse.ExtensionReposScreen(url, navController)
                        }
                        composable(ScreenRoutes.CreateBackup.route) {
                            ephyra.feature.settings.screen.data.CreateBackupScreen(navController)
                        }
                        composable(ScreenRoutes.RestoreBackup.route) { backStackEntry ->
                            val uri = backStackEntry.arguments?.getString("uri") ?: return@composable
                            ephyra.feature.settings.screen.data.RestoreBackupScreen(uri, navController)
                        }
                        composable(ScreenRoutes.BackupSchema.route) {
                            ephyra.feature.settings.screen.debug.BackupSchemaScreen(navController)
                        }
                        composable(ScreenRoutes.DebugInfo.route) {
                            ephyra.feature.settings.screen.debug.DebugInfoScreen(navController)
                        }
                        composable(ScreenRoutes.WorkerInfo.route) {
                            ephyra.feature.settings.screen.debug.WorkerInfoScreen(navController)
                        }
                        composable(ScreenRoutes.SettingsSearch.route) {
                            ephyra.feature.settings.screen.SettingsSearchScreen(navController)
                        }

                        composable(ScreenRoutes.SettingsAppearance.route) {
                            ephyra.feature.settings.screen.SettingsAppearanceScreen.Content()
                        }
                        composable(ScreenRoutes.SettingsLibrary.route) {
                            ephyra.feature.settings.screen.SettingsLibraryScreen.Content()
                        }
                        composable(ScreenRoutes.SettingsReader.route) {
                            ephyra.feature.settings.screen.SettingsReaderScreen.Content()
                        }
                        composable(ScreenRoutes.SettingsDownloads.route) {
                            ephyra.feature.settings.screen.SettingsDownloadScreen.Content()
                        }
                        composable(ScreenRoutes.SettingsTracking.route) {
                            ephyra.feature.settings.screen.SettingsTrackingScreen.Content()
                        }
                        composable(ScreenRoutes.SettingsBrowse.route) {
                            ephyra.feature.settings.screen.SettingsBrowseScreen.Content()
                        }
                        composable(ScreenRoutes.SettingsData.route) {
                            ephyra.feature.settings.screen.SettingsDataScreen.Content()
                        }
                        composable(ScreenRoutes.SettingsSecurity.route) {
                            ephyra.feature.settings.screen.SettingsSecurityScreen.Content()
                        }
                        composable(ScreenRoutes.SettingsAdvanced.route) {
                            ephyra.feature.settings.screen.SettingsAdvancedScreen.Content()
                        }

                        composable(
                            route = ScreenRoutes.MangaNotes.route,
                            arguments = listOf(
                                androidx.navigation.navArgument("mangaId") {
                                    type =
                                        androidx.navigation.NavType.LongType
                                },
                            ),
                        ) { backStackEntry ->
                            val mangaId = backStackEntry.arguments?.getLong("mangaId") ?: return@composable
                            ephyra.feature.manga.notes.MangaNotesScreen(mangaId, navController)
                        }

                        composable(
                            route = ScreenRoutes.GlobalSearch.route,
                            arguments = listOf(androidx.navigation.navArgument("query") { nullable = true }),
                        ) { backStackEntry ->
                            val query = backStackEntry.arguments?.getString("query") ?: ""
                            ephyra.feature.browse.source.globalsearch.GlobalSearchScreen(query, null, navController)
                        }

                        composable(
                            route = ScreenRoutes.BrowseSource.route,
                            arguments = listOf(
                                androidx.navigation.navArgument("sourceId") {
                                    type =
                                        androidx.navigation.NavType.LongType
                                },
                                androidx.navigation.navArgument("query") { nullable = true },
                            ),
                        ) { backStackEntry ->
                            val sourceId = backStackEntry.arguments?.getLong("sourceId") ?: return@composable
                            val query = backStackEntry.arguments?.getString("query")
                            ephyra.feature.browse.source.browse.BrowseSourceScreen(sourceId, query, navController)
                        }

                        composable(
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
                            ephyra.feature.webview.WebViewScreen(url, title, sourceId, navController)
                        }

                        composable(
                            route = ScreenRoutes.SourcePreferences.route,
                            arguments = listOf(
                                androidx.navigation.navArgument("sourceId") {
                                    type =
                                        androidx.navigation.NavType.LongType
                                },
                            ),
                        ) { backStackEntry ->
                            val sourceId = backStackEntry.arguments?.getLong("sourceId") ?: return@composable
                            ephyra.feature.browse.extension.details.SourcePreferencesScreen(sourceId, navController)
                        }

                        composable(
                            route = ScreenRoutes.ExtensionDetails.route,
                            arguments = listOf(
                                androidx.navigation.navArgument("pkgName") {
                                    type =
                                        androidx.navigation.NavType.StringType
                                },
                            ),
                        ) { backStackEntry ->
                            val pkgName = backStackEntry.arguments?.getString("pkgName") ?: return@composable
                            ephyra.feature.browse.extension.details.ExtensionDetailsScreen(pkgName, navController)
                        }
                    }
                }

                HandleOnNewIntent(context, navController)
                CheckForUpdates()
                ShowOnboarding()

                var showChangelog by remember { mutableStateOf(value = false) }
                LaunchedEffect(didMigration) {
                    if ((didMigration == true) && !BuildConfig.DEBUG) showChangelog = true
                }
                if (showChangelog) {
                    AlertDialog(
                        onDismissRequest = { showChangelog = false },
                        title = {
                            Text(
                                text = stringResource(
                                    ephyra.app.core.common.R.string.updated_version,
                                    BuildConfig.VERSION_NAME,
                                ),
                            )
                        },
                        dismissButton = {
                            TextButton(onClick = { openInBrowser(appInfo.releaseUrl) }) {
                                Text(text = stringResource(ephyra.app.core.common.R.string.whats_new))
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showChangelog = false }) {
                                Text(text = stringResource(ephyra.app.core.common.R.string.action_ok))
                            }
                        },
                    )
                }

                StartupDiagnosticOverlay(
                    isReleaseBuild = !(isDebugBuildType || isNightlyBuildType || isPreviewBuildType),
                )
            }
        }

        val startTime = System.currentTimeMillis()
        splashScreen?.setKeepOnScreenCondition {
            val elapsed = System.currentTimeMillis() - startTime
            (elapsed <= SPLASH_MIN_DURATION) || (!ready && (elapsed <= SPLASH_MAX_DURATION))
        }
    }

    @Composable
    private fun HandleOnNewIntent(context: Context, navController: NavHostController) {
        LaunchedEffect(Unit) {
            callbackFlow {
                val componentActivity = context as ComponentActivity
                val consumer = Consumer<Intent> { trySend(it) }
                componentActivity.addOnNewIntentListener(consumer)
                awaitClose { componentActivity.removeOnNewIntentListener(consumer) }
            }
                .collectLatest { handleIntentAction(it, navController) }
        }
    }

    @Composable
    private fun CheckForUpdates() {
        val context = LocalContext.current
        LaunchedEffect(Unit) {
            if (updaterEnabled) {
                try {
                    appUpdateChecker.checkForUpdate(context)
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e)
                }
            }
        }
        LaunchedEffect(Unit) {
            try {
                extensionApi.checkForUpdates(context)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    @Composable
    private fun ShowOnboarding() {
        val navController = LocalNavController.current
        LaunchedEffect(Unit) {
            if (!preferences.shownOnboardingFlow().get()) {
                navController.navigate(ScreenRoutes.Onboarding.route)
            }
        }
    }

    private fun handleIntentAction(intent: Intent, navController: NavHostController): Boolean {
        val notificationId = intent.getIntExtra("notificationId", -1)
        if (notificationId > -1) {
            NotificationReceiver.dismissNotification(
                applicationContext,
                notificationId,
                intent.getIntExtra("groupId", 0),
            )
        }

        val tabToOpen = when (intent.action) {
            Constants.SHORTCUT_LIBRARY -> HomeScreen.Tab.Library()
            Constants.SHORTCUT_MANGA -> {
                val idToOpen = intent.extras?.getLong(Constants.MANGA_EXTRA) ?: return false
                navController.popBackStack(navController.graph.findStartDestination().id, inclusive = false)
                HomeScreen.Tab.Library(idToOpen)
            }
            Constants.SHORTCUT_UPDATES -> HomeScreen.Tab.Updates
            Constants.SHORTCUT_HISTORY -> HomeScreen.Tab.History
            Constants.SHORTCUT_SOURCES -> HomeScreen.Tab.Browse(false)
            Constants.SHORTCUT_EXTENSIONS -> HomeScreen.Tab.Browse(true)
            Constants.SHORTCUT_DOWNLOADS -> {
                navController.popBackStack(navController.graph.findStartDestination().id, inclusive = false)
                HomeScreen.Tab.More(toDownloads = true)
            }
            Intent.ACTION_SEARCH, Intent.ACTION_SEND, "com.google.android.gms.actions.SEARCH_ACTION" -> {
                val query = intent.getStringExtra(SearchManager.QUERY) ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!query.isNullOrEmpty()) {
                    navController.popBackStack(navController.graph.findStartDestination().id, inclusive = false)
                    navController.navigate(ScreenRoutes.GlobalSearch.createRoute(query))
                }
                null
            }
            else -> return false
        }

        if (tabToOpen != null) {
            lifecycleScope.launch { HomeScreen.openTab(tabToOpen) }
        }

        ready = true
        StartupTracker.complete(StartupTracker.Phase.HOME_SCREEN_LOADED)
        return true
    }

    companion object {
        private const val SPLASH_MIN_DURATION = 500
        private const val SPLASH_MAX_DURATION = 3000
        private val MIGRATION_TIMEOUT_MS = 30.seconds
    }
}
