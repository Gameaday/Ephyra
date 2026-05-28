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
import ephyra.presentation.core.feature.FeatureApi
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.ui.AppInfo
import ephyra.presentation.core.ui.AppReadySignal
import ephyra.presentation.core.ui.activity.BaseActivity
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.Screen
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
import logcat.LogPriority
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

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

    @Inject lateinit var featureApis: Set<@JvmSuppressWildcards FeatureApi>

    var ready = false

    override fun signalReady() {
        registerSecureActivity(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val isLaunch = savedInstanceState == null
        val splashScreen = if (isLaunch) installSplashScreen() else null

        super.onCreate(savedInstanceState)
        if (!isLaunch) ready = true
        StartupTracker.complete(StartupTracker.Phase.ACTIVITY_CREATED)

        if (!isTaskRoot) {
            splashScreen?.setKeepOnScreenCondition { false }
            finish()
            return
        }

        setComposeContent {
            var didMigration by remember { mutableStateOf<Boolean?>(null) }

            LaunchedEffect(Unit) {
                StartupTracker.complete(StartupTracker.Phase.COMPOSE_STARTED)
            }
            val navController = rememberNavController()
            LaunchedEffect(navController, didMigration) {
                if (isLaunch && didMigration != null) {
                    handleIntentAction(intent, navController)
                }
            }
            androidx.compose.runtime.CompositionLocalProvider(
                ephyra.presentation.core.util.LocalUiPreferences provides uiPreferences,
                ephyra.presentation.core.util.LocalPrivacyPreferences provides privacyPreferences,
                LocalNavController provides navController,
            ) {
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

                if (didMigration != null) {
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

                            composable(ScreenRoutes.DownloadQueue.route) {
                                ephyra.feature.download.DownloadQueueScreen(navController)
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

                            featureApis.forEach { featureApi ->
                                try {
                                    featureApi.register(this, navController)
                                } catch (e: Exception) {
                                    logcat(LogPriority.ERROR, e) {
                                        "Failed to register feature: ${featureApi.javaClass.simpleName}"
                                    }
                                }
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
        ready = true
        StartupTracker.complete(StartupTracker.Phase.HOME_SCREEN_LOADED)

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
                    navController.navigate(Screen.GlobalSearch(query))
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
