package ephyra.feature.browse.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.asMangaCover
import ephyra.feature.browse.source.globalsearch.MergedSearchResult
import ephyra.presentation.core.components.material.padding
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.manga.components.CommonMangaItemDefaults
import ephyra.presentation.manga.components.MangaComfortableGridItem

/**
 * Primary global-search presentation: one deduped grid of works (Smart Merge output) with
 * per-source chips under each entry, instead of one row per source.
 *
 * `sourceIds.size > 1` entries render a chip row naming every catalogue that returned the
 * same work, which is what makes the dedup trustworthy rather than silently hiding results.
 */
@Composable
fun MergedSearchResultsGrid(
    results: List<MergedSearchResult>,
    sourceNames: (Long) -> String?,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickItem: (Manga) -> Unit,
    onLongClickItem: (Manga) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        items(
            results,
            key = { "${it.manga.source}:${it.manga.url}" },
            contentType = { "merged-search-result" },
        ) { entry ->
            MergedSearchResultCard(
                entry = entry,
                sourceNames = sourceNames,
                getManga = getManga,
                onClick = onClickItem,
                onLongClick = onLongClickItem,
            )
        }
    }
}

@Composable
internal fun MergedSearchResultCard(
    entry: MergedSearchResult,
    sourceNames: (Long) -> String?,
    getManga: @Composable (Manga) -> State<Manga>,
    onClick: (Manga) -> Unit,
    onLongClick: (Manga) -> Unit,
) {
    val manga by getManga(entry.manga)
    Column {
        Box(modifier = Modifier.width(104.dp)) {
            MangaComfortableGridItem(
                title = manga.title,
                titleMaxLines = 3,
                coverData = manga.asMangaCover(),
                coverBadgeStart = { InLibraryBadge(enabled = manga.favorite) },
                coverAlpha = if (manga.favorite) CommonMangaItemDefaults.BrowseFavoriteCoverAlpha else 1f,
                onClick = { onClick(manga) },
                onLongClick = { onLongClick(manga) },
            )
        }
        if (entry.sourceIds.size > 1) {
            Text(
                text = stringResource(
                    ephyra.app.core.common.R.string.search_merged_banner_short,
                    entry.sourceIds.size,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp, top = 2.dp),
            )
            // Per-source chips: every catalogue that returned this same work.
            entry.sourceIds.forEach { sourceId ->
                val name = sourceNames(sourceId) ?: return@forEach
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
        }
    }
}
