package ephyra.feature.reader.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
            text = error.message ?: "Failed to load page $pageNumber",
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
    }
}
