package ephyra.feature.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ChromeReaderMode
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import ephyra.feature.settings.widget.TextPreferenceWidget
import ephyra.presentation.core.components.AppBar
import ephyra.presentation.core.components.AppBarActions
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import kotlinx.collections.immutable.persistentListOf

@Composable
fun SettingsMainScreen(
    twoPane: Boolean,
    navController: NavController = LocalNavController.current,
) {
    val containerColor = if (twoPane) getPalerSurface() else MaterialTheme.colorScheme.surface
    val topBarState = rememberTopAppBarState()

    Scaffold(
        topBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(topBarState),
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(ephyra.app.core.common.R.string.label_settings),
                navigateUp = { navController.popBackStack() },
                actions = {
                    AppBarActions(
                        persistentListOf(
                            AppBar.Action(
                                title = stringResource(ephyra.app.core.common.R.string.action_search),
                                icon = Icons.Outlined.Search,
                                onClick = { navController.navigate(ScreenRoutes.SettingsSearch.route) },
                            ),
                        ),
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = containerColor,
        content = { contentPadding ->
            val state = rememberLazyListState()
            val settingItems = getItems()
            val indexSelected = if (twoPane) {
                // TODO: handle selection in twoPane
                null
            } else {
                null
            }

            LazyColumn(
                state = state,
                contentPadding = contentPadding,
            ) {
                itemsIndexed(
                    items = settingItems,
                    key = { _, item -> item.titleRes },
                ) { index, item ->
                    val selected = indexSelected == index
                    var modifier: Modifier = Modifier
                    var contentColor = LocalContentColor.current
                    if (twoPane) {
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .clip(MaterialTheme.shapes.extraLarge)
                            .then(
                                if (selected) {
                                    Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                                } else {
                                    Modifier
                                },
                            )
                        if (selected) {
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    }
                    CompositionLocalProvider(LocalContentColor provides contentColor) {
                        TextPreferenceWidget(
                            modifier = modifier,
                            title = stringResource(item.titleRes),
                            subtitle = item.formatSubtitle(),
                            icon = item.icon,
                            onPreferenceClick = {
                                if (twoPane) {
                                    // TODO: handle twoPane navigation
                                } else {
                                    navController.navigate(item.route)
                                }
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun getPalerSurface(): Color {
    return MaterialTheme.colorScheme.surfaceContainerLow
}

private data class Item(
    val titleRes: Int,
    val subtitleRes: Int? = null,
    val formatSubtitle: @Composable () -> String? = { subtitleRes?.let { stringResource(it) } },
    val icon: ImageVector,
    val route: String,
)

@Composable
private fun getItems(): List<Item> = listOf(
    Item(
        titleRes = ephyra.app.core.common.R.string.pref_category_appearance,
        subtitleRes = ephyra.app.core.common.R.string.pref_appearance_summary,
        icon = Icons.Outlined.Palette,
        route = ScreenRoutes.SettingsAppearance.route,
    ),
    Item(
        titleRes = ephyra.app.core.common.R.string.pref_category_library,
        subtitleRes = ephyra.app.core.common.R.string.pref_library_summary,
        icon = Icons.Outlined.CollectionsBookmark,
        route = ScreenRoutes.SettingsLibrary.route,
    ),
    Item(
        titleRes = ephyra.app.core.common.R.string.pref_category_reader,
        subtitleRes = ephyra.app.core.common.R.string.pref_reader_summary,
        icon = Icons.AutoMirrored.Outlined.ChromeReaderMode,
        route = ScreenRoutes.SettingsReader.route,
    ),
    Item(
        titleRes = ephyra.app.core.common.R.string.pref_category_downloads,
        subtitleRes = ephyra.app.core.common.R.string.pref_downloads_summary,
        icon = Icons.Outlined.GetApp,
        route = ScreenRoutes.SettingsDownloads.route,
    ),
    Item(
        titleRes = ephyra.app.core.common.R.string.pref_category_tracking,
        subtitleRes = ephyra.app.core.common.R.string.pref_tracking_summary,
        icon = Icons.Outlined.Sync,
        route = ScreenRoutes.SettingsTracking.route,
    ),
    Item(
        titleRes = ephyra.app.core.common.R.string.label_discover,
        subtitleRes = ephyra.app.core.common.R.string.pref_browse_summary,
        icon = Icons.Outlined.Explore,
        route = ScreenRoutes.SettingsBrowse.route,
    ),
    Item(
        titleRes = ephyra.app.core.common.R.string.label_data_storage,
        subtitleRes = ephyra.app.core.common.R.string.pref_backup_summary,
        icon = Icons.Outlined.Storage,
        route = ScreenRoutes.SettingsData.route,
    ),
    Item(
        titleRes = ephyra.app.core.common.R.string.pref_category_security,
        subtitleRes = ephyra.app.core.common.R.string.pref_security_summary,
        icon = Icons.Outlined.Security,
        route = ScreenRoutes.SettingsSecurity.route,
    ),
    Item(
        titleRes = ephyra.app.core.common.R.string.pref_category_advanced,
        subtitleRes = ephyra.app.core.common.R.string.pref_advanced_summary,
        icon = Icons.Outlined.Code,
        route = ScreenRoutes.SettingsAdvanced.route,
    ),
    Item(
        titleRes = ephyra.app.core.common.R.string.pref_category_about,
        formatSubtitle = {
            "${stringResource(ephyra.app.core.common.R.string.app_name)} 1.0.0"
        },
        icon = Icons.Outlined.Info,
        route = ScreenRoutes.About.route,
    ),
)
