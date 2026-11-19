package ephyra.feature.library.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import ephyra.domain.manga.model.SourceStatus
import ephyra.presentation.core.components.AuthorityBrandColors
import ephyra.presentation.core.components.Badge
import ephyra.presentation.theme.EphyraPreviewTheme

@Composable
internal fun DownloadsBadge(count: Long) {
    if (count > 0) {
        Badge(
            text = "$count",
            color = MaterialTheme.colorScheme.tertiary,
            textColor = MaterialTheme.colorScheme.onTertiary,
        )
    }
}

@Composable
internal fun UnreadBadge(count: Long) {
    if (count > 0) {
        Badge(text = "$count")
    }
}

@Composable
internal fun LanguageBadge(
    isLocal: Boolean,
    sourceLanguage: String,
) {
    if (isLocal) {
        Badge(
            imageVector = Icons.Outlined.Folder,
            color = MaterialTheme.colorScheme.tertiary,
            iconColor = MaterialTheme.colorScheme.onTertiary,
        )
    } else if (sourceLanguage.isNotEmpty()) {
        Badge(
            text = sourceLanguage.uppercase(),
            color = MaterialTheme.colorScheme.tertiary,
            textColor = MaterialTheme.colorScheme.onTertiary,
        )
    }
}

@Composable
internal fun SourceHealthBadge(sourceStatus: Int) {
    val status = SourceStatus.fromValue(sourceStatus)
    when (status) {
        SourceStatus.DEAD -> Badge(
            imageVector = Icons.Outlined.Warning,
            color = MaterialTheme.colorScheme.error,
            iconColor = MaterialTheme.colorScheme.onError,
            contentDescription = stringResource(ephyra.app.core.common.R.string.source_health_warning_dead),
        )

        SourceStatus.DEGRADED -> Badge(
            imageVector = Icons.Outlined.Warning,
            color = MaterialTheme.colorScheme.tertiary,
            iconColor = MaterialTheme.colorScheme.onTertiary,
            contentDescription = stringResource(ephyra.app.core.common.R.string.source_health_warning_degraded),
        )

        else -> {}
    }
}

@Composable
internal fun AuthorityBadge(hasCanonicalId: Boolean, canonicalId: String? = null) {
    if (hasCanonicalId) {
        val brandColor = authorityBrandColor(canonicalId)
        Badge(
            imageVector = Icons.Outlined.Verified,
            color = brandColor ?: MaterialTheme.colorScheme.primary,
            iconColor = if (brandColor != null) Color.White else MaterialTheme.colorScheme.onPrimary,
            contentDescription = stringResource(ephyra.app.core.common.R.string.authority_badge_description),
        )
    }
}

/**
 * Returns the brand color for a known authority, or null for unknown prefixes.
 * Used by both library badges and the series detail page authority badge.
 *
 * The values live in [AuthorityBrandColors] because `feature:manga` needs the same map and neither
 * feature may depend on the other. This file and `MangaInfoHeader.kt` each carried a byte-identical
 * copy of the map before, which is how one brand colour could change in one place and not the other.
 */
internal fun authorityBrandColor(canonicalId: String?): Color? =
    AuthorityBrandColors.forAuthority(canonicalId)

/**
 * Returns a gradient [Brush] for authorities that use a multi-color brand identity,
 * or null when the authority uses a single solid color. Currently only Jellyfin
 * has a gradient (purple → blue, matching the Jellyfin logo).
 */
internal fun authorityBrandGradient(canonicalId: String?): Brush? {
    val colors = AuthorityBrandColors.gradientFor(canonicalId) ?: return null
    return Brush.horizontalGradient(colors)
}

@PreviewLightDark
@Composable
private fun BadgePreview() {
    EphyraPreviewTheme {
        Column {
            DownloadsBadge(count = 10)
            UnreadBadge(count = 10)
            LanguageBadge(isLocal = true, sourceLanguage = "EN")
            LanguageBadge(isLocal = false, sourceLanguage = "EN")
            SourceHealthBadge(sourceStatus = SourceStatus.DEAD.value)
            SourceHealthBadge(sourceStatus = SourceStatus.DEGRADED.value)
            AuthorityBadge(hasCanonicalId = true)
        }
    }
}
