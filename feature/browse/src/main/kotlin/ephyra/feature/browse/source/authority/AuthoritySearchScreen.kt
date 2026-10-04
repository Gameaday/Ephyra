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

/**
 * Creates the Search sub-tab inside the top-level Discover tab.
 *
 * This is the authority-first search experience: users search tracker databases
 * (MAL, AniList, MangaUpdates) and add results directly to their library.
 * The search is one part of the broader Discover flow — the tab is designed
 * to accommodate future discovery features (suggestions, recommendations)
 * alongside the search.
 */
@Composable
fun discoverTab(navController: NavController = LocalNavController.current): TabContent {
    val viewModel = hiltViewModel<AuthoritySearchViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    return TabContent(
        titleRes = ephyra.app.core.common.R.string.label_search,
        actions = persistentListOf(),
        content = { contentPadding, _ ->
            DiscoverContent(
                state = state,
                trackersForFilter = viewModel::trackersForFilter,
                onSelectTracker = { viewModel.onEvent(AuthoritySearchScreenEvent.SelectTracker(it)) },
                onSearch = { viewModel.onEvent(AuthoritySearchScreenEvent.Search(it)) },
                onRetrySearch = { viewModel.onEvent(AuthoritySearchScreenEvent.RetrySearch) },
                onAddToLibrary = { viewModel.onEvent(AuthoritySearchScreenEvent.AddToLibrary(it)) },
                onSelectResult = { viewModel.onEvent(AuthoritySearchScreenEvent.SelectResult(it)) },
                onSetContentTypeFilter = { viewModel.onEvent(AuthoritySearchScreenEvent.SetContentTypeFilter(it)) },
                contentPadding = contentPadding,
            )

            // Detail sheet for viewing full result metadata
            val selectedResult = state.selectedResult
            if (selectedResult != null) {
                val prefix = AddTracks.TRACKER_CANONICAL_PREFIXES[selectedResult.tracker_id]
                val canonicalId = if (prefix != null) "$prefix:${selectedResult.remote_id}" else null
                val isAdded = canonicalId != null && canonicalId in state.addedCanonicalIds
                DiscoverDetailSheet(
                    result = selectedResult,
                    isAdded = isAdded,
                    onAdd = {
                        viewModel.onEvent(AuthoritySearchScreenEvent.AddToLibrary(selectedResult))
                        viewModel.onEvent(AuthoritySearchScreenEvent.DismissDetail)
                    },
                    onDismiss = { viewModel.onEvent(AuthoritySearchScreenEvent.DismissDetail) },
                )
            }

            // "Find content source?" prompt shown after adding an authority manga
            val sourcePrompt = state.sourcePromptManga
            if (sourcePrompt != null) {
                FindSourceDialog(
                    mangaTitle = sourcePrompt.title,
                    sourceMatches = sourcePrompt.sourceMatches,
                    isSearching = sourcePrompt.isSearching,
                    onSelectSource = { match ->
                        viewModel.onEvent(AuthoritySearchScreenEvent.DismissSourcePrompt)
                        navController.navigate(
                            Screen.BrowseSource(
                                match.sourceId,
                                match.manga.title,
                            ),
                        )
                    },
                    onManualSearch = {
                        viewModel.onEvent(AuthoritySearchScreenEvent.DismissSourcePrompt)
                        navController.navigate(Screen.GlobalSearch(sourcePrompt.title))
                    },
                    onDismiss = { viewModel.onEvent(AuthoritySearchScreenEvent.DismissSourcePrompt) },
                )
            }

            // "Merge with existing?" prompt when library has unpaired matches
            val mergePrompt = state.mergePrompt
            if (mergePrompt != null) {
                MergeWithExistingDialog(
                    resultTitle = mergePrompt.result.title,
                    candidates = mergePrompt.candidates,
                    onMerge = { viewModel.onEvent(AuthoritySearchScreenEvent.MergeWithExisting(it)) },
                    onSkip = { viewModel.onEvent(AuthoritySearchScreenEvent.SkipMerge) },
                    onDismiss = { viewModel.onEvent(AuthoritySearchScreenEvent.DismissMergePrompt) },
                )
            }
        },
    )
}
