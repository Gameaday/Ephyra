package ephyra.feature.updates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ephyra.domain.download.model.Download
import ephyra.domain.updates.model.UpdatesWithRelations
import ephyra.feature.manga.presentation.components.ChapterDownloadAction
import ephyra.feature.manga.presentation.components.ChapterDownloadIndicator
import ephyra.feature.manga.presentation.components.DotSeparatorText
import ephyra.presentation.core.components.ListGroupHeader
import ephyra.presentation.core.components.MangaCover
import ephyra.presentation.core.components.material.DISABLED_ALPHA
import ephyra.presentation.core.components.material.padding
import ephyra.presentation.core.components.relativeDateText
import ephyra.presentation.core.i18n.pluralStringResource
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.util.animateItemFastScroll
import ephyra.presentation.core.util.relativeTimeSpanString
import ephyra.presentation.core.util.selectedBackground
import kotlin.compareTo

internal fun LazyListScope.updatesLastUpdatedItem(
    lastUpdated: Long,
) {
    item(key = "updates-lastUpdated") {
        Box(
            modifier = Modifier
                .animateItem(fadeInSpec = null, fadeOutSpec = null)
                .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        ) {
            Text(
                text = stringResource(
                    ephyra.app.core.common.R.string.updates_last_update_info,
                    relativeTimeSpanString(lastUpdated),
                ),
                fontStyle = FontStyle.Italic,
            )
        }
    }
}

internal fun LazyListScope.updatesUiItems(
    uiModels: List<UpdatesUiModel>,
    expandedSeriesIds: Set<Long>,
    selectionMode: Boolean,
    onUpdateSelected: (UpdatesItem, Boolean, Boolean) -> Unit,
    onClickCover: (UpdatesItem) -> Unit,
    onClickUpdate: (UpdatesItem) -> Unit,
    onDownloadChapter: (List<UpdatesItem>, ChapterDownloadAction) -> Unit,
    onToggleSeries: (mangaId: Long) -> Unit,
) {
    items(
        items = uiModels.withExpandedSeriesItems(expandedSeriesIds),
        contentType = {
            when (it) {
                is UpdatesUiModel.Header -> "header"
                is UpdatesUiModel.Item -> "item"
                is UpdatesUiModel.Series -> "series"
            }
        },
        key = {
            when (it) {
                is UpdatesUiModel.Header -> "updatesHeader-${it.date}"
                is UpdatesUiModel.Item -> "updates-${it.item.update.mangaId}-${it.item.update.chapterId}"
                is UpdatesUiModel.Series -> "updatesSeries-${it.latest.update.mangaId}"
            }
        },
    ) { item ->
        when (item) {
            is UpdatesUiModel.Header -> {
                ListGroupHeader(
                    modifier = Modifier.Companion.animateItemFastScroll(),
                    text = relativeDateText(item.date),
                )
            }
            is UpdatesUiModel.Item -> {
                val updatesItem = item.item
                UpdatesUiItem(
                    modifier = Modifier.Companion.animateItemFastScroll(),
                    update = updatesItem.update,
                    selected = updatesItem.selected,
                    readProgress = updatesItem.update.lastPageRead
                        .takeIf { !updatesItem.update.read && it > 0L }
                        ?.let {
                            stringResource(
                                ephyra.app.core.common.R.string.chapter_progress,
                                it + 1,
                            )
                        },
                    onLongClick = {
                        onUpdateSelected(updatesItem, !updatesItem.selected, true)
                    },
                    onClick = {
                        when {
                            selectionMode -> onUpdateSelected(updatesItem, !updatesItem.selected, false)
                            else -> onClickUpdate(updatesItem)
                        }
                    },
                    onClickCover = { onClickCover(updatesItem) }.takeIf { !selectionMode },
                    onDownloadChapter = { action: ChapterDownloadAction ->
                        onDownloadChapter(listOf(updatesItem), action)
                    }.takeIf { !selectionMode },
                    downloadStateProvider = updatesItem.downloadStateProvider,
                    downloadProgressProvider = updatesItem.downloadProgressProvider,
                )
            }

            is UpdatesUiModel.Series -> {
                // Only the newest update and the expand toggle render here. When expanded, the older
                // updates become top-level keyed lazy items (see [withExpandedSeriesItems]), so only
                // the visible ones compose and each keeps its own key and animateItemFastScroll.
                Column(Modifier.Companion.animateItemFastScroll()) {
                    val latest = item.latest
                    UpdatesUiItem(
                        modifier = Modifier.Companion,
                        update = latest.update,
                        selected = latest.selected,
                        readProgress = latest.update.lastPageRead
                            .takeIf { !latest.update.read && it > 0L }
                            ?.let {
                                stringResource(
                                    ephyra.app.core.common.R.string.chapter_progress,
                                    it + 1,
                                )
                            },
                        onLongClick = {
                            onUpdateSelected(latest, !latest.selected, true)
                        },
                        onClick = {
                            when {
                                selectionMode -> onUpdateSelected(latest, !latest.selected, false)
                                else -> onClickUpdate(latest)
                            }
                        },
                        onClickCover = { onClickCover(latest) }.takeIf { !selectionMode },
                        onDownloadChapter = { action: ChapterDownloadAction ->
                            onDownloadChapter(listOf(latest), action)
                        }.takeIf { !selectionMode },
                        downloadStateProvider = latest.downloadStateProvider,
                        downloadProgressProvider = latest.downloadProgressProvider,
                    )
                    SeriesExpandRow(
                        count = item.older.size,
                        expanded = latest.update.mangaId in expandedSeriesIds,
                        onToggle = { onToggleSeries(latest.update.mangaId) },
                    )
                }
            }
        }
    }
}

/**
 * Flattens expanded series groups into top-level lazy items.
 *
 * Identical in intent to `HistoryScreen.withExpandedSeriesChildren`, and for the same reason: a series
 * rendered as a single lazy item would compose every older update whenever the group composed, give
 * none of them a key of their own, and none could animate with [animateItemFastScroll] â€” that
 * modifier only exists on the direct `LazyItemScope` receiver of a topâ€‘level item.
 *
 * Collapsed groups pass through untouched, so the collapsed list is exactly the stacked shape. The
 * children reuse the plainâ€‘item key and content type, which is unambiguous because grouping is by
 * `mangaId`: an update is either its group's newest (the [UpdatesUiModel.Series] row) or one of its
 * older items â€” never both.
 */
internal fun List<UpdatesUiModel>.withExpandedSeriesItems(
    expandedSeriesIds: Set<Long>,
): List<UpdatesUiModel> = flatMap { entry ->
    if (entry is UpdatesUiModel.Series && entry.latest.update.mangaId in expandedSeriesIds) {
        listOf(entry) + entry.older.map { UpdatesUiModel.Item(it) }
    } else {
        listOf(entry)
    }
}

/**
 * The expand/collapse affordance under a stacked series row.
 *
 * A wholeâ€‘width clickable row rather than an icon button, matching the history list: a small chevron
 * under a row that is itself clickable is ambiguous about which row it belongs to, and a wider target
 * is easier to hit.
 */
@Composable
private fun SeriesExpandRow(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = pluralStringResource(
                ephyra.app.core.common.R.plurals.updates_series_more_chapters,
                count,
                count,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UpdatesUiItem(
    update: UpdatesWithRelations,
    selected: Boolean,
    readProgress: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onClickCover: (() -> Unit)?,
    onDownloadChapter: ((ChapterDownloadAction) -> Unit)?,
    // Download Indicator
    downloadStateProvider: () -> Download.State,
    downloadProgressProvider: () -> Int,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val textAlpha = if (update.read) DISABLED_ALPHA else 1f

    Row(
        modifier = modifier
            .selectedBackground(selected)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    onLongClick()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
            )
            .height(56.dp)
            .padding(horizontal = MaterialTheme.padding.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MangaCover.Square(
            modifier = Modifier
                .padding(vertical = 6.dp)
                .fillMaxHeight(),
            data = update.coverData,
            // Named so this cover is the shared element for Updates -> Series. The row's cover is
            // square while the series header is 2:3, so the flight interpolates aspect; that is
            // deliberate here (a container transform) and is only correct because MangaCover
            // resolves the ratio before the shared modifier measures the element.
            mangaId = update.mangaId,
            onClick = onClickCover,
        )

        Column(
            modifier = Modifier
                .padding(horizontal = MaterialTheme.padding.medium)
                .weight(1f),
        ) {
            Text(
                text = update.mangaTitle,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = textAlpha),
                overflow = TextOverflow.Ellipsis,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                var textHeight by remember { mutableIntStateOf(0) }
                if (!update.read) {
                    Icon(
                        imageVector = Icons.Filled.Circle,
                        contentDescription = stringResource(ephyra.app.core.common.R.string.unread),
                        modifier = Modifier
                            .height(8.dp)
                            .padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (update.bookmark) {
                    Icon(
                        imageVector = Icons.Filled.Bookmark,
                        contentDescription = stringResource(ephyra.app.core.common.R.string.action_filter_bookmarked),
                        modifier = Modifier
                            .sizeIn(maxHeight = with(LocalDensity.current) { textHeight.toDp() - 2.dp }),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                Text(
                    text = update.chapterName,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = textAlpha),
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { textHeight = it.size.height },
                    modifier = Modifier
                        .weight(weight = 1f, fill = false),
                )
                if (readProgress != null) {
                    DotSeparatorText()
                    Text(
                        text = readProgress,
                        maxLines = 1,
                        color = LocalContentColor.current.copy(alpha = DISABLED_ALPHA),
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        ChapterDownloadIndicator(
            enabled = onDownloadChapter != null,
            modifier = Modifier.padding(start = 4.dp),
            downloadStateProvider = downloadStateProvider,
            downloadProgressProvider = downloadProgressProvider,
            onClick = { onDownloadChapter?.invoke(it) },
        )
    }
}
