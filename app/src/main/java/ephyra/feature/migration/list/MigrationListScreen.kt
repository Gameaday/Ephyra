package ephyra.feature.migration.list

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ephyra.feature.migration.list.components.MigrationExitDialog
import ephyra.feature.migration.list.components.MigrationMangaDialog
import ephyra.feature.migration.list.components.MigrationProgressDialog
import ephyra.presentation.core.util.system.toast
import androidx.navigation.NavController
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.ScreenRoutes

@Composable
fun MigrationListScreen(
    mangaIds: Collection<Long>,
    extraSearchQuery: String?,
    navController: NavController = LocalNavController.current,
) {
    val screenModel = hiltViewModel<MigrationListScreenModel>()
    LaunchedEffect(mangaIds, extraSearchQuery) {
        screenModel.init(mangaIds, extraSearchQuery)
    }
    val state by screenModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val matchOverride by navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow<Pair<Long, Long>?>("match_override", null)
        ?.collectAsStateWithLifecycle() ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(null) }

    LaunchedEffect(matchOverride) {
        val (current, target) = matchOverride ?: return@LaunchedEffect
        screenModel.onEvent(MigrationListScreenEvent.UseMangaForMigration(current, target))
        navController.currentBackStackEntry?.savedStateHandle?.remove<Pair<Long, Long>>("match_override")
    }

    LaunchedEffect(screenModel) {
        screenModel.navigateBackEvent.collect { navController.popBackStack() }
    }

    LaunchedEffect(screenModel) {
        screenModel.missingChaptersEvent.collect {
            context.toast(ephyra.app.core.common.R.string.migrationListScreen_matchWithoutChapterToast, Toast.LENGTH_LONG)
        }
    }

    MigrationListScreenContent(
        items = state.items,
        migrationComplete = state.migrationComplete,
        finishedCount = state.finishedCount,
        onItemClick = {
            navController.navigate(ScreenRoutes.MangaDetails.createRoute(it.id, true))
        },
        onSearchManually = { migrationItem ->
            navController.navigate(ScreenRoutes.MigrateSearch.createRoute(migrationItem.manga.id))
        },
        onSkip = { screenModel.onEvent(MigrationListScreenEvent.RemoveManga(it)) },
        onMigrate = { screenModel.onEvent(MigrationListScreenEvent.MigrateNow(it, replace = true)) },
        onCopy = { screenModel.onEvent(MigrationListScreenEvent.MigrateNow(it, replace = false)) },
        openMigrationDialog = { copy -> screenModel.onEvent(MigrationListScreenEvent.ShowMigrateDialog(copy)) },
    )

    when (val dialog = state.dialog) {
        is MigrationListScreenModel.Dialog.Migrate -> {
            MigrationMangaDialog(
                onDismissRequest = { screenModel.onEvent(MigrationListScreenEvent.DismissDialog) },
                copy = dialog.copy,
                totalCount = dialog.totalCount,
                skippedCount = dialog.skippedCount,
                onMigrate = {
                    if (dialog.copy) {
                        screenModel.onEvent(MigrationListScreenEvent.CopyMangas)
                    } else {
                        screenModel.onEvent(MigrationListScreenEvent.MigrateMangas)
                    }
                },
            )
        }
        is MigrationListScreenModel.Dialog.Progress -> {
            MigrationProgressDialog(
                progress = dialog.progress,
                exitMigration = { screenModel.onEvent(MigrationListScreenEvent.CancelMigrate) },
            )
        }
        MigrationListScreenModel.Dialog.Exit -> {
            MigrationExitDialog(
                onDismissRequest = { screenModel.onEvent(MigrationListScreenEvent.DismissDialog) },
                exitMigration = { navController.popBackStack() },
            )
        }
        null -> Unit
    }

    BackHandler(true) {
        screenModel.onEvent(MigrationListScreenEvent.ShowExitDialog)
    }
}
