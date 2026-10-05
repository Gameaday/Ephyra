package ephyra.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ephyra.app.util.system.updaterEnabled
import ephyra.core.common.util.system.logcat
import ephyra.feature.browse.BrowseTabScreen
import ephyra.feature.history.HistoryTabScreen
import ephyra.feature.library.LibraryScreen
import ephyra.feature.more.MoreTabScreen
import ephyra.feature.updates.UpdatesScreen
import ephyra.presentation.core.components.AppStateBanners
import ephyra.presentation.core.components.material.NavigationBar
import ephyra.presentation.core.components.material.NavigationRail
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.i18n.pluralStringResource
import ephyra.presentation.core.theme.MotionTokens
import ephyra.presentation.core.ui.navigation.LocalMotionPreference
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.LocalNavigationCoordinator
import ephyra.presentation.core.ui.navigation.NavigationCoordinator
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import ephyra.presentation.core.util.collectAsState
import ephyra.presentation.core.util.isTabletUi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

object HomeScreen {
    private val _openTabEvent = MutableSharedFlow<Tab>(extraBufferCapacity = 1)
    val openTabEvent = _openTabEvent.asSharedFlow()

    fun openTab(tab: Tab) {
        _openTabEvent.tryEmit(tab)
    }

    sealed class Tab {
        data class Library(val mangaId: Long? = null) : Tab()
        data object Updates : Tab()
        data object History : Tab()
        data class Browse(val toExtensions: Boolean) : Tab()
        data class More(val toDownloads: Boolean) : Tab()
    }
}

/**
 * The five tab root routes, in tab order.
 *
 * Named once and used both to build the tab strip and to decide whether back should be intercepted.
 * Duplicating the list would let the two disagree, and the failure would be silent: back would stop
 * working at a root, or would keep intercepting after the user was already there.
 */
private val TAB_ROOT_ROUTES = listOf(
    ScreenRoutes.Library.route,
    ScreenRoutes.Updates.route,
    ScreenRoutes.History.route,
    ScreenRoutes.Browse.route,
    ScreenRoutes.More.route,
)

/**
 * Index of a tab route in [TAB_ROOT_ROUTES], or -1 when the route is not a tab root.
 *
 * The index is the tab's position in the bottom bar, which is also its position on the horizontal
 * axis the tabs slide along. Deriving the slide direction from this one ordered list means the
 * visual direction can never disagree with the order of the buttons the user is tapping.
 */
internal fun tabIndexOf(route: String?): Int = TAB_ROOT_ROUTES.indexOf(route)

/**
 * Whether a transition between two routes should slide forward (leftward, toward higher tab
 * indices), backward, or not slide at all.
 *
 * Returns null when either end is not a tab root, which means the pair is not two peers on the tab
 * axis and the caller should fall back to the fade-through. Returning a tri-state instead of a
 * boolean keeps "this is not a tab pair" from being silently folded into "backward".
 */
internal fun tabSlideForward(fromRoute: String?, toRoute: String?): Boolean? {
    val from = tabIndexOf(fromRoute)
    val to = tabIndexOf(toRoute)
    if (from == -1 || to == -1) return null
    return to > from
}

@Composable
fun HomeScreen(
    externalNavController: NavHostController = LocalNavController.current,
    viewModel: HomeViewModel = hiltViewModel(),
    bottomNavController: NavHostController = rememberNavController(),
    navigationCoordinator: NavigationCoordinator = LocalNavigationCoordinator.current,
) {
    // **The controller is a parameter, not created here.** It used to be `rememberNavController()`
    // in this function's body, which made it a child of the `Home` composition: navigating to a
    // series detail disposed this whole subtree, destroying the tab back stack and every entry
    // `saveState` had saved along with it. Returning recreated a controller at `Library`, so every
    // tab lost its scroll position, filter and search query on every detail visit, and a destination
    // the user had pushed inside a tab was silently discarded. `MainActivity` now owns this
    // controller, so it outlives the `Home` composition and `saveState`/`restoreState` actually
    // mean something.
    //
    // The default keeps previews and tests working without a caller, which is why it is a defaulted
    // parameter rather than a required one.
    val tabs = listOf(
        HomeTab.Library,
        HomeTab.Updates,
        HomeTab.History,
        HomeTab.Browse,
        HomeTab.More,
    )

    val state by viewModel.state.collectAsStateWithLifecycle()

    // Back inside a tab returns to that tab's own root rather than leaving the app.
    //
    // Without this, pressing back while a non-Library tab was showing popped the *app-level* stack
    // instead: the user left the app from a screen they had only navigated within, which reads as
    // "back does something arbitrary". Enabled only when the current destination is genuinely not
    // one of the five tab roots, so a back press at a root falls through to the system and exits
    // normally.
    val backStackEntry by bottomNavController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    if (currentRoute != null && currentRoute !in TAB_ROOT_ROUTES) {
        BackHandler {
            bottomNavController.popBackStack(ScreenRoutes.Library.route, inclusive = false)
        }
    }

    LaunchedEffect(Unit) {
        HomeScreen.openTabEvent.collect { tab ->
            val homeTab = when (tab) {
                is HomeScreen.Tab.Library -> HomeTab.Library
                HomeScreen.Tab.Updates -> HomeTab.Updates
                HomeScreen.Tab.History -> HomeTab.History
                is HomeScreen.Tab.Browse -> HomeTab.Browse
                is HomeScreen.Tab.More -> HomeTab.More
            }
            bottomNavController.navigate(homeTab.route) {
                popUpTo(bottomNavController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    CompositionLocalProvider(LocalNavController provides externalNavController) {
        Scaffold(
            topBar = {
                AppStateBanners(
                    downloadedOnlyMode = state.downloadOnly,
                    incognitoMode = state.incognito,
                    indexing = state.indexing,
                )
            },
            startBar = {
                if (isTabletUi()) {
                    NavigationRail {
                        tabs.forEach {
                            HomeNavigationRailItem(
                                it,
                                bottomNavController,
                                state.updatesBadgeCount,
                                state.extensionsBadgeCount,
                                navigationCoordinator,
                            )
                        }
                    }
                }
            },
            bottomBar = {
                if (!isTabletUi()) {
                    val bottomNavVisible by navigationCoordinator
                        .isBottomNavVisible
                        .collectAsStateWithLifecycle()
                    AnimatedVisibility(
                        visible = bottomNavVisible,
                        enter = expandVertically(
                            animationSpec = MotionTokens.tweenEnter(),
                        ),
                        exit = shrinkVertically(
                            animationSpec = MotionTokens.tweenExit(),
                        ),
                    ) {
                        NavigationBar {
                            tabs.forEach {
                                HomeNavigationBarItem(
                                    it,
                                    bottomNavController,
                                    state.updatesBadgeCount,
                                    state.extensionsBadgeCount,
                                    navigationCoordinator,
                                )
                            }
                        }
                    }
                }
            },
            contentWindowInsets = WindowInsets(0),
        ) { contentPadding ->
            Box(
                modifier = Modifier
                    .padding(contentPadding)
                    .consumeWindowInsets(contentPadding),
            ) {
                // The five tabs are ordered peers, so a tab change slides horizontally by tab order
                // instead of fading. A fade makes each tab read as a separate, discrete screen; a
                // slide keeps the bar and the content on one surface, and makes going back to an
                // earlier tab the same movement reversed rather than a second unrelated dissolve.
                //
                // Direction comes from the tab index, so the incoming page always enters from the
                // side the tapped button sits on. Non-tab destinations (a nested screen reached
                // inside a tab) keep the fade-through, since they are not peers on this axis.
                val reducedMotion = LocalMotionPreference.current
                NavHost(
                    navController = bottomNavController,
                    startDestination = ScreenRoutes.Library.route,
                    enterTransition = {
                        val forward = tabSlideForward(initialState.destination.route, targetState.destination.route)
                        when {
                            reducedMotion -> EnterTransition.None
                            forward == null -> MotionTokens.m3FadeThroughEnter()
                            else -> MotionTokens.m3TabSlideEnter(forward)
                        }
                    },
                    exitTransition = {
                        val forward = tabSlideForward(initialState.destination.route, targetState.destination.route)
                        when {
                            reducedMotion -> ExitTransition.None
                            forward == null -> MotionTokens.m3FadeThroughExit()
                            else -> MotionTokens.m3TabSlideExit(forward)
                        }
                    },
                    popEnterTransition = {
                        val forward = tabSlideForward(initialState.destination.route, targetState.destination.route)
                        when {
                            reducedMotion -> EnterTransition.None
                            forward == null -> MotionTokens.m3FadeThroughEnter()
                            else -> MotionTokens.m3TabSlideEnter(forward)
                        }
                    },
                    popExitTransition = {
                        val forward = tabSlideForward(initialState.destination.route, targetState.destination.route)
                        when {
                            reducedMotion -> ExitTransition.None
                            forward == null -> MotionTokens.m3FadeThroughExit()
                            else -> MotionTokens.m3TabSlideExit(forward)
                        }
                    },
                    modifier = Modifier,
                ) {
                    composable(ScreenRoutes.Library.route) {
                        LibraryScreen(navController = externalNavController)
                    }
                    composable(ScreenRoutes.Updates.route) {
                        UpdatesScreen(navController = externalNavController)
                    }
                    composable(ScreenRoutes.History.route) {
                        HistoryTabScreen(navController = externalNavController)
                    }
                    composable(ScreenRoutes.Browse.route) {
                        BrowseTabScreen(navController = externalNavController)
                    }
                    composable(ScreenRoutes.More.route) {
                        MoreTabScreen(navController = externalNavController)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.HomeNavigationBarItem(
    tab: HomeTab,
    navController: NavHostController,
    updatesBadgeCount: Int,
    extensionsBadgeCount: Int,
    navigationCoordinator: NavigationCoordinator,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true

    NavigationBarItem(
        selected = selected,
        onClick = {
            if (selected) {
                navigationCoordinator.triggerReselect(tab.route)
            } else {
                navController.navigate(tab.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        },
        icon = { HomeTabIcon(tab, selected, updatesBadgeCount, extensionsBadgeCount) },
        label = {
            Text(
                text = stringResource(tab.titleRes),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        alwaysShowLabel = true,
    )
}

@Composable
private fun HomeNavigationRailItem(
    tab: HomeTab,
    navController: NavHostController,
    updatesBadgeCount: Int,
    extensionsBadgeCount: Int,
    navigationCoordinator: NavigationCoordinator,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true

    NavigationRailItem(
        selected = selected,
        onClick = {
            if (selected) {
                navigationCoordinator.triggerReselect(tab.route)
            } else {
                navController.navigate(tab.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        },
        icon = { HomeTabIcon(tab, selected, updatesBadgeCount, extensionsBadgeCount) },
        label = {
            Text(
                text = stringResource(tab.titleRes),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        alwaysShowLabel = true,
    )
}

@Composable
private fun HomeTabIcon(
    tab: HomeTab,
    selected: Boolean,
    updatesBadgeCount: Int,
    extensionsBadgeCount: Int,
) {
    BadgedBox(
        badge = {
            if (tab == HomeTab.Updates) {
                if (updatesBadgeCount > 0) {
                    Badge {
                        val desc = pluralStringResource(
                            ephyra.app.core.common.R.plurals.notification_chapters_generic,
                            count = updatesBadgeCount,
                            updatesBadgeCount,
                        )
                        Text(
                            text = updatesBadgeCount.toString(),
                            modifier = Modifier.semantics { contentDescription = desc },
                        )
                    }
                }
            }
            if (tab == HomeTab.Browse) {
                if (extensionsBadgeCount > 0) {
                    Badge {
                        val desc = pluralStringResource(
                            ephyra.app.core.common.R.plurals.update_check_notification_ext_updates,
                            count = extensionsBadgeCount,
                            extensionsBadgeCount,
                        )
                        Text(
                            text = extensionsBadgeCount.toString(),
                            modifier = Modifier.semantics { contentDescription = desc },
                        )
                    }
                }
            }
        },
    ) {
        val image = AnimatedImageVector.animatedVectorResource(if (selected) tab.iconSelectedRes else tab.iconRes)
        val painter = rememberAnimatedVectorPainter(image, atEnd = selected)
        Icon(
            painter = painter,
            contentDescription = stringResource(tab.titleRes),
        )
    }
}

enum class HomeTab(
    val route: String,
    val titleRes: Int,
    val iconRes: Int,
    val iconSelectedRes: Int,
) {
    Library(
        ScreenRoutes.Library.route,
        ephyra.app.core.common.R.string.label_library,
        ephyra.presentation.core.R.drawable.anim_library_enter,
        ephyra.presentation.core.R.drawable.anim_library_enter,
    ),
    Updates(
        ScreenRoutes.Updates.route,
        ephyra.app.core.common.R.string.label_recent_updates,
        ephyra.presentation.core.R.drawable.anim_updates_enter,
        ephyra.presentation.core.R.drawable.anim_updates_enter,
    ),
    History(
        ScreenRoutes.History.route,
        ephyra.app.core.common.R.string.label_recent_manga,
        ephyra.presentation.core.R.drawable.anim_history_enter,
        ephyra.presentation.core.R.drawable.anim_history_enter,
    ),
    Browse(
        ScreenRoutes.Browse.route,
        ephyra.app.core.common.R.string.label_discover,
        ephyra.presentation.core.R.drawable.anim_browse_enter,
        ephyra.presentation.core.R.drawable.anim_browse_enter,
    ),
    More(
        ScreenRoutes.More.route,
        ephyra.app.core.common.R.string.label_more,
        ephyra.presentation.core.R.drawable.anim_more_enter,
        ephyra.presentation.core.R.drawable.anim_more_enter,
    ),
}
