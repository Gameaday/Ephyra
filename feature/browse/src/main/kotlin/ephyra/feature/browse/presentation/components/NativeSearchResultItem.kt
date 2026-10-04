package ephyra.feature.browse.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import ephyra.feature.browse.source.globalsearch.NativeSourceResult
import ephyra.presentation.core.components.material.padding
import ephyra.presentation.core.i18n.stringResource
import ephyra.source.api.SourceContentItem

/**
 * Result row for a target-native gateway (Jellyfin/OPDS) in the unified search list.
 *
 * Protocol [SourceContentItem]s have no legacy `Manga` id, so unlike extension rows there is
 * nothing to navigate to yet — items render as read-only title chips until a native details
 * route exists. This keeps native results visible in the unified search without a broken tap
 * target.
 */
@Composable
fun NativeSearchResultItem(
    result: NativeSourceResult,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        when (result) {
            NativeSourceResult.Loading -> GlobalSearchLoadingResultItem()

            is NativeSourceResult.Error -> GlobalSearchErrorResultItem(message = result.message)

            is NativeSourceResult.Success -> {
                if (result.isEmpty) {
                    Text(
                        text = stringResource(ephyra.app.core.common.R.string.no_results_found),
                        modifier = Modifier.padding(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.small,
                        ),
                    )
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(MaterialTheme.padding.small),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                    ) {
                        items(
                            result.items,
                            key = { "${it.sourceId.value}:${it.url}" },
                            contentType = { "native-item" },
                        ) { item ->
                            NativeItemChip(item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NativeItemChip(item: SourceContentItem) {
    Text(
        text = item.title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(
            horizontal = MaterialTheme.padding.small,
            vertical = MaterialTheme.padding.extraSmall,
        ),
    )
}
