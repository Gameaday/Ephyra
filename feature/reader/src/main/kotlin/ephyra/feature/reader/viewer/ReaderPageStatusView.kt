package ephyra.feature.reader.viewer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ephyra.feature.reader.ReaderCopyReportButton
import ephyra.feature.reader.errorDetails
import ephyra.feature.reader.errorHeadline

/**
 * Shared loading chrome for the pager ([ephyra.feature.reader.viewer.pager.ZoomableMangaPage])
 * and webtoon ([ephyra.feature.reader.viewer.webtoon.ComposeWebtoonReader]) readers.
 *
 * A `progress` of `0` renders an indeterminate spinner (the queued / in-transfer
 * states carry no byte offset); a non-zero value renders a determinate one for the
 * downloading state. Used by both readers' [Page.State.Queue], [Page.State.LoadPage]
 * and [Page.State.DownloadImage] branches instead of duplicating the spinner box.
 */
@Composable
fun ReaderPageLoadingView(
    modifier: Modifier = Modifier,
    progress: Int,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val safeProgress = progress.coerceIn(0, 100)
        if (safeProgress > 0) {
            CircularProgressIndicator(
                progress = { safeProgress / 100f },
                modifier = Modifier.size(48.dp),
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
        }
    }
}

/**
 * Shared error chrome for both readers: a warning icon, the error message and a
 * retry affordance.
 *
 * `retryContent` lets a reader inject a backoff-aware retry affordance without the pager
 * depending on webtoon internals; when null a plain "Retry" [OutlinedButton] is shown. The page
 * image itself is never rendered here — each reader owns the [Page.State.Ready] branch.
 * Replaces the icon + message + retry blocks that were duplicated in the pager and webtoon
 * status `when`.
 */
@Composable
fun ReaderPageErrorView(
    modifier: Modifier = Modifier,
    error: Throwable,
    pageNumber: Int,
    onRetry: () -> Unit,
    retryContent: (@Composable () -> Unit)? = null,
) {
    val message = error.message ?: "Failed to load page $pageNumber"
    val details = remember(message) { errorDetails(message) }
    var expanded by rememberSaveable(message) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = errorHeadline(message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (retryContent != null) {
            retryContent()
        } else {
            OutlinedButton(onClick = onRetry) {
                Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(text = "Retry")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // The diagnosis sits behind a disclosure rather than inline. Printed in full these messages
        // push the buttons off a phone screen, and a reader who cannot reach Retry cannot act at all.
        if (details.isNotEmpty()) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide details" else "Show details")
            }
        }

        ReaderCopyReportButton(message)

        if (expanded && details.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(modifier = Modifier.padding(12.dp)) {
                    // Monospace and horizontally scrollable: field names, addresses and UUIDs, and
                    // wrapping them across lines makes a value impossible to read back accurately.
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}
