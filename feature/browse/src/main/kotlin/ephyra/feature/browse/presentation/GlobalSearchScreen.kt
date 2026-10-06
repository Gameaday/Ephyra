package ephyra.feature.browse.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ephyra.core.common.util.system.LocaleHelper
import ephyra.domain.manga.model.Manga
import ephyra.feature.browse.presentation.components.GlobalSearchCardRow
import ephyra.feature.browse.presentation.components.GlobalSearchErrorResultItem
import ephyra.feature.browse.presentation.components.GlobalSearchLoadingResultItem
import ephyra.feature.browse.presentation.components.GlobalSearchResultItem
import ephyra.feature.browse.presentation.components.GlobalSearchToolbar
import ephyra.feature.browse.source.globalsearch.SearchItemResult
import ephyra.feature.browse.source.globalsearch.SearchViewModel
import ephyra.feature.browse.source.globalsearch.SourceFilter
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.screens.EmptyScreen
import eu.kanade.tachiyomi.source.CatalogueSource

@Composable
fun GlobalSearchScreen(
    state: SearchViewModel.State,
    navigateUp: () -> Unit,
    onChangeSearchQuery: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onChangeSearchFilter: (SourceFilter) -> Unit,
    onToggleResults: () -> Unit,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickSource: (CatalogueSource) -> Unit,
    onClickItem: (Manga) -> Unit,
    onLongClickItem: (Manga) -> Unit,
    suggestions: List<String> = emptyList(),
    onSuggestionClick: (String) -> Unit = {},
    mergedDuplicateCount: Int = 0,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            GlobalSearchToolbar(
                searchQuery = state.searchQuery,
                progress = state.progress,
                total = state.total,
                navigateUp = navigateUp,
                onChangeSearchQuery = onChangeSearchQuery,
                onSearch = onSearch,
                hideSourceFilter = false,
                sourceFilter = state.sourceFilter,
                onChangeSearchFilter = onChangeSearchFilter,
                onlyShowHasResults = state.onlyShowHasResults,
                onToggleResults = onToggleResults,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        GlobalSearchContent(
            items = state.filteredItems,
            contentPadding = paddingValues,
            getManga = getManga,
            onClickSource = onClickSource,
            onClickItem = onClickItem,
            onLongClickItem = onLongClickItem,
            suggestions = suggestions,
            onSuggestionClick = onSuggestionClick,
            mergedDuplicateCount = mergedDuplicateCount,
            searchQuery = state.searchQuery,
        )
    }
}

/**
 * One-tap suggestion chips (recent queries + library title matches) shown above the
 * results while the query is short or blank; removes the need to retype old searches.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GlobalSearchSuggestions(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
) {
    if (suggestions.isEmpty()) return
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        suggestions.forEach { suggestion ->
            SuggestionChip(
                onClick = { onSuggestionClick(suggestion) },
                label = { Text(suggestion, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.semantics {
                    contentDescription = "Search suggestion: $suggestion"
                },
            )
        }
    }
}

/**
 * Banner surfaced when Smart Merge collapsed duplicate rows from different sources —
 * makes the dedup value visible instead of silently hiding results.
 */
@Composable
internal fun GlobalSearchMergedBanner(mergedCount: Int) {
    if (mergedCount <= 0) return
    Text(
        text = stringResource(
            ephyra.app.core.common.R.string.search_merged_banner,
            mergedCount,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
internal fun GlobalSearchContent(
    items: Map<CatalogueSource, SearchItemResult>,
    contentPadding: PaddingValues,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickSource: (CatalogueSource) -> Unit,
    onClickItem: (Manga) -> Unit,
    onLongClickItem: (Manga) -> Unit,
    fromSourceId: Long? = null,
    suggestions: List<String> = emptyList(),
    onSuggestionClick: (String) -> Unit = {},
    mergedDuplicateCount: Int = 0,
    searchQuery: String? = null,
    libraryResults: List<Manga> = emptyList(),
) {
    val loading = items.values.any { it is SearchItemResult.Loading }
    val hasResults = libraryResults.isNotEmpty() ||
        items.values.any { it is SearchItemResult.Success && !it.isEmpty }
    // Don't show a "no results" screen while a search is in flight, while library
    // suggestions still exist for the query (those are valid matches the user owns),
    // or when there's nothing to search yet (blank query / suggestions row present).
    val showEmptyState =
        !searchQuery.isNullOrBlank() && !loading && !hasResults && suggestions.isEmpty()

    if (showEmptyState) {
        EmptyScreen(
            stringRes = ephyra.app.core.common.R.string.no_results_found,
            modifier = Modifier.padding(contentPadding),
        )
        return
    }

    LazyColumn(
        contentPadding = contentPadding,
    ) {
        // Library section (instant, local DB): rendered above everything else when the
        // query matches something the user already owns, per the v1 spec. Reuses the
        // same card row as the per-source results so the page stays uniform and
        // uncluttered (Library → recents → sources).
        if (libraryResults.isNotEmpty()) {
            item(key = "library-results", contentType = "library-results") {
                Text(
                    text = stringResource(ephyra.app.core.common.R.string.search_library_suggestions),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
                GlobalSearchCardRow(
                    titles = libraryResults,
                    getManga = getManga,
                    onClick = onClickItem,
                    onLongClick = onLongClickItem,
                )
            }
        }
        if (suggestions.isNotEmpty() || mergedDuplicateCount > 0) {
            item(key = "search-suggestions", contentType = "search-suggestions") {
                GlobalSearchMergedBanner(mergedDuplicateCount)
                GlobalSearchSuggestions(
                    suggestions = suggestions,
                    onSuggestionClick = onSuggestionClick,
                )
            }
        }
        items.forEach { (source, result) ->
            item(key = source.id, contentType = "search-result") {
                GlobalSearchResultItem(
                    title = fromSourceId?.let {
                        "▶ ${source.name}".takeIf { source.id == fromSourceId }
                    } ?: source.name,
                    subtitle = LocaleHelper.getLocalizedDisplayName(source.lang),
                    onClick = { onClickSource(source) },
                    modifier = Modifier.animateItem(),
                ) {
                    when (result) {
                        SearchItemResult.Loading -> {
                            GlobalSearchLoadingResultItem()
                        }

                        is SearchItemResult.Success -> {
                            GlobalSearchCardRow(
                                titles = result.result,
                                getManga = getManga,
                                onClick = onClickItem,
                                onLongClick = onLongClickItem,
                            )
                        }

                        is SearchItemResult.Error -> {
                            GlobalSearchErrorResultItem(message = result.throwable.message)
                        }
                    }
                }
            }
        }
    }
}
