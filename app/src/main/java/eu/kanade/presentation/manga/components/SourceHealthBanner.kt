package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import tachiyomi.domain.manga.model.SourceStatus
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Banner displayed on the manga detail screen when the source health is not HEALTHY.
 * Shows a warning for DEGRADED sources and an error for DEAD sources.
 * When [deadSince] is provided for DEAD sources, shows how long the source has been dead.
 */
@Composable
fun SourceHealthBanner(
    sourceStatus: SourceStatus,
    modifier: Modifier = Modifier,
    deadSince: Long? = null,
) {
    if (sourceStatus == SourceStatus.HEALTHY || sourceStatus == SourceStatus.REPLACED) return

    val containerColor = when (sourceStatus) {
        SourceStatus.DEAD -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = when (sourceStatus) {
        SourceStatus.DEAD -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    val text = when (sourceStatus) {
        SourceStatus.DEAD -> {
            val baseText = stringResource(MR.strings.source_health_dead)
            val durationText = deadSince?.let { formatDeadDuration(it) }
            if (durationText != null) "$baseText ($durationText)" else baseText
        }
        else -> stringResource(MR.strings.source_health_degraded)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * Formats a dead_since timestamp into a human-readable duration string.
 */
internal fun formatDeadDuration(deadSince: Long): String? {
    if (deadSince <= 0) return null
    val elapsed = System.currentTimeMillis() - deadSince
    if (elapsed < 0) return null
    val days = elapsed / (24 * 60 * 60 * 1000)
    return when {
        days >= 1 -> "${days}d"
        else -> "<1d"
    }
}
