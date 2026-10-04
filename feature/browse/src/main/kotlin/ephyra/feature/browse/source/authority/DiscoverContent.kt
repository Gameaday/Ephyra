package ephyra.feature.browse.source.authority

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import ephyra.domain.content.model.ContentType
import ephyra.domain.manga.interactor.FindContentSource
import ephyra.domain.manga.model.MangaWithChapterCount
import ephyra.domain.track.interactor.AddTracks
import ephyra.domain.track.model.TrackSearch
import ephyra.feature.browse.source.globalsearch.GlobalSearchScreen
import ephyra.presentation.core.components.AdaptiveSheet
import ephyra.presentation.core.components.ScrollbarLazyColumn
import ephyra.presentation.core.components.TabContent
import ephyra.presentation.core.components.material.padding
import ephyra.presentation.core.i18n.pluralStringResource
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.screens.EmptyScreen
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.core.theme.MotionTokens
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.Screen
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
internal fun DiscoverContent(
    state: AuthoritySearchState,
    trackersForFilter: (ContentType) -> ImmutableList<ephyra.domain.track.service.Tracker>,
    onSelectTracker: (ephyra.domain.track.service.Tracker) -> Unit,
    onSearch: (String) -> Unit,
    onRetrySearch: () -> Unit,
    onAddToLibrary: (TrackSearch) -> Unit,
    onSelectResult: (TrackSearch) -> Unit,
    onSetContentTypeFilter: (ContentType) -> Unit,
    contentPadding: PaddingValues,
) {
    // All trackers = unfiltered list — use to check if any are available at all
    val allTrackers = trackersForFilter(ContentType.UNKNOWN)
    if (allTrackers.isEmpty()) {
        EmptyScreen(
            stringRes = ephyra.app.core.common.R.string.discover_no_trackers,
            modifier = Modifier.padding(contentPadding),
        )
        return
    }

    // Trackers filtered by the selected content type
    val filteredTrackers = trackersForFilter(state.contentTypeFilter)

    val focusManager = LocalFocusManager.current
    var query by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        // Search bar — rounded pill shape, Material 3 Expressive
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MaterialTheme.padding.medium,
                    vertical = MaterialTheme.padding.small,
                ),
            placeholder = { Text(stringResource(ephyra.app.core.common.R.string.discover_search_hint)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = null,
                        )
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onSearch(query)
                    focusManager.clearFocus()
                },
            ),
        )

        // Content type filter chips — always visible, controls which trackers are shown.
        // This organizes authorities by what they are an authority of, so only
        // relevant trackers are queried — saving API calls as more authorities are added.
        val typeFilters = remember {
            listOf(ContentType.UNKNOWN, ContentType.MANGA, ContentType.NOVEL)
        }
        val typeFilterLabels = mapOf(
            ContentType.UNKNOWN to stringResource(ephyra.app.core.common.R.string.discover_filter_all),
            ContentType.MANGA to stringResource(ephyra.app.core.common.R.string.discover_filter_manga),
            ContentType.NOVEL to stringResource(ephyra.app.core.common.R.string.discover_filter_novel),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.extraSmall,
            ),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            items(typeFilters.size) { index ->
                val type = typeFilters[index]
                val isSelected = state.contentTypeFilter == type
                FilterChip(
                    selected = isSelected,
                    onClick = { onSetContentTypeFilter(type) },
                    label = { Text(typeFilterLabels[type] ?: "") },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }

        // Tracker filter chips — filtered by the selected content type.
        // Only shown when multiple trackers match the current type.
        // Uses LazyRow for horizontal scrolling if many trackers are available.
        if (filteredTrackers.size > 1) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    horizontal = MaterialTheme.padding.medium,
                    vertical = MaterialTheme.padding.extraSmall,
                ),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                items(filteredTrackers.size) { index ->
                    val tracker = filteredTrackers[index]
                    val isSelected = tracker == state.selectedTracker
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectTracker(tracker) },
                        label = { Text(tracker.name) },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }

        // Result count indicator — shows how many results are displayed
        val displayResults = state.filteredResults
        if (displayResults.isNotEmpty()) {
            val countText = if (
                state.contentTypeFilter != ContentType.UNKNOWN &&
                displayResults.size != state.results.size
            ) {
                pluralStringResource(
                    ephyra.app.core.common.R.plurals.discover_result_count_filtered,
                    count = displayResults.size,
                    displayResults.size,
                    state.results.size,
                )
            } else {
                pluralStringResource(
                    ephyra.app.core.common.R.plurals.discover_result_count,
                    count = displayResults.size,
                    displayResults.size,
                )
            }
            Text(
                text = countText,
                modifier = Modifier.padding(
                    horizontal = MaterialTheme.padding.medium,
                    vertical = MaterialTheme.padding.extraSmall,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Animated content area — search results or landing state
        // Derive a stable display state to avoid unnecessary transitions
        val displayState = when {
            state.isSearching -> DiscoverDisplayState.LOADING
            state.searchError != null -> DiscoverDisplayState.ERROR
            state.results.isEmpty() && state.query.isBlank() -> DiscoverDisplayState.LANDING
            displayResults.isEmpty() -> DiscoverDisplayState.NO_RESULTS
            else -> DiscoverDisplayState.RESULTS
        }
        AnimatedContent(
            targetState = displayState,
            transitionSpec = {
                fadeIn(tween(MotionTokens.DURATION_MEDIUM)) togetherWith
                    fadeOut(tween(MotionTokens.DURATION_SHORT))
            },
            label = "discover_results",
            modifier = Modifier.weight(1f),
        ) { currentDisplayState ->
            when (currentDisplayState) {
                DiscoverDisplayState.LOADING -> LoadingScreen()
                DiscoverDisplayState.LANDING -> {
                    EmptyScreen(stringResource(ephyra.app.core.common.R.string.discover_empty_state))
                }

                DiscoverDisplayState.ERROR -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(MaterialTheme.padding.medium))
                        Text(
                            text = stringResource(ephyra.app.core.common.R.string.discover_search_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(MaterialTheme.padding.medium))
                        Button(onClick = onRetrySearch) {
                            Text(stringResource(ephyra.app.core.common.R.string.discover_retry))
                        }
                    }
                }

                DiscoverDisplayState.NO_RESULTS -> {
                    EmptyScreen(stringResource(ephyra.app.core.common.R.string.no_results_found))
                }

                DiscoverDisplayState.RESULTS -> {
                    ScrollbarLazyColumn(
                        contentPadding = PaddingValues(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.small,
                        ),
                        verticalArrangement = Arrangement.spacedBy(
                            MaterialTheme.padding.small,
                        ),
                    ) {
                        items(
                            displayResults,
                            key = { "${it.tracker_id}:${it.remote_id}" },
                        ) { result ->
                            val prefix =
                                AddTracks.TRACKER_CANONICAL_PREFIXES[result.tracker_id]
                            val canonicalId = if (prefix != null) {
                                "$prefix:${result.remote_id}"
                            } else {
                                null
                            }
                            val isAdded = canonicalId != null &&
                                canonicalId in state.addedCanonicalIds
                            val isAdding = canonicalId != null &&
                                canonicalId in state.addingCanonicalIds
                            DiscoverResultCard(
                                result = result,
                                isAdded = isAdded,
                                isAdding = isAdding,
                                onAdd = { onAddToLibrary(result) },
                                onClick = { onSelectResult(result) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DiscoverResultCard(
    result: TrackSearch,
    isAdded: Boolean,
    isAdding: Boolean,
    onAdd: () -> Unit,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.padding.medium),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
        ) {
            AsyncImage(
                model = result.cover_url,
                contentDescription = result.title,
                modifier = Modifier
                    .size(56.dp, 80.dp)
                    .clip(MaterialTheme.shapes.extraSmall),
                contentScale = ContentScale.Crop,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // Type + status metadata row for disambiguation
                val metaItems = buildList {
                    if (result.publishing_type.isNotBlank()) add(result.publishing_type)
                    if (result.publishing_status.isNotBlank()) add(result.publishing_status)
                    if (result.start_date.isNotBlank()) add(result.start_date)
                }
                if (metaItems.isNotEmpty()) {
                    Text(
                        text = metaItems.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (result.summary.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = result.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(MaterialTheme.padding.small))
            IconButton(
                onClick = onAdd,
                enabled = !isAdded && !isAdding,
                colors = if (isAdded) {
                    IconButtonDefaults.iconButtonColors(
                        disabledContentColor = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    IconButtonDefaults.filledTonalIconButtonColors()
                },
            ) {
                if (isAdding) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = if (isAdded) {
                            Icons.Outlined.Check
                        } else {
                            Icons.Outlined.Add
                        },
                        contentDescription = stringResource(
                            if (isAdded) {
                                ephyra.app.core.common.R.string.discover_added
                            } else {
                                ephyra.app.core.common.R.string.discover_add
                            },
                        ),
                    )
                }
            }
        }
    }
}

/** Display states for the animated content area — avoids Triple allocations. */
private enum class DiscoverDisplayState { LOADING, LANDING, NO_RESULTS, RESULTS, ERROR }
