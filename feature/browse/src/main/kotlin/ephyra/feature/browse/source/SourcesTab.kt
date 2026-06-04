package ephyra.feature.browse.source

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.feature.browse.presentation.SourceOptionsDialog
import ephyra.feature.browse.presentation.SourcesScreen
import ephyra.presentation.core.components.AppBar
import ephyra.presentation.core.components.TabContent
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.Screen
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun sourcesTab(
    ViewModel: SourcesViewModel,
    navController: NavController = LocalNavController.current,
): TabContent {
    val state by ViewModel.state.collectAsStateWithLifecycle()

    return TabContent(
        titleRes = ephyra.app.core.common.R.string.label_content_sources,
        searchEnabled = true,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(ephyra.app.core.common.R.string.action_global_search),
                icon = Icons.Outlined.TravelExplore,
                onClick = { navController.navigate(Screen.GlobalSearch(null)) },
            ),
            AppBar.Action(
                title = stringResource(ephyra.app.core.common.R.string.action_filter),
                icon = Icons.Outlined.FilterList,
                onClick = { navController.navigate(ScreenRoutes.SourcesFilter.route) },
            ),
            AppBar.Action(
                title = "Content Sourcing Hub",
                icon = Icons.Outlined.CloudSync,
                onClick = { navController.navigate(ScreenRoutes.ContentSourcing.route) },
            ),
        ),
        content = { contentPadding, snackbarHostState ->
            BackHandler(enabled = state.searchQuery != null) {
                ViewModel.search(null)
            }

            SourcesScreen(
                state = state,
                contentPadding = contentPadding,
                onClickItem = { source, listing ->
                    navController.navigate(Screen.BrowseSource(source.id, listing.query))
                },
                onClickPin = { ViewModel.onEvent(SourcesScreenEvent.TogglePin(it)) },
                onLongClickItem = { ViewModel.onEvent(SourcesScreenEvent.ShowSourceDialog(it)) },
            )

            state.dialog?.let { dialog ->
                val source = dialog.source
                SourceOptionsDialog(
                    source = source,
                    onClickPin = {
                        ViewModel.onEvent(SourcesScreenEvent.TogglePin(source))
                        ViewModel.onEvent(SourcesScreenEvent.CloseDialog)
                    },
                    onClickDisable = {
                        ViewModel.onEvent(SourcesScreenEvent.ToggleSource(source))
                        ViewModel.onEvent(SourcesScreenEvent.CloseDialog)
                    },
                    onDismiss = { ViewModel.onEvent(SourcesScreenEvent.CloseDialog) },
                )
            }

            val internalErrString = stringResource(ephyra.app.core.common.R.string.internal_error)
            LaunchedEffect(Unit) {
                ViewModel.events.collectLatest { event ->
                    when (event) {
                        SourcesViewModel.Event.FailedFetchingSources -> {
                            launch { snackbarHostState.showSnackbar(internalErrString) }
                        }
                    }
                }
            }
        },
    )
}
