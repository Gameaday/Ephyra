package ephyra.feature.browse.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun UniversalAddSourceDialog(
    onDismissRequest: () -> Unit,
    onAddRepo: (String) -> Unit,
    onAddWebSource: (String, String?) -> Unit,
) {
    var urlInput by remember { mutableStateOf("") }
    var sourceNameInput by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(AddSourceType.AUTO) }

    val isRepoUrl = remember(urlInput, selectedType) {
        if (selectedType == AddSourceType.EXTENSION_REPO) return@remember true
        if (selectedType == AddSourceType.WEB_SOURCE) return@remember false
        val trimmed = urlInput.trim()
        trimmed.startsWith("tachiyomi://", ignoreCase = true) ||
            trimmed.startsWith("mihon://", ignoreCase = true) ||
            trimmed.startsWith("ephyra://", ignoreCase = true) ||
            trimmed.endsWith(".json", ignoreCase = true) ||
            trimmed.endsWith(".pb", ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "Add Repository or Source",
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
                // Type selector chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedType == AddSourceType.AUTO,
                        onClick = { selectedType = AddSourceType.AUTO },
                        label = { Text("Auto-detect") },
                    )
                    FilterChip(
                        selected = selectedType == AddSourceType.EXTENSION_REPO,
                        onClick = { selectedType = AddSourceType.EXTENSION_REPO },
                        label = { Text("Extension Repo") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Extension,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                    FilterChip(
                        selected = selectedType == AddSourceType.WEB_SOURCE,
                        onClick = { selectedType = AddSourceType.WEB_SOURCE },
                        label = { Text("Web Source") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Public,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }

                // URL Input Field
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("URL or Deep Link") },
                    placeholder = {
                        Text(
                            if (isRepoUrl) {
                                "https://.../index.min.json or add-repo deep link"
                            } else {
                                "https://example.com"
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Link,
                            contentDescription = null,
                        )
                    },
                )

                // Optional Display Name for Web Sources
                if (!isRepoUrl) {
                    OutlinedTextField(
                        value = sourceNameInput,
                        onValueChange = { sourceNameInput = it },
                        label = { Text("Display Name (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }

                // Information Helper Text
                Text(
                    text = if (isRepoUrl) {
                        "Supports community extension repository URLs and add-repo deep links."
                    } else {
                        "Direct website URL for automatic extraction using the content engine."
                    },
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
                        if (isRepoUrl) {
                            onAddRepo(trimmed)
                        } else {
                            onAddWebSource(trimmed, sourceNameInput.trim().ifBlank { null })
                        }
                        onDismissRequest()
                    }
                },
                enabled = urlInput.isNotBlank(),
            ) {
                Text(if (isRepoUrl) "Add Repository" else "Add Source")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        },
    )
}

enum class AddSourceType {
    AUTO,
    EXTENSION_REPO,
    WEB_SOURCE,
}
