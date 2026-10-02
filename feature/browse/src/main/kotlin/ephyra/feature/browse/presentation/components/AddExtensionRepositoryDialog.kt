package ephyra.feature.browse.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Adds an extension repository.
 *
 * **Why there is no "web source" option any more.** There used to be one, and it was a trap: every
 * caller wired it to something that could not work. Two closed the dialog and said nothing, and the
 * third dispatched an event whose handler was `AddWebSource -> Unit`. So a user who typed a site
 * URL, chose "Web Source" (or let "Auto-detect" pick it for them) and tapped Add got a dialog that
 * closed and no other feedback at all — the worst outcome available, because it reads as the app
 * having accepted the request. The heuristic engine the option meant to invoke was removed in
 * `ADR-0015`; the option outlived it and kept offering a feature that was already gone.
 *
 * **What replaces it, and when.** A source that is not an extension APK arrives as its own *working*
 * add flow, the way Jellyfin will be: a real branch with a real action behind it, not a chip that
 * quietly does nothing. Until one exists, this dialog says what it does and only that.
 */
@Composable
fun AddExtensionRepositoryDialog(
    onDismissRequest: () -> Unit,
    onAddRepo: (String) -> Unit,
) {
    var urlInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "Add Extension Repository",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Repository URL") },
                    placeholder = { Text("https://.../index.min.json") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Link,
                            contentDescription = null,
                        )
                    },
                )

                Text(
                    // Says what the app actually does, and names the other way in, so someone who
                    // came here to add a website is sent to the path that works instead of being
                    // left to guess why nothing happened.
                    text = "A repository publishes extension APKs, which Ephyra installs and keeps " +
                        "updated. To use a site that has no extension, import its APK directly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = urlInput.trim()
                    if (trimmed.isNotBlank()) {
                        onAddRepo(trimmed)
                        onDismissRequest()
                    }
                },
                enabled = urlInput.isNotBlank(),
            ) {
                Text("Add Repository")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        },
    )
}
