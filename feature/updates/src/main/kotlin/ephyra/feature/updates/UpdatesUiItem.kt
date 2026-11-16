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
import androidx.compose.ui.res.pluralStringResource
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
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.util.animateItemFastScroll
import ephyra.presentation.core.util.relativeTimeSpanString
import ephyra.presentation.core.util.selectedBackground

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
    selectionMode: Boolean,
    expandedSeriesIds: Set<Long>,
    onUpdateSelected: (UpdatesItem, Boolean, Boolean) -> Unit,
    onClickCover: (UpdatesItem) -> Unit,
    onClickUpdate: (UpdatesItem) -> Unit,
    onDownloadChapter: (List<UpdatesItem>, ChapterDownloadAction) -> Unit,
    onToggleSeries: (mangaId: Long) -> Unit,
) {
    items(
        items = uiModels.withExpandedSeriesChildren(expandedSeriesIds),
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
                is UpdatesUiModel.Series -> "updates-series-${it.latest.update.mangaId}"
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
                UpdatesItemRow(
                    modifier = Modifier.Companion.animateItemFastScroll(),
                    updatesItem = item.item,
                    selectionMode = selectionMode,
                    onUpdateSelected = onUpdateSelected,
                    onClickCover = onClickCover,
                    onClickUpdate = onClickUpdate,
                    onDownloadChapter = onDownloadChapter,
                )
            }
            is UpdatesUiModel.Series -> {
                // Only the latest update and the expand toggle render here. When the series
                // is expanded, its older updates are top-level keyed lazy items (see
                // [withExpandedSeriesChildren]), so only the visible ones compose and each
                // keeps its own key and animateItemFastScroll.
                Column(Modifier.Companion.animateItemFastScroll()) {
                    UpdatesItemRow(
                        updatesItem = item.latest,
                        selectionMode = selectionMode,
                        onUpdateSelected = onUpdateSelected,
                        onClickCover = onClickCover,
                        onClickUpdate = onClickUpdate,
                        onDownloadChapter = onDownloadChapter,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleSeries(item.latest.update.mangaId) }
                            .padding(horizontal = MaterialTheme.padding.medium, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (item.latest.update.mangaId in expandedSeriesIds) {
                                Icons.Filled.ExpandLess
                            } else {
                                Icons.Filled.ExpandMore
                            },
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = pluralStringResource(
                                ephyra.app.core.common.R.plurals.updates_series_more_chapters,
                                item.older.size,
                                item.older.size,
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

/**
 * Flattens expanded series groups into top-level lazy items.
 *
 * The Updates screen stacks a series' chapter updates behind one row (the History screen's
 * series-stacking shape). When a group is expanded, its older updates are lifted into
 * [UpdatesUiModel.Item]s of their own, filed directly after their series row, so the lazy
 * list only composes the visible ones, item identity survives list changes, and the children
 * animate like every other row. Collapsed series pass through untouched. The children reuse
 * the plain-item key (`updates-<mangaId>-<chapterId>`), which is unambiguous because grouping
 * is by `mangaId`: an update is either its group's latest (the [UpdatesUiModel.Series] row)
 * or one of its older children — never both.
 */
internal fun List<UpdatesUiModel>.withExpandedSeriesChildren(
    expandedSeriesIds: Set<Long>,
): List<UpdatesUiModel> = flatMap { entry ->
    if (entry is UpdatesUiModel.Series && entry.latest.update.mangaId in expandedSeriesIds) {
        listOf(entry) + entry.older.map { UpdatesUiModel.Item(it) }
    } else {
        listOf(entry)
    }
}

/** The shared row renderer for a plain update and a stacked series' latest update. */
@Composable
private fun UpdatesItemRow(
    updatesItem: UpdatesItem,
    selectionMode: Boolean,
    onUpdateSelected: (UpdatesItem, Boolean, Boolean) -> Unit,
    onClickCover: (UpdatesItem) -> Unit,
    onClickUpdate: (UpdatesItem) -> Unit,
    onDownloadChapter: (List<UpdatesItem>, ChapterDownloadAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = updatesItem
    UpdatesUiItem(
        modifier = modifier,
        update = item.update,
        selected = item.selected,
        readProgress = item.update.lastPageRead
            .takeIf { !item.update.read && it > 0L }
            ?.let {
                stringResource(
                    ephyra.app.core.common.R.string.chapter_progress,
                    it + 1,
                )
            },
        onLongClick = {
            onUpdateSelected(item, !item.selected, true)
        },
        onClick = {
            when {
                selectionMode -> onUpdateSelected(item, !item.selected, false)
                else -> onClickUpdate(item)
            }
        },
        onClickCover = { onClickCover(item) }.takeIf { !selectionMode },
        onDownloadChapter = { action: ChapterDownloadAction ->
            onDownloadChapter(listOf(item), action)
        }.takeIf { !selectionMode },
        downloadStateProvider = item.downloadStateProvider,
        downloadProgressProvider = item.downloadProgressProvider,
    )
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
