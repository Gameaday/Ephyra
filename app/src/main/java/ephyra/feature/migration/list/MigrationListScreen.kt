package ephyra.feature.migration.list

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.feature.migration.list.components.MigrationExitDialog
import ephyra.feature.migration.list.components.MigrationMangaDialog
import ephyra.feature.migration.list.components.MigrationProgressDialog
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.Screen
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import ephyra.presentation.core.util.system.toast

@Composable
fun MigrationListScreen(
    mangaIds: Collection<Long>,
    extraSearchQuery: String?,
    navController: NavController = LocalNavController.current,
) {
    val viewModel = hiltViewModel<MigrationListViewModel>()
    LaunchedEffect(mangaIds, extraSearchQuery) {
        viewModel.init(mangaIds, extraSearchQuery)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val matchOverride by navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow<Pair<Long, Long>?>("match_override", null)
        ?.collectAsStateWithLifecycle()
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(null) }

    LaunchedEffect(matchOverride) {
        val (current, target) = matchOverride ?: return@LaunchedEffect
        viewModel.onEvent(MigrationListScreenEvent.UseMangaForMigration(current, target))
        navController.currentBackStackEntry?.savedStateHandle?.remove<Pair<Long, Long>>("match_override")
    }

    LaunchedEffect(viewModel) {
        viewModel.navigateBackEvent.collect { navController.popBackStack() }
    }

    LaunchedEffect(viewModel) {
        viewModel.missingChaptersEvent.collect {
            context.toast(
                ephyra.app.core.common.R.string.migrationListScreen_matchWithoutChapterToast,
                Toast.LENGTH_LONG,
            )
        }
    }

    MigrationListScreenContent(
        items = state.items,
        migrationComplete = state.migrationComplete,
        finishedCount = state.finishedCount,
        onItemClick = {
            navController.navigate(Screen.MangaDetails(it.id, true))
        },
        onSearchManually = { migrationItem ->
            navController.navigate(ScreenRoutes.MigrateSearch.createRoute(migrationItem.manga.id))
        },
        onSkip = { viewModel.onEvent(MigrationListScreenEvent.RemoveManga(it)) },
        onMigrate = { viewModel.onEvent(MigrationListScreenEvent.MigrateNow(it, replace = true)) },
        onCopy = { viewModel.onEvent(MigrationListScreenEvent.MigrateNow(it, replace = false)) },
        openMigrationDialog = { copy -> viewModel.onEvent(MigrationListScreenEvent.ShowMigrateDialog(copy)) },
    )

    when (val dialog = state.dialog) {
        is MigrationListViewModel.Dialog.Migrate -> {
            MigrationMangaDialog(
                onDismissRequest = { viewModel.onEvent(MigrationListScreenEvent.DismissDialog) },
                copy = dialog.copy,
                totalCount = dialog.totalCount,
                skippedCount = dialog.skippedCount,
                onMigrate = {
                    if (dialog.copy) {
                        viewModel.onEvent(MigrationListScreenEvent.CopyMangas)
                    } else {
                        viewModel.onEvent(MigrationListScreenEvent.MigrateMangas)
                    }
                },
            )
        }
        is MigrationListViewModel.Dialog.Progress -> {
            MigrationProgressDialog(
                progress = dialog.progress,
                exitMigration = { viewModel.onEvent(MigrationListScreenEvent.CancelMigrate) },
            )
        }
        MigrationListViewModel.Dialog.Exit -> {
            MigrationExitDialog(
                onDismissRequest = { viewModel.onEvent(MigrationListScreenEvent.DismissDialog) },
                exitMigration = { navController.popBackStack() },
            )
        }
        null -> Unit
    }

    BackHandler(true) {
        viewModel.onEvent(MigrationListScreenEvent.ShowExitDialog)
    }
}
