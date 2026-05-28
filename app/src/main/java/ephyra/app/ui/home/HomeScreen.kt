package ephyra.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
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
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.NavigationEvents
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

@Composable
fun HomeScreen(
    externalNavController: NavHostController = LocalNavController.current,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val bottomNavController = rememberNavController()
    val tabs = listOf(
        HomeTab.Library,
        HomeTab.Updates,
        HomeTab.History,
        HomeTab.Browse,
        HomeTab.More,
    )

    val incognito by viewModel.incognito.collectAsStateWithLifecycle()
    val downloadOnly by viewModel.downloadOnly.collectAsStateWithLifecycle()
    val indexing by viewModel.indexing.collectAsStateWithLifecycle()

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
                    downloadedOnlyMode = downloadOnly,
                    incognitoMode = incognito,
                    indexing = indexing,
                )
            },
            startBar = {
                if (isTabletUi()) {
                    NavigationRail {
                        tabs.forEach {
                            HomeNavigationRailItem(it, bottomNavController, viewModel)
                        }
                    }
                }
            },
            bottomBar = {
                if (!isTabletUi()) {
                    // TODO: communicate visibility from screens
                    val bottomNavVisible = true
                    AnimatedVisibility(
                        visible = bottomNavVisible,
                        enter = expandVertically(
                            animationSpec = tween(
                                durationMillis = MotionTokens.DURATION_MEDIUM,
                                easing = MotionTokens.EasingDecelerate,
                            ),
                        ),
                        exit = shrinkVertically(
                            animationSpec = tween(
                                durationMillis = MotionTokens.DURATION_SHORT,
                                easing = MotionTokens.EasingAccelerate,
                            ),
                        ),
                    ) {
                        NavigationBar {
                            tabs.forEach {
                                HomeNavigationBarItem(it, bottomNavController, viewModel)
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
                NavHost(
                    navController = bottomNavController,
                    startDestination = ScreenRoutes.Library.route,
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
    viewModel: HomeViewModel,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true

    NavigationBarItem(
        selected = selected,
        onClick = {
            if (selected) {
                NavigationEvents.triggerReselect(tab.route)
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
        icon = { HomeTabIcon(tab, selected, viewModel) },
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
    viewModel: HomeViewModel,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true

    NavigationRailItem(
        selected = selected,
        onClick = {
            if (selected) {
                NavigationEvents.triggerReselect(tab.route)
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
        icon = { HomeTabIcon(tab, selected, viewModel) },
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
    viewModel: HomeViewModel,
) {
    BadgedBox(
        badge = {
            if (tab == HomeTab.Updates) {
                val count by viewModel.updatesBadgeCount.collectAsStateWithLifecycle()
                if (count > 0) {
                    Badge {
                        val desc = pluralStringResource(
                            ephyra.app.core.common.R.plurals.notification_chapters_generic,
                            count = count,
                            count,
                        )
                        Text(
                            text = count.toString(),
                            modifier = Modifier.semantics { contentDescription = desc },
                        )
                    }
                }
            }
            if (tab == HomeTab.Browse) {
                val count by viewModel.extensionsBadgeCount.collectAsStateWithLifecycle()
                if (count > 0) {
                    Badge {
                        val desc = pluralStringResource(
                            ephyra.app.core.common.R.plurals.update_check_notification_ext_updates,
                            count = count,
                            count,
                        )
                        Text(
                            text = count.toString(),
                            modifier = Modifier.semantics { contentDescription = desc },
                        )
                    }
                }
            }
        },
    ) {
        Icon(
            painter = painterResource(if (selected) tab.iconSelectedRes else tab.iconRes),
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
