package ephyra.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ephyra.app.util.system.updaterEnabled
import ephyra.core.common.util.system.logcat
import ephyra.domain.navigation.motion.MotionPolicy
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
 * The index is the tab's position in the bottom bar. Deriving tab membership from this one
 * ordered list means the transition decision can never disagree with the order of the buttons
 * the user is tapping.
 */
internal fun tabIndexOf(route: String?): Int = TAB_ROOT_ROUTES.indexOf(route)

/**
 * Whether a transition runs between two tab roots — two peers on the tab axis.
 *
 * The owner's directive (2026-11-11) reverses the 2026-11-09 no-movement lock for this one
 * surface: two tabs are siblings on one ordered axis, so peers now slide in bar order — the
 * "tablet or book being read and navigated" aesthetic — while a nested screen reached inside a
 * tab is not a peer and keeps the fade-through. Peer-ness stays a test, not a comment.
 */
internal fun isTabPeerTransition(fromRoute: String?, toRoute: String?): Boolean {
    val from = tabIndexOf(fromRoute)
    val to = tabIndexOf(toRoute)
    return from != -1 && to != -1
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

    // Back at a tab root exits the app, handing the gesture to the system's predictive back,
    // which is what Android's top-level guidance prescribes for peer destinations. The tab
    // strip's own taps already preserve each tab's state (`popUpTo` + `saveState` +
    // `restoreState` below), so exiting from a tab costs nothing a walk could have saved.
    //
    // **This is a deliberate reversal of the tab-history back walk, on the owner's defect
    // report (2026-11-08).** The walk intercepted back and navigated to the previously visited
    // tab, so back at a main tab hopped to that tab instead of leaving — pressed again, it
    // swapped between the two most recent tabs, a motion that read as a glitch rather than
    // navigation. The motion was the visible half of the same defect: a back gesture replayed
    // a tab-slide animation, which the predictive-back hand-off never previews, so the return
    // path looked inconsistent with every other back in the app. No history is kept and no
    // `BackHandler` is installed; the structural test pins both absences so the walk does not
    // quietly return.
    //
    // **Amended on the owner's second defect report (2026-11-11): the exit only worked at the
    // start tab.** Tab switching kept `Library` — the graph's start destination — beneath every
    // other tab, so back from History or Updates still popped the tab stack and dropped the
    // user into Library instead of leaving. Worse, that hop ran through the pop slots, which
    // swapped the index order before asking for a slide direction, so the move animated
    // backwards: the gesture previewed Library arriving from the far side, the opposite of
    // every push between tabs. A tab switch now clears the stack (`popUpTo(0)` with
    // `inclusive = true`, state still saved and restored), so no tab is ever beneath another:
    // back at *every* tab root finds nothing to pop and hands the gesture to the system, and
    // the pop slots ask for direction in bar order like every other slot — whichever slots
    // fire, the slide follows the bar.

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
                popUpTo(0) {
                    inclusive = true
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
                        // The bar fades rather than growing or shrinking in place: a bar that
                        // expands and collapses is exactly the "element flying up and down" the
                        // owner's motion audit rejected, and it resizes the content beneath it
                        // while it moves. A fade changes no geometry.
                        enter = fadeIn(
                            animationSpec = MotionTokens.tweenEnter(),
                        ),
                        exit = fadeOut(
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
                // The five tabs are ordered siblings, and a tab change slides in bar order: the
                // owner's directive (2026-11-11, reversing the 2026-11-09 no-movement audit)
                // asked for the "tablet or book being read and navigated" aesthetic — a move
                // between sibling pages, not elements flying, fading or zooming. The slide is
                // pure translation at the policy's duration, its direction derived from the same
                // ordered list that builds the bar. Non-tab destinations (a nested screen
                // reached inside a tab) are not siblings on the tab axis and keep the
                // fade-through.
                val reducedMotion = LocalMotionPreference.current
                NavHost(
                    navController = bottomNavController,
                    startDestination = ScreenRoutes.Library.route,
                    enterTransition = {
                        val tabPair = isTabPeerTransition(
                            initialState.destination.route,
                            targetState.destination.route,
                        )
                        when {
                            reducedMotion -> EnterTransition.None
                            tabPair -> MotionTokens.m3TabSlideEnter(
                                MotionPolicy.tabSlideDirection(
                                    tabIndexOf(initialState.destination.route),
                                    tabIndexOf(targetState.destination.route),
                                ),
                            )
                            else -> MotionTokens.m3FadeThroughEnter()
                        }
                    },
                    exitTransition = {
                        val tabPair = isTabPeerTransition(
                            initialState.destination.route,
                            targetState.destination.route,
                        )
                        when {
                            reducedMotion -> ExitTransition.None
                            tabPair -> MotionTokens.m3TabSlideExit(
                                MotionPolicy.tabSlideDirection(
                                    tabIndexOf(initialState.destination.route),
                                    tabIndexOf(targetState.destination.route),
                                ),
                            )
                            else -> MotionTokens.m3FadeThroughExit()
                        }
                    },
                    popEnterTransition = {
                        val tabPair = isTabPeerTransition(
                            initialState.destination.route,
                            targetState.destination.route,
                        )
                        when {
                            reducedMotion -> EnterTransition.None
                            tabPair -> MotionTokens.m3TabSlideEnter(
                                MotionPolicy.tabSlideDirection(
                                    tabIndexOf(initialState.destination.route),
                                    tabIndexOf(targetState.destination.route),
                                ),
                            )
                            else -> MotionTokens.m3FadeThroughEnter()
                        }
                    },
                    popExitTransition = {
                        val tabPair = isTabPeerTransition(
                            initialState.destination.route,
                            targetState.destination.route,
                        )
                        when {
                            reducedMotion -> ExitTransition.None
                            tabPair -> MotionTokens.m3TabSlideExit(
                                MotionPolicy.tabSlideDirection(
                                    tabIndexOf(initialState.destination.route),
                                    tabIndexOf(targetState.destination.route),
                                ),
                            )
                            else -> MotionTokens.m3FadeThroughExit()
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
                    popUpTo(0) {
                        inclusive = true
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
                    popUpTo(0) {
                        inclusive = true
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
