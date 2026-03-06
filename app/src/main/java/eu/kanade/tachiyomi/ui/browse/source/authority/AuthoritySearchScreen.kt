package eu.kanade.tachiyomi.ui.browse.source.authority

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Screen for searching tracker databases (MAL, AniList, etc.) and adding results
 * to the library as authority-only manga entries.
 *
 * Addresses the user report that "the search on the browse page still searches all the
 * extensions directly" — this provides a dedicated authority-first search path.
 */
class AuthoritySearchScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { AuthoritySearchScreenModel() }
        val state by screenModel.state.collectAsState()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.authority_search_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { paddingValues ->
            when {
                screenModel.availableTrackers.isEmpty() -> {
                    EmptyScreen(stringResource(MR.strings.authority_search_no_trackers))
                }
                else -> {
                    AuthoritySearchContent(
                        state = state,
                        availableTrackers = screenModel.availableTrackers,
                        onSelectTracker = screenModel::selectTracker,
                        onSearch = screenModel::search,
                        onAddToLibrary = screenModel::addToLibrary,
                        contentPadding = paddingValues,
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthoritySearchContent(
    state: AuthoritySearchState,
    availableTrackers: List<eu.kanade.tachiyomi.data.track.Tracker>,
    onSelectTracker: (eu.kanade.tachiyomi.data.track.Tracker) -> Unit,
    onSearch: (String) -> Unit,
    onAddToLibrary: (TrackSearch) -> Unit,
    contentPadding: PaddingValues,
) {
    var query by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        // Tracker selector chips
        if (availableTrackers.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.padding.medium),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                availableTrackers.forEach { tracker ->
                    FilterChip(
                        selected = tracker == state.selectedTracker,
                        onClick = { onSelectTracker(tracker) },
                        label = { Text(tracker.name) },
                    )
                }
            }
        }

        // Search box
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
            placeholder = { Text(stringResource(MR.strings.authority_search_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
        )

        HorizontalDivider()

        when {
            state.isSearching -> LoadingScreen()
            state.results.isEmpty() && state.query.isBlank() -> {
                EmptyScreen(stringResource(MR.strings.authority_search_empty))
            }
            state.results.isEmpty() -> {
                EmptyScreen(stringResource(MR.strings.no_results_found))
            }
            else -> {
                ScrollbarLazyColumn(contentPadding = PaddingValues(vertical = MaterialTheme.padding.small)) {
                    items(state.results, key = { "${it.tracker_id}:${it.remote_id}" }) { result ->
                        val prefix = eu.kanade.domain.track.interactor.AddTracks.TRACKER_CANONICAL_PREFIXES[result.tracker_id]
                        val canonicalId = if (prefix != null) "$prefix:${result.remote_id}" else null
                        val isAdded = canonicalId != null && canonicalId in state.addedCanonicalIds
                        AuthoritySearchResultItem(
                            result = result,
                            isAdded = isAdded,
                            onAdd = { onAddToLibrary(result) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthoritySearchResultItem(
    result: TrackSearch,
    isAdded: Boolean,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
    ) {
        AsyncImage(
            model = result.cover_url,
            contentDescription = result.title,
            modifier = Modifier
                .size(48.dp, 64.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
            if (result.publishing_type.isNotBlank()) {
                Text(
                    text = result.publishing_type,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clickable(enabled = !isAdded, onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            if (isAdded) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = stringResource(MR.strings.authority_search_added),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(MR.strings.authority_search_add),
                )
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium))
}
