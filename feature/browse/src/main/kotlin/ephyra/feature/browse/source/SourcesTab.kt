package ephyra.feature.browse.source

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    var showAddWebSourceDialog by remember { mutableStateOf(false) }

    return TabContent(
        titleRes = ephyra.app.core.common.R.string.label_content_sources,
        searchEnabled = true,
        actions = persistentListOf(
            AppBar.Action(
                title = "Add Web Source",
                icon = Icons.Outlined.AddLink,
                onClick = { showAddWebSourceDialog = true },
            ),
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
            var webSourceUrl by remember { mutableStateOf("") }
            var webSourceName by remember { mutableStateOf("") }

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

            if (showAddWebSourceDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showAddWebSourceDialog = false
                        webSourceUrl = ""
                        webSourceName = ""
                    },
                    title = { Text("Add Web Source") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Enter any website URL to automatically discover " +
                                    "and extract manga content using the adaptive heuristic engine.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            OutlinedTextField(
                                value = webSourceUrl,
                                onValueChange = { webSourceUrl = it },
                                label = { Text("Base URL (e.g. https://mangadex.org)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = webSourceName,
                                onValueChange = { webSourceName = it },
                                label = { Text("Display Name (optional)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (webSourceUrl.isNotBlank()) {
                                    ViewModel.onEvent(
                                        SourcesScreenEvent.AddWebSource(
                                            url = webSourceUrl.trim(),
                                            name = webSourceName.trim().ifBlank { null },
                                        ),
                                    )
                                    showAddWebSourceDialog = false
                                    webSourceUrl = ""
                                    webSourceName = ""
                                }
                            },
                            enabled = webSourceUrl.isNotBlank(),
                        ) {
                            Text("Add Source")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showAddWebSourceDialog = false
                                webSourceUrl = ""
                                webSourceName = ""
                            },
                        ) {
                            Text("Cancel")
                        }
                    },
                )
            }

            val internalErrString = stringResource(ephyra.app.core.common.R.string.internal_error)
            LaunchedEffect(Unit) {
                ViewModel.effects.collectLatest { effect ->
                    when (effect) {
                        SourcesViewModel.Effect.FailedFetchingSources -> {
                            launch { snackbarHostState.showSnackbar(internalErrString) }
                        }
                        is SourcesViewModel.Effect.WebSourceAdded -> {
                            launch { snackbarHostState.showSnackbar("Added source: ${effect.name}") }
                        }
                        is SourcesViewModel.Effect.WebSourceAddFailed -> {
                            launch { snackbarHostState.showSnackbar("Failed to add source: ${effect.error}") }
                        }
                    }
                }
            }
        },
    )
}
