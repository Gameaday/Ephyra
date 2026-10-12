package ephyra.presentation.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ephyra.presentation.core.i18n.stringResource

/*
 * Banner colors come from the active Material 3 color scheme, not from hardcoded literals.
 *
 * They used to be three fixed `Color(0x...)` values with white text, which meant the banners
 * ignored all 17 themes (and every branded theme's translucency) and could not adapt to a light or
 * dark surface. Each banner keeps a distinct role so the three states remain distinguishable.
 */

@Composable
fun AppStateBanners(
    downloadedOnlyMode: Boolean,
    incognitoMode: Boolean,
    indexing: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (indexing) {
            IndexingBanner()
        }
        if (downloadedOnlyMode) {
            DownloadedOnlyModeBanner()
        }
        if (incognitoMode) {
            IncognitoModeBanner()
        }
    }
}

@Composable
private fun IndexingBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .padding(end = 16.dp)
                .size(16.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            strokeWidth = 2.dp,
        )
        Text(
            text = stringResource(ephyra.app.core.common.R.string.loading),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun DownloadedOnlyModeBanner() {
    Text(
        text = stringResource(ephyra.app.core.common.R.string.label_downloaded_only),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

@Composable
private fun IncognitoModeBanner() {
    Text(
        text = stringResource(ephyra.app.core.common.R.string.pref_incognito_mode),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
    )
}
