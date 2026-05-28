package ephyra.feature.browse.source.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.core.common.Constants
import ephyra.core.common.util.lang.launchIO
import ephyra.domain.source.model.StubSource
import ephyra.feature.browse.presentation.BrowseSourceContent
import ephyra.feature.browse.presentation.MissingSourceScreen
import ephyra.feature.browse.presentation.components.BrowseSourceToolbar
import ephyra.feature.browse.presentation.components.RemoveMangaDialog
import ephyra.feature.browse.source.browse.BrowseSourceScreenModel.Listing
import ephyra.feature.category.presentation.components.ChangeCategoryDialog
import ephyra.feature.manga.presentation.DuplicateMangaDialog
import ephyra.feature.migration.dialog.MigrateMangaDialog
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.components.material.padding
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.Screen
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import ephyra.presentation.core.util.collectAsLazyPagingItems
import ephyra.presentation.core.util.ifSourcesLoaded
import ephyra.source.local.LocalSource
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow

@Composable
fun BrowseSourceScreen(
    sourceId: Long,
    listingQuery: String?,
    navController: NavController = LocalNavController.current,
) {
    if (!ifSourcesLoaded()) {
        LoadingScreen()
        return
    }

    val screenModel = hiltViewModel<BrowseSourceScreenModel>()
    LaunchedEffect(sourceId, listingQuery) {
        screenModel.init(sourceId, listingQuery)
    }

    val source = screenModel.source
    if (source == null) {
        LoadingScreen()
        return
    }

    val state by screenModel.state.collectAsStateWithLifecycle()

    val navigateUp: () -> Unit = {
        when {
            !state.isUserQuery && state.toolbarQuery != null ->
                screenModel.onEvent(BrowseSourceScreenEvent.SetToolbarQuery(null))
            else -> navController.popBackStack()
        }
    }

    if (source is StubSource) {
        MissingSourceScreen(
            source = source,
            navigateUp = navigateUp,
        )
        return
    }

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }

    val onHelpClick = { uriHandler.openUri(LocalSource.HELP_URL) }
    val onWebViewClick = f@{
        val httpSource = source as? HttpSource ?: return@f
        navController.navigate(
            Screen.WebView(
                url = httpSource.baseUrl,
                title = httpSource.name,
                sourceId = httpSource.id,
            ),
        )
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .pointerInput(Unit) {},
            ) {
                BrowseSourceToolbar(
                    searchQuery = state.toolbarQuery,
                    onSearchQueryChange = { screenModel.onEvent(BrowseSourceScreenEvent.SetToolbarQuery(it)) },
                    source = source,
                    displayMode = screenModel.displayMode,
                    onDisplayModeChange = { screenModel.displayMode = it },
                    navigateUp = navigateUp,
                    onWebViewClick = onWebViewClick,
                    onHelpClick = onHelpClick,
                    onSettingsClick = {
                        /* TODO: navController.navigate(ScreenRoutes.SourcePreferences.createRoute(sourceId)) */
                    },
                    onSearch = { screenModel.onEvent(BrowseSourceScreenEvent.Search(it)) },
                )

                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = MaterialTheme.padding.small),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    FilterChip(
                        selected = state.listing == Listing.Popular,
                        onClick = {
                            screenModel.onEvent(BrowseSourceScreenEvent.ResetFilters)
                            screenModel.onEvent(BrowseSourceScreenEvent.SetListing(Listing.Popular))
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Favorite,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(FilterChipDefaults.IconSize),
                            )
                        },
                        label = {
                            Text(text = stringResource(ephyra.app.core.common.R.string.popular))
                        },
                    )
                    if ((source as CatalogueSource).supportsLatest) {
                        FilterChip(
                            selected = state.listing == Listing.Latest,
                            onClick = {
                                screenModel.onEvent(BrowseSourceScreenEvent.ResetFilters)
                                screenModel.onEvent(BrowseSourceScreenEvent.SetListing(Listing.Latest))
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.NewReleases,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(FilterChipDefaults.IconSize),
                                )
                            },
                            label = {
                                Text(text = stringResource(ephyra.app.core.common.R.string.latest))
                            },
                        )
                    }
                    if (state.filters.isNotEmpty()) {
                        FilterChip(
                            selected = state.listing is Listing.Search,
                            onClick = { screenModel.onEvent(BrowseSourceScreenEvent.OpenFilterSheet) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.FilterList,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(FilterChipDefaults.IconSize),
                                )
                            },
                            label = {
                                Text(text = stringResource(ephyra.app.core.common.R.string.action_filter))
                            },
                        )
                    }
                }

                HorizontalDivider()
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        BrowseSourceContent(
            source = source,
            mangaList = screenModel.mangaPagerFlowFlow.collectAsLazyPagingItems(),
            columns = screenModel.getColumnsPreference(LocalConfiguration.current.orientation),
            displayMode = screenModel.displayMode,
            snackbarHostState = snackbarHostState,
            contentPadding = paddingValues,
            onWebViewClick = onWebViewClick,
            onHelpClick = { uriHandler.openUri(Constants.URL_HELP) },
            onLocalSourceHelpClick = onHelpClick,
            onMangaClick = { navController.navigate(ephyra.presentation.core.ui.navigation.Screen.MangaDetails(mangaId = it.id, fromSource = true)) },
            onMangaLongClick = { manga ->
                scope.launchIO {
                    val duplicates = screenModel.getDuplicateLibraryManga(manga)
                    when {
                        manga.favorite ->
                            screenModel.onEvent(
                                BrowseSourceScreenEvent.SetDialog(
                                    BrowseSourceScreenModel.Dialog.RemoveManga(manga),
                                ),
                            )
                        duplicates.isNotEmpty() ->
                            screenModel.onEvent(
                                BrowseSourceScreenEvent.SetDialog(
                                    BrowseSourceScreenModel.Dialog.AddDuplicateManga(manga, duplicates),
                                ),
                            )
                        else -> screenModel.onEvent(BrowseSourceScreenEvent.AddFavorite(manga))
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            },
        )
    }

    val onDismissRequest = { screenModel.onEvent(BrowseSourceScreenEvent.SetDialog(null)) }
    when (val dialog = state.dialog) {
        is BrowseSourceScreenModel.Dialog.Filter -> {
            SourceFilterDialog(
                onDismissRequest = onDismissRequest,
                filters = state.filters,
                onReset = { screenModel.onEvent(BrowseSourceScreenEvent.ResetFilters) },
                onFilter = { screenModel.onEvent(BrowseSourceScreenEvent.Search(filters = state.filters)) },
                onUpdate = { screenModel.onEvent(BrowseSourceScreenEvent.SetFilters(it)) },
            )
        }

        is BrowseSourceScreenModel.Dialog.AddDuplicateManga -> {
            DuplicateMangaDialog(
                duplicates = dialog.duplicates,
                onDismissRequest = onDismissRequest,
                onConfirm = { screenModel.onEvent(BrowseSourceScreenEvent.AddFavorite(dialog.manga)) },
                onOpenManga = { navController.navigate(ephyra.presentation.core.ui.navigation.Screen.MangaDetails(mangaId = it.id, fromSource = false)) },
                onMigrate = {
                    screenModel.onEvent(
                        BrowseSourceScreenEvent.SetDialog(BrowseSourceScreenModel.Dialog.Migrate(dialog.manga, it)),
                    )
                },
                sourceManager = screenModel.sourceManager,
            )
        }

        is BrowseSourceScreenModel.Dialog.Migrate -> {
            MigrateMangaDialog(
                current = dialog.current,
                target = dialog.target,
                onClickTitle = {
                    navController.navigate(ephyra.presentation.core.ui.navigation.Screen.MangaDetails(mangaId = dialog.current.id, fromSource = false))
                },
                onDismissRequest = onDismissRequest,
            )
        }

        is BrowseSourceScreenModel.Dialog.RemoveManga -> {
            RemoveMangaDialog(
                onDismissRequest = onDismissRequest,
                onConfirm = {
                    screenModel.onEvent(BrowseSourceScreenEvent.ChangeMangaFavorite(dialog.manga))
                },
                mangaToRemove = dialog.manga,
            )
        }

        is BrowseSourceScreenModel.Dialog.ChangeMangaCategory -> {
            ChangeCategoryDialog(
                initialSelection = dialog.initialSelection,
                onDismissRequest = onDismissRequest,
                onEditCategories = { navController.navigate(ephyra.presentation.core.ui.navigation.Screen.Category) },
                onConfirm = { include, _ ->
                    screenModel.onEvent(BrowseSourceScreenEvent.ChangeMangaFavorite(dialog.manga))
                    screenModel.onEvent(BrowseSourceScreenEvent.MoveMangaToCategories(dialog.manga, include))
                },
            )
        }

        else -> {}
    }

    LaunchedEffect(Unit) {
        queryEvent.receiveAsFlow()
            .collectLatest {
                when (it) {
                    is SearchType.Genre -> screenModel.onEvent(BrowseSourceScreenEvent.SearchGenre(it.txt))
                    is SearchType.Text -> screenModel.onEvent(BrowseSourceScreenEvent.Search(it.txt))
                }
            }
    }
}

private val queryEvent = Channel<SearchType>()

sealed class SearchType(val txt: String) {
    class Text(txt: String) : SearchType(txt)
    class Genre(txt: String) : SearchType(txt)
}
