package ephyra.feature.library.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ephyra.core.common.preference.TriState
import ephyra.presentation.core.i18n.stringResource

enum class LibraryFilterType {
    Unread,
    Downloaded,
    Started,
    Bookmarked,
    Completed,
    SourceHealthDead,
}

@Immutable
data class LibraryFilterChipItem(
    val type: LibraryFilterType,
    val titleRes: Int,
    val state: TriState,
    val icon: ImageVector,
    val count: Int? = null,
)

@Composable
fun LibraryFilterChips(
    unreadState: TriState,
    downloadedState: TriState,
    startedState: TriState,
    bookmarkedState: TriState,
    completedState: TriState,
    sourceHealthState: TriState,
    deadSourceCount: Int,
    degradedSourceCount: Int,
    onToggleFilter: (LibraryFilterType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = buildList {
        add(
            LibraryFilterChipItem(
                type = LibraryFilterType.Unread,
                titleRes = ephyra.app.core.common.R.string.action_filter_unread,
                state = unreadState,
                icon = Icons.Outlined.VisibilityOff,
            ),
        )
        add(
            LibraryFilterChipItem(
                type = LibraryFilterType.Downloaded,
                titleRes = ephyra.app.core.common.R.string.label_downloaded,
                state = downloadedState,
                icon = Icons.Outlined.Download,
            ),
        )
        add(
            LibraryFilterChipItem(
                type = LibraryFilterType.Started,
                titleRes = ephyra.app.core.common.R.string.label_started,
                state = startedState,
                icon = Icons.Outlined.PlayArrow,
            ),
        )
        add(
            LibraryFilterChipItem(
                type = LibraryFilterType.Bookmarked,
                titleRes = ephyra.app.core.common.R.string.action_filter_bookmarked,
                state = bookmarkedState,
                icon = Icons.Outlined.Bookmark,
            ),
        )
        add(
            LibraryFilterChipItem(
                type = LibraryFilterType.Completed,
                titleRes = ephyra.app.core.common.R.string.completed,
                state = completedState,
                icon = Icons.Outlined.CheckCircle,
            ),
        )
        if (deadSourceCount > 0 || degradedSourceCount > 0 || sourceHealthState != TriState.DISABLED) {
            val totalProblemCount = deadSourceCount + degradedSourceCount
            add(
                LibraryFilterChipItem(
                    type = LibraryFilterType.SourceHealthDead,
                    titleRes = ephyra.app.core.common.R.string.action_filter_source_health_dead,
                    state = sourceHealthState,
                    icon = Icons.Outlined.Warning,
                    count = totalProblemCount.takeIf { it > 0 },
                ),
            )
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val isSelected = item.state != TriState.DISABLED
            val isExcluded = item.state == TriState.ENABLED_NOT

            FilterChip(
                selected = isSelected,
                onClick = { onToggleFilter(item.type) },
                label = {
                    val text = if (item.count != null) {
                        "${stringResource(item.titleRes)} (${item.count})"
                    } else {
                        stringResource(item.titleRes)
                    }
                    Text(text = text)
                },
                leadingIcon = {
                    val icon = when {
                        isExcluded -> Icons.Default.Close
                        isSelected -> Icons.Default.Check
                        else -> item.icon
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
                colors = if (item.type == LibraryFilterType.SourceHealthDead && isSelected) {
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                } else {
                    FilterChipDefaults.filterChipColors()
                },
            )
        }
    }
}
