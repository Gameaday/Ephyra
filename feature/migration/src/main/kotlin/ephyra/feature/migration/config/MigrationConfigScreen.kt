package ephyra.feature.migration.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.core.common.util.system.LocaleHelper
import ephyra.presentation.core.components.AppBar
import ephyra.presentation.core.components.AppBarActions
import ephyra.presentation.core.components.FastScrollLazyColumn
import ephyra.presentation.core.components.Pill
import ephyra.presentation.core.components.SourceIcon
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.components.material.padding
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import ephyra.presentation.core.util.shouldExpandFAB
import kotlinx.collections.immutable.persistentListOf
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun MigrationConfigScreen(
    mangaIds: Collection<Long>,
    navController: NavController = LocalNavController.current,
) {
    val viewModel = hiltViewModel<MigrationConfigViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    var migrationSheetOpen by rememberSaveable { mutableStateOf(false) }

    fun continueMigration(openSheet: Boolean, extraSearchQuery: String?) {
        val mangaId = mangaIds.singleOrNull()
        if (mangaId == null && openSheet) {
            migrationSheetOpen = true
            return
        }
        if (mangaId == null) {
            navController.navigate(ScreenRoutes.MigrationList.createRoute(mangaIds, extraSearchQuery)) {
                popUpTo(ScreenRoutes.MigrationConfig.route) { inclusive = true }
            }
        } else {
            navController.navigate(ScreenRoutes.MigrateSearch.createRoute(mangaId)) {
                popUpTo(ScreenRoutes.MigrationConfig.route) { inclusive = true }
            }
        }
    }

    if (state.isLoading) {
        LoadingScreen()
        return
    }

    val (selectedSources, availableSources) = state.sources.partition { it.isSelected }
    val showLanguage by remember(state) {
        derivedStateOf {
            state.sources.distinctBy { it.source.lang }.size > 1
        }
    }

    val lazyListState = rememberLazyListState()
    Scaffold(
        topBar = {
            AppBar(
                title = null,
                navigateUp = { navController.popBackStack() },
                scrollBehavior = it,
                actions = {
                    AppBarActions(
                        persistentListOf(
                            AppBar.Action(
                                title = stringResource(
                                    ephyra.app.core.common.R.string.migrationConfigScreen_selectAllLabel,
                                ),
                                icon = Icons.Outlined.SelectAll,
                                onClick = {
                                    viewModel.onEvent(
                                        MigrationConfigEvent.ToggleSelectionConfig(
                                            MigrationConfigViewModel.SelectionConfig.All,
                                        ),
                                    )
                                },
                            ),
                            AppBar.Action(
                                title = stringResource(
                                    ephyra.app.core.common.R.string.migrationConfigScreen_selectNoneLabel,
                                ),
                                icon = Icons.Outlined.Deselect,
                                onClick = {
                                    viewModel.onEvent(
                                        MigrationConfigEvent.ToggleSelectionConfig(
                                            MigrationConfigViewModel.SelectionConfig.None,
                                        ),
                                    )
                                },
                            ),
                            AppBar.OverflowAction(
                                title = stringResource(
                                    ephyra.app.core.common.R.string.migrationConfigScreen_selectEnabledLabel,
                                ),
                                onClick = {
                                    viewModel.onEvent(
                                        MigrationConfigEvent.ToggleSelectionConfig(
                                            MigrationConfigViewModel.SelectionConfig.Enabled,
                                        ),
                                    )
                                },
                            ),
                            AppBar.OverflowAction(
                                title = stringResource(
                                    ephyra.app.core.common.R.string.migrationConfigScreen_selectPinnedLabel,
                                ),
                                onClick = {
                                    viewModel.onEvent(
                                        MigrationConfigEvent.ToggleSelectionConfig(
                                            MigrationConfigViewModel.SelectionConfig.Pinned,
                                        ),
                                    )
                                },
                            ),
                        ),
                    )
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = {
                    Text(
                        text = stringResource(ephyra.app.core.common.R.string.migrationConfigScreen_continueButtonText),
                    )
                },
                icon = { Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null) },
                onClick = {
                    viewModel.onEvent(MigrationConfigEvent.SaveSources)
                    continueMigration(openSheet = true, extraSearchQuery = null)
                },
                expanded = lazyListState.shouldExpandFAB(),
            )
        },
    ) { contentPadding ->
        val reorderableState = rememberReorderableLazyListState(lazyListState, contentPadding) { from, to ->
            val fromIndex = selectedSources.indexOfFirst { it.id == from.key }
            val toIndex = selectedSources.indexOfFirst { it.id == to.key }
            if (fromIndex == -1 || toIndex == -1) return@rememberReorderableLazyListState
            viewModel.onEvent(MigrationConfigEvent.OrderSource(fromIndex, toIndex))
        }

        FastScrollLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = lazyListState,
            contentPadding = contentPadding,
        ) {
            listOf(selectedSources, availableSources).fastForEachIndexed { listIndex, sources ->
                val selectedSourceList = listIndex == 0
                if (sources.isNotEmpty()) {
                    val headerPrefix = if (selectedSourceList) "selected" else "available"
                    item("$headerPrefix-header") {
                        Text(
                            text = stringResource(
                                resource = if (selectedSourceList) {
                                    ephyra.app.core.common.R.string.migrationConfigScreen_selectedHeader
                                } else {
                                    ephyra.app.core.common.R.string.migrationConfigScreen_availableHeader
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .padding(MaterialTheme.padding.medium)
                                .animateItem(),
                        )
                    }
                }
                itemsIndexed(
                    items = sources,
                    key = { _, item -> item.id },
                ) { index, item ->
                    SourceItemContainer(
                        firstItem = index == 0,
                        lastItem = index == (sources.size - 1),
                        source = item,
                        showLanguage = showLanguage,
                        dragEnabled = selectedSourceList && sources.size > 1,
                        state = reorderableState,
                        key = { if (selectedSourceList) it.id else "available-${it.id}" },
                        onClick = { viewModel.onEvent(MigrationConfigEvent.ToggleSelection(item.id)) },
                    )
                }
            }
        }
    }

    if (migrationSheetOpen) {
        MigrationConfigScreenSheet(
            preferences = viewModel.sourcePreferences,
            onDismissRequest = { migrationSheetOpen = false },
            onStartMigration = { extraSearchQuery ->
                migrationSheetOpen = false
                continueMigration(openSheet = false, extraSearchQuery = extraSearchQuery)
            },
        )
    }
}

@Composable
private fun LazyItemScope.SourceItemContainer(
    firstItem: Boolean,
    lastItem: Boolean,
    source: MigrationSource,
    showLanguage: Boolean,
    dragEnabled: Boolean,
    state: ReorderableLazyListState,
    key: (MigrationSource) -> Any,
    onClick: () -> Unit,
) {
    val shape = remember(firstItem, lastItem) {
        val top = if (firstItem) 12.dp else 0.dp
        val bottom = if (lastItem) 12.dp else 0.dp
        RoundedCornerShape(top, top, bottom, bottom)
    }

    ReorderableItem(
        state = state,
        key = key(source),
        enabled = dragEnabled,
    ) { _ ->
        ElevatedCard(
            shape = shape,
            modifier = Modifier
                .padding(horizontal = MaterialTheme.padding.medium)
                .animateItem(),
        ) {
            SourceItem(
                source = source,
                showLanguage = showLanguage,
                dragEnabled = dragEnabled,
                scope = this@ReorderableItem,
                onClick = onClick,
            )
        }
    }

    if (!lastItem) {
        HorizontalDivider(modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium))
    }
}

@Composable
private fun SourceItem(
    source: MigrationSource,
    showLanguage: Boolean,
    dragEnabled: Boolean,
    scope: ReorderableCollectionItemScope,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SourceIcon(source = source.source)
                Text(
                    text = source.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (showLanguage) {
                    Pill(
                        text = LocaleHelper.getShortDisplayName(source.shortLanguage, uppercase = true),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        trailingContent = if (dragEnabled) {
            {
                Icon(
                    imageVector = Icons.Outlined.DragHandle,
                    contentDescription = null,
                    modifier = with(scope) {
                        Modifier.draggableHandle()
                    },
                )
            }
        } else {
            null
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
        ),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
