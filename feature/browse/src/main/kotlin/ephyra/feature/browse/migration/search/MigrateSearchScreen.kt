package ephyra.feature.browse.migration.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ephyra.feature.browse.presentation.MigrateSearchScreen
import ephyra.presentation.util.Screen
import ephyra.feature.browse.source.globalsearch.SearchScreenModel
import ephyra.feature.browse.source.globalsearch.SearchScreenEvent
import ephyra.feature.manga.MangaScreen
import ephyra.feature.migration.dialog.MigrateMangaDialog
import ephyra.presentation.core.ui.MigrationListPresenter

class MigrateSearchScreen(private val mangaId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = hiltViewModel<MigrateSearchScreenModel>()
        LaunchedEffect(mangaId) {
            screenModel.init(mangaId)
        }
        val state by screenModel.state.collectAsStateWithLifecycle()

        MigrateSearchScreen(
            state = state,
            fromSourceId = state.from?.source,
            navigateUp = navigator::pop,
            onChangeSearchQuery = { screenModel.onEvent(SearchScreenEvent.UpdateSearchQuery(it)) },
            onSearch = { screenModel.onEvent(SearchScreenEvent.Search) },
            getManga = { screenModel.getManga(it) },
            onChangeSearchFilter = { screenModel.onEvent(SearchScreenEvent.SetSourceFilter(it)) },
            onToggleResults = { screenModel.onEvent(SearchScreenEvent.ToggleFilterResults) },
            onClickSource = { navigator.push(MigrateSourceSearchScreen(state.from!!, it.id, state.searchQuery)) },
            onClickItem = {
                val migrateListScreen = navigator.items
                    .filterIsInstance<MigrationListPresenter>()
                    .lastOrNull()

                if (migrateListScreen == null) {
                    screenModel.onEvent(SearchScreenEvent.SetMigrateDialog(mangaId, it))
                } else {
                    migrateListScreen.addMatchOverride(current = mangaId, target = it.id)
                    navigator.popUntil { screen -> screen is MigrationListPresenter }
                }
            },
            onLongClickItem = { navigator.push(MangaScreen(it.id, true)) },
        )

        when (val dialog = state.dialog) {
            is SearchScreenModel.Dialog.Migrate -> {
                MigrateMangaDialog(
                    current = dialog.current,
                    target = dialog.target,
                    // Initiated from the context of [dialog.current] so we show [dialog.target].
                    onClickTitle = { navigator.push(MangaScreen(dialog.target.id, true)) },
                    onDismissRequest = { screenModel.onEvent(SearchScreenEvent.ClearDialog) },
                    onComplete = {
                        if (navigator.lastItem is MangaScreen) {
                            val lastItem = navigator.lastItem
                            navigator.popUntil { navigator.items.contains(lastItem) }
                            navigator.push(MangaScreen(dialog.target.id))
                        } else {
                            navigator.replace(MangaScreen(dialog.target.id))
                        }
                    },
                )
            }
            else -> {}
        }
    }
}
