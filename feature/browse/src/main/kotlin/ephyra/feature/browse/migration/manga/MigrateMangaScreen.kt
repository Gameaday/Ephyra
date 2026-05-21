package ephyra.feature.browse.migration.manga

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import ephyra.presentation.components.AppBar
import ephyra.presentation.manga.components.BaseMangaListItem
import ephyra.presentation.core.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import ephyra.domain.manga.model.Manga
import ephyra.presentation.core.components.FastScrollLazyColumn
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.screens.EmptyScreen
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.core.util.selectedBackground
import ephyra.presentation.core.util.shouldExpandFAB

import androidx.navigation.NavController
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.ScreenRoutes

@Composable
fun MigrateMangaScreen(
    sourceId: Long,
    navController: NavController = LocalNavController.current,
) {
    val context = LocalContext.current
    val screenModel = hiltViewModel<MigrateMangaScreenModel>()

    LaunchedEffect(sourceId) {
        screenModel.init(sourceId)
    }

    val state by screenModel.state.collectAsStateWithLifecycle()

    if (state.isLoading) {
        LoadingScreen()
        return
    }

    BackHandler(enabled = state.selectionMode) {
        screenModel.onEvent(MigrateMangaScreenEvent.ClearSelection)
    }

    val lazyListState = rememberLazyListState()

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = state.source!!.name,
                navigateUp = {
                    if (state.selectionMode) {
                        screenModel.onEvent(MigrateMangaScreenEvent.ClearSelection)
                    } else {
                        navController.popBackStack()
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            SmallExtendedFloatingActionButton(
                text = { Text(text = stringResource(ephyra.app.core.common.R.string.migrationConfigScreen_continueButtonText)) },
                icon = {
                    Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
                },
                onClick = {
                    val selection = state.selection
                    screenModel.onEvent(MigrateMangaScreenEvent.ClearSelection)
                    navController.navigate(ScreenRoutes.MigrationConfig.createRoute(selection))
                },
                expanded = lazyListState.shouldExpandFAB(),
                modifier = Modifier.animateFloatingActionButton(
                    visible = state.selectionMode,
                    alignment = Alignment.BottomEnd,
                ),
            )
        },
    ) { contentPadding ->
        if (state.isEmpty) {
            EmptyScreen(
                stringRes = ephyra.app.core.common.R.string.empty_screen,
                modifier = Modifier.padding(contentPadding),
            )
            return@Scaffold
        }

        MigrateMangaContent(
            lazyListState = lazyListState,
            contentPadding = contentPadding,
            state = state,
            onClickItem = { screenModel.onEvent(MigrateMangaScreenEvent.ToggleSelection(it)) },
            onClickCover = { navController.navigate(ScreenRoutes.MangaDetails.createRoute(it.id, true)) },
        )
    }

    LaunchedEffect(Unit) {
        screenModel.events.collectLatest { event ->
            when (event) {
                MigrationMangaEvent.FailedFetchingFavorites -> {
                    context.toast(ephyra.app.core.common.R.string.internal_error)
                }
            }
        }
    }
}

@Composable
private fun MigrateMangaContent(
        lazyListState: LazyListState,
        contentPadding: PaddingValues,
        state: MigrateMangaScreenModel.State,
        onClickItem: (Manga) -> Unit,
        onClickCover: (Manga) -> Unit,
    ) {
        FastScrollLazyColumn(
            state = lazyListState,
            contentPadding = contentPadding,
        ) {
            items(state.titles, key = { it.id }) { manga ->
                MigrateMangaItem(
                    manga = manga,
                    isSelected = manga.id in state.selection,
                    onClickItem = onClickItem,
                    onClickCover = onClickCover,
                )
            }
        }
    }

    @Composable
    private fun MigrateMangaItem(
        manga: Manga,
        isSelected: Boolean,
        onClickItem: (Manga) -> Unit,
        onClickCover: (Manga) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        BaseMangaListItem(
            modifier = modifier.selectedBackground(isSelected),
            manga = manga,
            onClickItem = { onClickItem(manga) },
            onClickCover = { onClickCover(manga) },
        )
    }
