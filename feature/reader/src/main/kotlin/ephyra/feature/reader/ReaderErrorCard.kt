package ephyra.feature.reader

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ephyra.presentation.core.util.system.copyToClipboard
import ephyra.presentation.core.util.system.readRecentLog
import kotlinx.coroutines.launch

/**
 * Composes a pasted failure report: the exception, then the log that led to it.
 *
 * **Why the log is copied rather than summarised.** Every attempt to diagnose a source from the
 * exception message alone failed, because the message says what was rejected while the log says what
 * happened on the way there — which list was fetched, how many addresses it carried, whether a cached
 * list was discarded, what the retry ladder decided. Maintaining those fields inside the exception
 * means remembering to add each one; copying the log means they are all there by default.
 *
 * The message leads, so the report is still readable if the log could not be read at all. When it
 * cannot, no heading is emitted — a report claiming evidence it does not contain is worse than none.
 */
fun buildErrorReport(message: String, log: String): String {
    val trimmedLog = log.trim()
    return when {
        message.isBlank() -> trimmedLog
        trimmedLog.isEmpty() -> message
        else -> "$message\n\n$LOG_HEADING\n$trimmedLog"
    }
}

private const val LOG_HEADING = "--- recent log ---"

/**
 * The one-line summary of a failure, without the diagnosis.
 *
 * A message from this pipeline is `"<what> (<why>):\n<value>"`, optionally followed by a blank line
 * and a `"Why this was rejected:"` block. The first part is what failed; the rest is evidence. Both
 * hosts — the chapter-level card and the per-page view — split it the same way, which is why this is
 * one function rather than two parsers that can disagree.
 */
fun errorHeadline(message: String): String =
    message.substringBefore("\n\n").substringBefore("\n:").trim()
        .ifEmpty { "Something went wrong loading this." }

/** The diagnostic block, or empty when the message carries none. */
fun errorDetails(message: String): String = message.substringAfter("\n\n", "").trim()

/**
 * Copies a failure report — the message plus the recent log — to the clipboard.
 *
 * Shared by both hosts so the report is defined once. Everything about it lives in [buildErrorReport]
 * and [readRecentLog]; this is only the affordance.
 */
@Composable
fun ReaderCopyReportButton(
    message: String,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    OutlinedButton(
        modifier = modifier,
        onClick = {
            scope.launch {
                context.copyToClipboard("Reader error", buildErrorReport(message, readRecentLog()))
            }
        },
    ) {
        Icon(Icons.Outlined.ContentCopy, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text("Copy report")
    }
}

/**
 * The reader's failure state, shown as a card rather than as the raw exception text.
 *
 * **Why it is not a raw [Text].** The image pipeline reports what it rejected and where the value came
 * from, because every attempt to diagnose a source from a one-line error failed. Those fields are far
 * too long for a phone screen — pasted into the message they made the common case unreadable. So the
 * first line stays visible as the headline and the diagnosis lives behind a disclosure, with a copy
 * button so it can be pasted into a report rather than transcribed by hand from a screenshot.
 */
@Composable
fun ReaderErrorCard(
    message: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The message is "<what> (<why>):\n<value>" followed by an optional "\n\nWhy this was rejected:\n…"
    // block. Only the first part is the headline; the rest is evidence.
    val headline = message.substringBefore("\n\n").substringBefore("\n:").trim()
    val details = remember(message) { message.substringAfter("\n\n", "").trim() }
    val hasDetails = details.isNotEmpty()

    var expanded by rememberSaveable(message) { mutableStateOf(false) }
    var copied by remember(message) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = headline.ifEmpty { "Something went wrong loading this." },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (hasDetails) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide details" else "Show details")
                }
            }

            // Full report on the clipboard: the diagnosis is worthless if it has to be retyped.
            OutlinedButton(
                onClick = {
                    // The whole report, not the message: `logcat` read on IO, then copied through the
                    // shared helper so the failure toast and error handling come with it.
                    scope.launch {
                        context.copyToClipboard("Reader error", buildErrorReport(message, readRecentLog()))
                    }
                    copied = true
                },
            ) {
                Text(if (copied) "Copied" else "Copy details")
            }

            Button(onClick = onNavigateBack) {
                Text("Go Back")
            }
        }
    }

    if (expanded && hasDetails) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(modifier = Modifier.padding(12.dp)) {
                // Monospace and horizontally scrollable: these are field names, addresses and UUIDs, and
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

@Composable
private fun ErrorCenteredColumn(
    horizontalAlignment: Alignment.Horizontal,
    content: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = horizontalAlignment,
        content = { content() },
    )
}
