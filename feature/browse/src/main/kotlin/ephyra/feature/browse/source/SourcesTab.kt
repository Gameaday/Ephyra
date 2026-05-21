package ephyra.feature.browse.source

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.feature.browse.presentation.SourceOptionsDialog
import ephyra.feature.browse.presentation.SourcesScreen
import ephyra.presentation.core.components.AppBar
import ephyra.presentation.core.components.TabContent
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun sourcesTab(navController: NavController = LocalNavController.current): TabContent {
    val screenModel = hiltViewModel<SourcesScreenModel>()
    val state by screenModel.state.collectAsStateWithLifecycle()

    return TabContent(
        titleRes = ephyra.app.core.common.R.string.label_content_sources,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(ephyra.app.core.common.R.string.action_global_search),
                icon = Icons.Outlined.TravelExplore,
                onClick = { navController.navigate(ScreenRoutes.GlobalSearch.createRoute(null)) },
            ),
            AppBar.Action(
                title = stringResource(ephyra.app.core.common.R.string.action_filter),
                icon = Icons.Outlined.FilterList,
                onClick = { navController.navigate(ScreenRoutes.SourcesFilter.route) },
            ),
        ),
        content = { contentPadding, snackbarHostState ->
            SourcesScreen(
                state = state,
                contentPadding = contentPadding,
                onClickItem = { source, listing ->
                    navController.navigate(ScreenRoutes.BrowseSource.createRoute(source.id, listing.query))
                },
                onClickPin = { screenModel.onEvent(SourcesScreenEvent.TogglePin(it)) },
                onLongClickItem = { screenModel.onEvent(SourcesScreenEvent.ShowSourceDialog(it)) },
            )

            state.dialog?.let { dialog ->
                val source = dialog.source
                SourceOptionsDialog(
                    source = source,
                    onClickPin = {
                        screenModel.onEvent(SourcesScreenEvent.TogglePin(source))
                        screenModel.onEvent(SourcesScreenEvent.CloseDialog)
                    },
                    onClickDisable = {
                        screenModel.onEvent(SourcesScreenEvent.ToggleSource(source))
                        screenModel.onEvent(SourcesScreenEvent.CloseDialog)
                    },
                    onDismiss = { screenModel.onEvent(SourcesScreenEvent.CloseDialog) },
                )
            }

            val internalErrString = stringResource(ephyra.app.core.common.R.string.internal_error)
            LaunchedEffect(Unit) {
                screenModel.events.collectLatest { event ->
                    when (event) {
                        SourcesScreenModel.Event.FailedFetchingSources -> {
                            launch { snackbarHostState.showSnackbar(internalErrString) }
                        }
                    }
                }
            }
        },
    )
}
