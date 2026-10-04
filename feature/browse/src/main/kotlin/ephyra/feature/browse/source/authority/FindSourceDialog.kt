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
 * Dialog shown when the user adds a manga from Discover and existing unpaired library
 * entries (without canonical IDs) match the title. The user can select one to merge
 * the canonical ID into, or skip to create a separate authority entry.
 */
@Composable
internal fun MergeWithExistingDialog(
    resultTitle: String,
    candidates: List<MangaWithChapterCount>,
    onMerge: (MangaWithChapterCount) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(ephyra.app.core.common.R.string.discover_merge_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(ephyra.app.core.common.R.string.discover_merge_message, resultTitle),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(MaterialTheme.padding.medium))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    items(candidates, key = { it.manga.id }) { candidate ->
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onMerge(candidate) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(MaterialTheme.padding.medium),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                            ) {
                                AsyncImage(
                                    model = candidate.manga.thumbnailUrl,
                                    contentDescription = candidate.manga.title,
                                    modifier = Modifier
                                        .size(40.dp, 56.dp)
                                        .clip(MaterialTheme.shapes.extraSmall),
                                    contentScale = ContentScale.Crop,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = candidate.manga.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = pluralStringResource(
                                            ephyra.app.core.common.R.plurals.discover_merge_chapters,
                                            count = candidate.chapterCount.toInt(),
                                            candidate.chapterCount,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSkip) {
                Text(stringResource(ephyra.app.core.common.R.string.discover_merge_skip))
            }
        },
    )
}

/**
 * Dialog prompting the user to find a content source after adding an authority manga.
 * Shows auto-search results when available, with option for manual search fallback.
 * This bridges the authority-first model with source pairing: manga exist by their
 * canonical identity first, and a content source is an optional addition on top.
 */
@Composable
internal fun FindSourceDialog(
    mangaTitle: String,
    sourceMatches: List<FindContentSource.SourceMatch>,
    isSearching: Boolean,
    onSelectSource: (FindContentSource.SourceMatch) -> Unit,
    onManualSearch: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(ephyra.app.core.common.R.string.discover_find_source_title)) },
        text = {
            Column {
                if (isSearching) {
                    Text(
                        text = stringResource(ephyra.app.core.common.R.string.discover_find_source_searching),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(MaterialTheme.padding.medium))
                    androidx.compose.material3.LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (sourceMatches.isNotEmpty()) {
                    Text(
                        text = stringResource(ephyra.app.core.common.R.string.discover_find_source_found, mangaTitle),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(MaterialTheme.padding.medium))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        items(
                            sourceMatches,
                            key = { it.sourceId },
                        ) { match ->
                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectSource(match) },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(MaterialTheme.padding.medium),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(
                                        MaterialTheme.padding.medium,
                                    ),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = match.sourceName,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = match.manga.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        val chapterText = when {
                                            match.chapterCount > 0 -> pluralStringResource(
                                                ephyra.app.core.common.R.plurals.discover_find_source_chapters,
                                                count = match.chapterCount,
                                                match.chapterCount,
                                            )

                                            match.chapterCount == 0 -> stringResource(
                                                ephyra.app.core.common.R.string.discover_find_source_no_chapters,
                                            )

                                            else -> null
                                        }
                                        if (chapterText != null) {
                                            Text(
                                                text = chapterText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (match.chapterCount > 0) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.error
                                                },
                                            )
                                        }
                                    }
                                    val confidencePercent = (match.confidence * 100).toInt()
                                    Text(
                                        text = "$confidencePercent%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (match.confidence >= 0.9) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = stringResource(ephyra.app.core.common.R.string.discover_find_source_message, mangaTitle),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onManualSearch) {
                Text(stringResource(ephyra.app.core.common.R.string.discover_find_source_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(ephyra.app.core.common.R.string.discover_find_source_skip))
            }
        },
    )
}
