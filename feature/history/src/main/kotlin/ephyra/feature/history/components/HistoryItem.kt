package ephyra.feature.history.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import ephyra.feature.history.HistoryUiItem
import ephyra.presentation.core.components.MangaCover
import ephyra.presentation.core.components.material.padding
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.theme.EphyraPreviewTheme

private val HistoryItemHeight = 96.dp

@Composable
fun HistoryItem(
    history: HistoryUiItem,
    onClickCover: () -> Unit,
    onClickResume: () -> Unit,
    onClickDelete: () -> Unit,
    onClickFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClickResume)
            .height(HistoryItemHeight)
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MangaCover.Book(
            modifier = Modifier.fillMaxHeight(),
            data = history.coverData,
            // Named so this cover is the shared element for History -> Series, like the library
            // grid cell. Without it the series page declared a shared element with no counterpart
            // here, and because the pair container motion is a deliberate no-op the whole
            // transition had nothing left to animate, so it cut.
            mangaId = history.mangaId,
            onClick = onClickCover,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = MaterialTheme.padding.medium, end = MaterialTheme.padding.small),
        ) {
            val textStyle = MaterialTheme.typography.bodyMedium
            Text(
                text = history.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = textStyle,
            )
            // Keyed by the label, not the timestamp: the label is what actually changes when the
            // row is rebound, and keying on a value we no longer hold would be a lie.
            // In a LazyColumn the same slot is reused as items scroll, so a keyless remember would
            // show the previous item timestamp here, and never update it.
            Text(
                text = if (history.chapterLabel != null) {
                    stringResource(
                        ephyra.app.core.common.R.string.recent_manga_time,
                        history.chapterLabel,
                        history.readAtLabel,
                    )
                } else {
                    history.readAtLabel
                },
                modifier = Modifier.padding(top = 4.dp),
                style = textStyle,
            )
        }

        if (history.canAddToLibrary) {
            IconButton(onClick = onClickFavorite) {
                Icon(
                    imageVector = Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(ephyra.app.core.common.R.string.add_to_library),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        IconButton(onClick = onClickDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(ephyra.app.core.common.R.string.action_delete),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun HistoryItemPreviews(
    @PreviewParameter(HistoryWithRelationsProvider::class)
    previewItem: HistoryUiItem,
) {
    EphyraPreviewTheme {
        Surface {
            HistoryItem(
                history = previewItem,
                onClickCover = {},
                onClickResume = {},
                onClickDelete = {},
                onClickFavorite = {},
            )
        }
    }
}
