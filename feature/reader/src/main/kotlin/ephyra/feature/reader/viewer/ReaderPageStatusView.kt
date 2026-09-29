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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ephyra.presentation.core.util.formattedMessage

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
 *
 * **Why the message goes through [formattedMessage].** The raw `Throwable.message` of a failed page
 * load is whatever the network stack produced, and for the failure users actually hit — a name that
 * does not resolve — that is a resolver string naming an internal CDN host
 * (`Unable to resolve host "cmxd….network": No address associated with hostname`). The same text
 * appears whether the device has no connectivity, the source is down, or one host is gone, and it
 * tells the reader none of those apart. `formattedMessage` is the app's one formatter for this
 * (already used for browse errors) and does discriminate what it can: a resolution failure on a
 * device with no connectivity becomes "No Internet connection" rather than a resolver string, a
 * `403`/`410` becomes an HTTP status, and a Cloudflare challenge becomes the challenge notice.
 *
 * It does **not** invent a cause it cannot see — with connectivity present and only one host dead,
 * the resolver string is still the honest text, and the value here is the offline case plus the
 * typed failures. A Retry above "No Internet connection" is worth pressing; one above a resolver
 * string is worth pressing *because the URL is re-resolved*, which is the `DEF-023` fix in
 * [ephyra.feature.reader.loader.HttpPageLoader.retryPage] and not something this view can express.
 */
@Composable
fun ReaderPageErrorView(
    modifier: Modifier = Modifier,
    error: Throwable,
    pageNumber: Int,
    onRetry: () -> Unit,
    retryContent: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val message = remember(error) {
        with(context) { error.formattedMessage }
    }.ifBlank { "Failed to load page $pageNumber" }

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
            text = message,
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
