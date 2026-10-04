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
 * Full-detail sheet shown when tapping a Discover search result.
 * Displays all authoritative metadata from the tracker: cover, title, description,
 * author/artist, status, chapters, publishing type, start date, and alternative titles.
 */
@Composable
internal fun DiscoverDetailSheet(
    result: TrackSearch,
    isAdded: Boolean,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    AdaptiveSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = MaterialTheme.padding.medium,
                    vertical = MaterialTheme.padding.medium,
                ),
        ) {
            // Cover + title row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
            ) {
                AsyncImage(
                    model = result.cover_url,
                    contentDescription = result.title,
                    modifier = Modifier
                        .size(120.dp, 170.dp)
                        .clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Crop,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = result.title,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    val authors = result.authors.joinToString(", ")
                    if (authors.isNotBlank()) {
                        DetailLabel(
                            label = stringResource(ephyra.app.core.common.R.string.discover_detail_author),
                            value = authors,
                        )
                    }
                    val artists = result.artists.joinToString(", ")
                    if (artists.isNotBlank() && artists != authors) {
                        DetailLabel(
                            label = stringResource(ephyra.app.core.common.R.string.discover_detail_artist),
                            value = artists,
                        )
                    }
                    if (result.publishing_status.isNotBlank()) {
                        DetailLabel(
                            label = stringResource(ephyra.app.core.common.R.string.discover_detail_status),
                            value = result.publishing_status,
                        )
                    }
                    if (result.publishing_type.isNotBlank()) {
                        DetailLabel(
                            label = stringResource(ephyra.app.core.common.R.string.discover_detail_type),
                            value = result.publishing_type,
                        )
                    }
                    if (result.start_date.isNotBlank()) {
                        DetailLabel(
                            label = stringResource(ephyra.app.core.common.R.string.discover_detail_start_date),
                            value = result.start_date,
                        )
                    }
                    if (result.total_chapters > 0) {
                        DetailLabel(
                            label = stringResource(ephyra.app.core.common.R.string.discover_detail_chapters),
                            value = result.total_chapters.toString(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(MaterialTheme.padding.medium))

            // Add to library button
            Button(
                onClick = onAdd,
                enabled = !isAdded,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = if (isAdded) Icons.Outlined.Check else Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (isAdded) {
                            ephyra.app.core.common.R.string.discover_added
                        } else {
                            ephyra.app.core.common.R.string.discover_add
                        },
                    ),
                )
            }

            // Description
            if (result.summary.isNotBlank()) {
                Spacer(Modifier.height(MaterialTheme.padding.medium))
                HorizontalDivider()
                Spacer(Modifier.height(MaterialTheme.padding.medium))
                SelectionContainer {
                    Text(
                        text = result.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Alternative titles
            if (result.alternative_titles.isNotEmpty()) {
                Spacer(Modifier.height(MaterialTheme.padding.medium))
                HorizontalDivider()
                Spacer(Modifier.height(MaterialTheme.padding.small))
                Text(
                    text = stringResource(ephyra.app.core.common.R.string.discover_detail_alt_titles),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                result.alternative_titles.forEach { altTitle ->
                    Text(
                        text = "• $altTitle",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(MaterialTheme.padding.medium))
        }
    }
}

/** Small label + value row for the detail view metadata section. */
@Composable
internal fun DetailLabel(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
