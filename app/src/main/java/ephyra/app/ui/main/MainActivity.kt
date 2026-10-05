package ephyra.app.ui.main

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dagger.hilt.android.AndroidEntryPoint
import ephyra.app.BuildConfig
import ephyra.app.data.notification.NotificationReceiver
import ephyra.app.extension.api.ExtensionApi
import ephyra.app.startup.StartupTracker
import ephyra.app.ui.home.HomeScreen
import ephyra.app.util.system.isDebugBuildType
import ephyra.app.util.system.isNightlyBuildType
import ephyra.app.util.system.isPreviewBuildType
import ephyra.app.util.system.updaterEnabled
import ephyra.core.common.Constants
import ephyra.core.common.util.lang.launchIO
import ephyra.core.common.util.storage.BackupStaging
import ephyra.core.common.util.system.logcat
import ephyra.core.common.util.system.openInBrowser
import ephyra.core.download.DownloadCache
import ephyra.core.migration.Migrator
import ephyra.data.updater.AppUpdateChecker
import ephyra.domain.base.BasePreferences
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.navigation.motion.MotionDirection
import ephyra.domain.navigation.motion.MotionPlan
import ephyra.domain.navigation.motion.MotionPolicy
import ephyra.domain.navigation.motion.MotionRoutePair
import ephyra.domain.release.interactor.GetApplicationRelease
import ephyra.domain.source.interactor.GetIncognitoState
import ephyra.presentation.core.components.DownloadedOnlyBannerBackgroundColor
import ephyra.presentation.core.components.IncognitoModeBannerBackgroundColor
import ephyra.presentation.core.components.IndexingBannerBackgroundColor
import ephyra.presentation.core.feature.FeatureApi
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.theme.MotionTokens
import ephyra.presentation.core.ui.AppInfo
import ephyra.presentation.core.ui.AppReadySignal
import ephyra.presentation.core.ui.activity.BaseActivity
import ephyra.presentation.core.ui.navigation.LocalMotionPreference
import ephyra.presentation.core.ui.navigation.LocalNavAnimatedVisibilityScope
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.LocalNavigationCoordinator
import ephyra.presentation.core.ui.navigation.LocalSharedTransitionScope
import ephyra.presentation.core.ui.navigation.NavigationCoordinator
import ephyra.presentation.core.ui.navigation.Screen
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import ephyra.presentation.core.ui.navigation.rememberSystemReducedMotion
import ephyra.presentation.core.util.AppNavigator
import ephyra.presentation.core.util.LocalAppNavigator
import ephyra.presentation.core.util.collectAsState
import ephyra.presentation.core.util.view.setComposeContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

private fun NavBackStackEntry.isMangaDetails(): Boolean = runCatching {
    toRoute<Screen.MangaDetails>()
}.isSuccess

private fun NavBackStackEntry.isHome(): Boolean = destination.route == ScreenRoutes.Home.route

private fun NavBackStackEntry.isBrowseSource(): Boolean = runCatching {
    toRoute<Screen.BrowseSource>()
}.isSuccess

private fun NavBackStackEntry.isGlobalSearch(): Boolean = runCatching {
    toRoute<Screen.GlobalSearch>()
}.isSuccess

/**
 * Whether this destination can be one end of the shared-cover transition.
 *
 * A destination qualifies only if it *both* renders `MangaCover` with the manga's id for its list
 * items *and* provides `LocalNavAnimatedVisibilityScope`. The shared element needs a scope at each
 * end inside the same `SharedTransitionLayout`; a screen that renders the cover but provides no
 * scope cannot match, and the element then has no counterpart — which, because the pair's container
 * motion is a deliberate no-op, leaves the whole transition with nothing to animate.
 *
 * This predicate is the "and" of those two obligations written down once. It used to be
 * `destination.route == Home`, which was too narrow: `Home` is the whole tab shell, so any series
 * opened from a source's results or from global search took the generic fallback even though those
 * lists carry the same cover.
 *
 * `MangaDetails` is deliberately **not** a host. It renders covers and provides the scope, but the
 * direction rule below reads "leaving a series page" as BACKWARD, so listing it would give a
 * forward navigation to a related series the shorter return timeline. Details-to-details keeps the
 * generic shared-axis treatment until that direction can be told apart.
 */
private fun NavBackStackEntry.hostsSharedCover(): Boolean =
    isHome() || isBrowseSource() || isGlobalSearch()

/**
 * The motion route pair for a transition between [from] and [to], or null when the transition has
 * no declared rule and should keep the default shared-axis treatment.
 *
 * This lives here rather than in `MotionPolicy` because recognising a `NavBackStackEntry` is an
 * Android-layer concern; the *decision* about what the pair should do is `MotionPolicy`'s, and this
 * function only names the pair.
 *
 * Both directions are named in one `when`, over one predicate, because they must resolve to the same
 * pair: the cover is the same element whether it is growing or shrinking, and predictive back
 * replays this model, so a pair that differed on the way out would give the gesture a different
 * animation from the toolbar arrow. What differs is [MotionDirection], which `MotionPolicy` uses to
 * pick the timeline: M3's shared-element spec is deliberately asymmetric, with the return shorter
 * than the arrival.
 */
private fun motionRoutePair(
    from: NavBackStackEntry,
    to: NavBackStackEntry,
    isPop: Boolean,
): MotionRoutePair? = when {
    // Pushing into a series from a list that carries its cover.
    !isPop && from.hostsSharedCover() && to.isMangaDetails() -> MotionRoutePair.LIBRARY_SERIES

    // Popping out of a series back to the list that carries its cover.
    //
    // The two branches are kept separate on purpose: a series page can also navigate *forward* into
    // a cover list (e.g. "browse more from this source"), and that is not a return. Letting it name
    // the pair would hold the container still, look for a shared cover the target does not
    // necessarily render, and run the shorter backward timeline for a forward move. Only a real pop
    // out of the series page is a return.
    isPop && from.isMangaDetails() && to.hostsSharedCover() -> MotionRoutePair.LIBRARY_SERIES

    else -> null
}

/**
 * Which way the user is travelling.
 *
 * Direction comes from [isPop], not from which entry is the series page. The entries alone cannot
 * answer it, because a series page can navigate forward *into* a cover list as well as back out of
 * one; only the transition that fired (push vs pop) separates the two. Reading direction from the
 * entry roles was correct only while the pair could not form in the forward direction, which the
 * wider [hostsSharedCover] set changed.
 */
private fun motionDirectionFor(
    from: NavBackStackEntry,
    to: NavBackStackEntry,
    isPop: Boolean,
): MotionDirection? = motionRoutePair(from, to, isPop)?.let {
    if (isPop) MotionDirection.BACKWARD else MotionDirection.FORWARD
}

/**
 * The manga whose cover is the shared element for this transition, or null when the transition does
 * not involve a series page.
 *
 * The key is derived from the route argument rather than from whatever the user last tapped, so the
 * plan cannot name an element the destination is not going to render.
 */
private fun NavBackStackEntry.sharedCoverMangaId(): Long? =
    runCatching { toRoute<Screen.MangaDetails>().mangaId }.getOrNull()

/**
 * The resolved plan for this transition.
 *
 * `sharedElementFound` is reported as true whenever the pair is the library-cover pair, because the
 * library cell and the series header both render the cover unconditionally. If the element genuinely
 * fails to resolve, Compose declines to animate it and the container crossfade below is what
 * remains, which is the documented fallback rather than a broken intermediate state.
 */
private fun motionPlanFor(
    from: NavBackStackEntry,
    to: NavBackStackEntry,
    reducedMotion: Boolean,
    isPop: Boolean,
): MotionPlan? {
    val pair = motionRoutePair(from, to, isPop) ?: return null
    val direction = motionDirectionFor(from, to, isPop) ?: return null
    val mangaId = to.sharedCoverMangaId() ?: from.sharedCoverMangaId() ?: return null
    return MotionPolicy.plan(
        pair = pair,
        direction = direction,
        sharedElementKey = MotionPolicy.mangaCoverKey(mangaId),
        sharedElementFound = true,
        reducedMotion = reducedMotion,
    )
}

@AndroidEntryPoint
class MainActivity : BaseActivity(), AppReadySignal {

    @Inject
    lateinit var libraryPreferences: LibraryPreferences

    @Inject
    lateinit var preferences: BasePreferences

    @Inject
    lateinit var downloadCache: DownloadCache

    @Inject
    lateinit var getIncognitoState: GetIncognitoState

    @Inject
    lateinit var uiPreferences: ephyra.domain.ui.UiPreferences

    @Inject
    lateinit var privacyPreferences: ephyra.core.common.core.security.PrivacyPreferences

    @Inject
    lateinit var storagePreferences: ephyra.domain.storage.service.StoragePreferences

    @Inject
    lateinit var extensionApi: ExtensionApi

    @Inject
    lateinit var appUpdateChecker: AppUpdateChecker

    @Inject
    lateinit var appInfo: AppInfo

    @Inject
    lateinit var appNavigator: AppNavigator

    @Inject
    lateinit var featureApis: Set<@JvmSuppressWildcards FeatureApi>

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

            // The bottom-tab controller is hoisted here, above the `NavHost`, so its lifetime is
            // the Activity's rather than the `Home` destination's. `HomeScreen` used to create it
            // with `rememberNavController()` inside its own body, which made it a child of the
            // `Home` composition — so navigating to a series detail disposed the whole tab back
            // stack together with every entry `saveState` had saved. Returning rebuilt a controller
            // at `Library`, and each tab lost its scroll position, filter and search query on every
            // detail visit.
            //
            // This is the concrete form of the problem ADR-0011 describes: the defect was never the
            // nested `NavHost` (which is the correct pattern for per-tab back stacks) but the
            // *lifetime* of the controller owning it. Hoisting fixes the cause without touching the
            // graph shape, so no tab loses its back stack.
            val bottomNavController = rememberNavController()

            // Owns bottom-bar visibility and tab reselect for this Activity. `NAV-001` replaced two
            // global `object` singletons with this; scoping it to the composition is what stops one
            // Activity (or one test) from driving another one's navigation state.
            val navigationCoordinator = remember { NavigationCoordinator() }

            LaunchedEffect(navController, didMigration) {
                if (didMigration != null) {
                    ready = true
                    StartupTracker.complete(StartupTracker.Phase.HOME_SCREEN_LOADED)
                    if (isLaunch) {
                        handleIntentAction(intent, navController)
                    }
                }
            }
            androidx.compose.runtime.CompositionLocalProvider(
                ephyra.presentation.core.util.LocalUiPreferences provides uiPreferences,
                ephyra.presentation.core.util.LocalPrivacyPreferences provides privacyPreferences,
                LocalNavController provides navController,
                LocalAppNavigator provides appNavigator,
                LocalNavigationCoordinator provides navigationCoordinator,
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
                        // Read once here so every transition in this graph agrees. Reading it per
                        // transition would let two screens disagree about whether the user asked
                        // for less motion.
                        val reducedMotion = rememberSystemReducedMotion()
                        CompositionLocalProvider(
                            LocalMotionPreference provides reducedMotion,
                        ) {
                            SharedTransitionLayout {
                                CompositionLocalProvider(
                                    LocalSharedTransitionScope provides this,
                                ) {
                                    NavHost(
                                        navController = navController,
                                        startDestination = ScreenRoutes.Home.route,
                                        enterTransition = {
                                            val plan = motionPlanFor(
                                                initialState,
                                                targetState,
                                                reducedMotion,
                                                isPop = false,
                                            )
                                            if (plan != null) {
                                                MotionTokens.containerEnter(
                                                    plan.effectiveContainerMotion,
                                                    plan.effectiveDurationMillis,
                                                )
                                            } else {
                                                // Fallback path (no declared pair, or no cover id).
                                                // Must honour reduced motion too: the plan path
                                                // does, so a fallback that animates anyway would
                                                // make the setting inconsistent per route.
                                                if (reducedMotion) {
                                                    EnterTransition.None
                                                } else {
                                                    MotionTokens.m3SharedAxisXEnter()
                                                }
                                            }
                                        },
                                        exitTransition = {
                                            val plan = motionPlanFor(
                                                initialState,
                                                targetState,
                                                reducedMotion,
                                                isPop = false,
                                            )
                                            if (plan != null) {
                                                MotionTokens.containerExit(
                                                    plan.effectiveContainerMotion,
                                                    plan.effectiveDurationMillis,
                                                )
                                            } else {
                                                if (reducedMotion) {
                                                    ExitTransition.None
                                                } else {
                                                    MotionTokens.m3SharedAxisXExit()
                                                }
                                            }
                                        },
                                        popEnterTransition = {
                                            val plan = motionPlanFor(
                                                initialState,
                                                targetState,
                                                reducedMotion,
                                                isPop = true,
                                            )
                                            if (plan != null) {
                                                MotionTokens.containerEnter(
                                                    plan.effectiveContainerMotion,
                                                    plan.effectiveDurationMillis,
                                                )
                                            } else {
                                                if (reducedMotion) {
                                                    EnterTransition.None
                                                } else {
                                                    MotionTokens.m3SharedAxisXPopEnter()
                                                }
                                            }
                                        },
                                        popExitTransition = {
                                            val plan = motionPlanFor(
                                                initialState,
                                                targetState,
                                                reducedMotion,
                                                isPop = true,
                                            )
                                            if (plan != null) {
                                                MotionTokens.containerExit(
                                                    plan.effectiveContainerMotion,
                                                    plan.effectiveDurationMillis,
                                                )
                                            } else {
                                                if (reducedMotion) {
                                                    ExitTransition.None
                                                } else {
                                                    MotionTokens.m3SharedAxisXPopExit()
                                                }
                                            }
                                        },
                                    ) {
                                        composable(ScreenRoutes.Home.route) {
                                            CompositionLocalProvider(
                                                LocalNavAnimatedVisibilityScope provides this@composable,
                                            ) {
                                                HomeScreen(navController, bottomNavController = bottomNavController)
                                            }
                                        }

                                        composable(ScreenRoutes.DownloadQueue.route) {
                                            ephyra.feature.download.DownloadQueueScreen(navController)
                                        }
                                        composable(ScreenRoutes.MigrationConfig.route) { backStackEntry ->
                                            val mangaIdsStr =
                                                backStackEntry.arguments?.getString("mangaIds") ?: return@composable
                                            val mangaIds = mangaIdsStr.split(",").mapNotNull { it.toLongOrNull() }
                                            ephyra.feature.migration.config.MigrationConfigScreen(
                                                mangaIds,
                                                navController,
                                            )
                                        }

                                        composable(
                                            route = ScreenRoutes.MigrationList.route,
                                            arguments = listOf(
                                                androidx.navigation.navArgument("mangaIds") {
                                                    type = androidx.navigation.NavType.StringType
                                                },
                                                androidx.navigation.navArgument("query") { nullable = true },
                                            ),
                                        ) { backStackEntry ->
                                            val mangaIdsStr =
                                                backStackEntry.arguments?.getString("mangaIds") ?: return@composable
                                            val mangaIds = mangaIdsStr.split(",").mapNotNull { it.toLongOrNull() }
                                            val query = backStackEntry.arguments?.getString("query")
                                            ephyra.feature.migration.list.MigrationListScreen(
                                                mangaIds,
                                                query,
                                                navController,
                                            )
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
                }
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
        // React to the flag instead of a one-shot blocking read: the flag is false
        // during onboarding, and permission grants / file pickers can recreate the
        // activity, re-running this effect. Without dedup, each recreation stacked
        // another Onboarding destination on the back stack, so finishing popped
        // only one and Back re-entered onboarding — the "onboarding loop".
        LaunchedEffect(Unit) {
            preferences.shownOnboardingFlow().changes().collect { completed ->
                if (!completed) {
                    navController.navigate(ScreenRoutes.Onboarding.route) {
                        launchSingleTop = true
                    }
                }
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

            Intent.ACTION_VIEW -> {
                val data = intent.data
                val scheme = data?.scheme
                if (data != null && (scheme == "tachiyomi" || scheme == "mihon" || scheme == "ephyra") &&
                    data.host == "add-repo"
                ) {
                    val repoUrl = data.getQueryParameter("url")
                    if (!repoUrl.isNullOrEmpty()) {
                        navController.popBackStack(navController.graph.findStartDestination().id, inclusive = false)
                        navController.navigate(ScreenRoutes.ExtensionRepos.createRoute(repoUrl))
                    }
                } else if (data != null) {
                    val isBackup = data.path?.endsWith(".tachibk", ignoreCase = true) == true ||
                        data.path?.endsWith(".proto.gz", ignoreCase = true) == true ||
                        intent.type?.contains("tachibk", ignoreCase = true) == true
                    if (isBackup) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val stagedUri = BackupStaging.stageBackupFile(this@MainActivity, data)
                                withContext(Dispatchers.Main) {
                                    val route = ScreenRoutes.RestoreBackup.createRoute(stagedUri.toString())
                                    navController.navigate(route)
                                }
                            } catch (e: Exception) {
                                logcat(LogPriority.ERROR, e) { "Failed to stage backup from external intent: $data" }
                            }
                        }
                    }
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
