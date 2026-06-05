package ephyra.feature.browse.migration.search

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.core.common.Constants
import ephyra.domain.manga.model.Manga
import ephyra.feature.browse.presentation.BrowseSourceContent
import ephyra.feature.browse.source.browse.BrowseSourceScreenEvent
import ephyra.feature.browse.source.browse.BrowseSourceViewModel
import ephyra.feature.browse.source.browse.SourceFilterDialog
import ephyra.feature.migration.dialog.MigrateMangaDialog
import ephyra.presentation.core.components.SearchToolbar
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.Screen
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import ephyra.presentation.core.util.collectAsLazyPagingItems
import ephyra.presentation.core.util.ifSourcesLoaded
import ephyra.source.local.LocalSource
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.launch

@Composable
fun MigrateSourceSearchScreen(
    mangaId: Long,
    sourceId: Long,
    query: String?,
    navController: NavController = LocalNavController.current,
) {
    if (!ifSourcesLoaded()) {
        LoadingScreen()
        return
    }

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    val viewModel = hiltViewModel<BrowseSourceViewModel>()
    // We need to fetch currentManga
    val mangaModel = hiltViewModel<ephyra.feature.manga.MangaViewModel>()
    LaunchedEffect(mangaId) {
        mangaModel.init(mangaId, false)
    }
    val mangaState by mangaModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(sourceId, query) {
        viewModel.init(sourceId, query)
    }

    val source = viewModel.source
    if (source == null || mangaState is ephyra.feature.manga.MangaViewModel.State.Loading) {
        LoadingScreen()
        return
    }

    val currentManga = (mangaState as ephyra.feature.manga.MangaViewModel.State.Success).manga
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = { scrollBehavior ->
            SearchToolbar(
                searchQuery = state.toolbarQuery ?: "",
                onChangeSearchQuery = { viewModel.onEvent(BrowseSourceScreenEvent.SetToolbarQuery(it)) },
                onClickCloseSearch = { navController.popBackStack() },
                onSearch = { viewModel.onEvent(BrowseSourceScreenEvent.Search(it)) },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(text = stringResource(ephyra.app.core.common.R.string.action_filter)) },
                icon = { Icon(Icons.Outlined.FilterList, contentDescription = null) },
                onClick = { viewModel.onEvent(BrowseSourceScreenEvent.OpenFilterSheet) },
                modifier = Modifier.alpha(if (state.filters.isNotEmpty()) 1f else 0f),
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        val openMigrateDialog: (Manga) -> Unit = {
            val migrateListEntry = try {
                navController.getBackStackEntry(ScreenRoutes.MigrationList.route)
            } catch (e: Exception) {
                null
            }

            if (migrateListEntry == null) {
                viewModel.onEvent(
                    BrowseSourceScreenEvent.SetDialog(
                        BrowseSourceViewModel.Dialog.Migrate(target = it, current = currentManga),
                    ),
                )
            } else {
                migrateListEntry.savedStateHandle["match_override"] = currentManga.id to it.id
                navController.popBackStack(migrateListEntry.destination.id, inclusive = false)
            }
        }
        BrowseSourceContent(
            source = source,
            mangaList = viewModel.mangaPagerFlowFlow.collectAsLazyPagingItems(),
            columns = viewModel.getColumnsPreference(LocalConfiguration.current.orientation),
            displayMode = viewModel.displayMode,
            snackbarHostState = snackbarHostState,
            contentPadding = paddingValues,
            onWebViewClick = {
                val httpSource = source as? HttpSource ?: return@BrowseSourceContent
                navController.navigate(
                    Screen.WebView(httpSource.baseUrl, httpSource.name, httpSource.id),
                )
            },
            onHelpClick = { uriHandler.openUri(Constants.URL_HELP) },
            onLocalSourceHelpClick = { uriHandler.openUri(LocalSource.HELP_URL) },
            onMangaClick = openMigrateDialog,
            onMangaLongClick = {
                navController.navigate(Screen.MangaDetails(it.id, true))
            },
        )
    }

    val onDismissRequest = { viewModel.onEvent(BrowseSourceScreenEvent.SetDialog(null)) }
    when (val dialog = state.dialog) {
        is BrowseSourceViewModel.Dialog.Filter -> {
            SourceFilterDialog(
                onDismissRequest = onDismissRequest,
                filters = state.filters,
                onReset = { viewModel.onEvent(BrowseSourceScreenEvent.ResetFilters) },
                onFilter = { viewModel.onEvent(BrowseSourceScreenEvent.Search(filters = state.filters)) },
                onUpdate = { viewModel.onEvent(BrowseSourceScreenEvent.SetFilters(it)) },
            )
        }

        is BrowseSourceViewModel.Dialog.Migrate -> {
            MigrateMangaDialog(
                current = currentManga,
                target = dialog.target,
                onClickTitle = {
                    navController.navigate(Screen.MangaDetails(dialog.target.id, true))
                },
                onDismissRequest = onDismissRequest,
                onComplete = {
                    scope.launch {
                        navController.navigate(Screen.MangaDetails(dialog.target.id, true)) {
                            popUpTo(ScreenRoutes.Home.route) { inclusive = false }
                        }
                    }
                },
            )
        }

        else -> {}
    }
}
