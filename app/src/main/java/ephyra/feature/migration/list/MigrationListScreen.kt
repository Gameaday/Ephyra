package ephyra.feature.migration.list

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ephyra.feature.browse.migration.search.MigrateSearchScreen
import ephyra.feature.manga.MangaScreen
import ephyra.feature.migration.list.components.MigrationExitDialog
import ephyra.feature.migration.list.components.MigrationMangaDialog
import ephyra.feature.migration.list.components.MigrationProgressDialog
import ephyra.i18n.MR
import ephyra.presentation.core.ui.MigrationListPresenter
import ephyra.presentation.core.util.Screen
import ephyra.presentation.core.util.system.toast
import org.koin.core.parameter.parametersOf

class MigrationListScreen(
    private val mangaIds: Collection<Long>,
    private val extraSearchQuery: String?,
) : Screen(), MigrationListPresenter {

    private var matchOverride: Pair<Long, Long>? = null

    override fun addMatchOverride(current: Long, target: Long) {
        matchOverride = current to target
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<MigrationListScreenModel> { parametersOf(mangaIds, extraSearchQuery) }
        val state by screenModel.state.collectAsStateWithLifecycle()
        val context = LocalContext.current

        LaunchedEffect(matchOverride) {
            val (current, target) = matchOverride ?: return@LaunchedEffect
            screenModel.onEvent(MigrationListScreenEvent.UseMangaForMigration(current, target))
            matchOverride = null
        }

        LaunchedEffect(screenModel) {
            screenModel.navigateBackEvent.collect { navigator.pop() }
        }

        LaunchedEffect(screenModel) {
            screenModel.missingChaptersEvent.collect {
                context.toast(MR.strings.migrationListScreen_matchWithoutChapterToast, Toast.LENGTH_LONG)
            }
        }

        MigrationListScreenContent(
            items = state.items,
            migrationComplete = state.migrationComplete,
            finishedCount = state.finishedCount,
            onItemClick = {
                navigator.push(MangaScreen(it.id, true))
            },
            onSearchManually = { migrationItem ->
                navigator push MigrateSearchScreen(migrationItem.manga.id)
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
                    exitMigration = navigator::pop,
                )
            }
            null -> Unit
        }

        BackHandler(true) {
            screenModel.onEvent(MigrationListScreenEvent.ShowExitDialog)
        }
    }
}
