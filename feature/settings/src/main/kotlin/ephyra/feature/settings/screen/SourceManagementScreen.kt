package ephyra.feature.settings.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.domain.content.source.SourceType
import ephyra.domain.content.source.interactor.UnifiedSource
import ephyra.presentation.core.components.AppBar
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.ui.navigation.LocalNavController

@Composable
fun SourceManagementScreen(
    navController: NavController = LocalNavController.current,
) {
    val viewModel = hiltViewModel<SourceManagementViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sources = state.sources
    val isLoading = state.isLoading
    val error = state.error

    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var showRemoveConfirmDialog by remember { mutableStateOf(false) }
    var selectedSourceToRemove by remember { mutableStateOf<UnifiedSource?>(null) }

    LaunchedEffect(error) {
        if (error != null) {
            snackbarMessage = error
            viewModel.onEvent(SourceManagementEvent.ClearError)
        }
    }

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = "Source Management",
                navigateUp = { navController.popBackStack() },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            SourceManagementLayout(
                contentPadding = contentPadding,
                sources = sources,

                onRefresh = { viewModel.onEvent(SourceManagementEvent.LoadSources) },
                onSourceClick = { source ->
                    snackbarMessage = buildString {
                        appendLine("Source: ${source.name}")
                        appendLine("URL: ${source.baseUrl}")
                        appendLine("Type: ${source.sourceType.displayName}")
                        appendLine("Enabled: ${source.enabled}")
                        if (source.failureCount > 0) {
                            appendLine("Failures: ${source.failureCount}")
                        }
                        if (source.extensionId != null) {
                            appendLine("Extension: ${source.extensionId}")
                        }
                    }
                },
                onSourceLongClick = { source ->
                    snackbarMessage = "Long-press actions coming soon for ${source.name}"
                },
                onForceRediscover = { source ->
                    viewModel.onEvent(SourceManagementEvent.ForceRediscover(source.baseUrl))
                },
                onRemoveSource = { source ->
                    selectedSourceToRemove = source
                    showRemoveConfirmDialog = true
                },
            )
        }

        // Notification dialog
        snackbarMessage?.let { msg ->
            AlertDialog(
                onDismissRequest = { snackbarMessage = null },
                title = { Text("Notification") },
                text = { Text(msg) },
                confirmButton = {
                    TextButton(onClick = { snackbarMessage = null }) {
                        Text("OK")
                    }
                },
            )
        }

        // Remove source confirmation dialog
        if (showRemoveConfirmDialog && selectedSourceToRemove != null) {
            AlertDialog(
                onDismissRequest = {
                    showRemoveConfirmDialog = false
                    selectedSourceToRemove = null
                },
                title = { Text("Remove Source") },
                text = {
                    Text(
                        "Are you sure you want to remove \"${selectedSourceToRemove!!.name}\"? " +
                            "This action cannot be undone.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            selectedSourceToRemove?.let { source ->
                                viewModel.onEvent(SourceManagementEvent.RemoveSource(source.baseUrl))
                            }
                            showRemoveConfirmDialog = false
                            selectedSourceToRemove = null
                        },
                    ) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showRemoveConfirmDialog = false
                            selectedSourceToRemove = null
                        },
                    ) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

@Composable
private fun SourceManagementLayout(
    contentPadding: PaddingValues,
    sources: List<UnifiedSource>,

    onRefresh: () -> Unit,
    onSourceClick: (UnifiedSource) -> Unit,
    onSourceLongClick: (UnifiedSource) -> Unit,
    onForceRediscover: (UnifiedSource) -> Unit,
    onRemoveSource: (UnifiedSource) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        // Quick Actions Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickActionButton(
                icon = Icons.Outlined.Refresh,
                label = "Refresh All",
                color = MaterialTheme.colorScheme.outline,
                onClick = onRefresh,
            )
        }

        // Sources grouped by type
        val grouped = sources.groupBy { it.sourceType }
        val typeOrder = listOf(
            SourceType.REMOTE_EXTENSION,
            SourceType.REPOSITORY,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(typeOrder) { sourceType ->
                val typeSources = grouped[sourceType] ?: emptyList()
                if (typeSources.isNotEmpty()) {
                    SourceTypeSection(
                        sourceType = sourceType,
                        sources = typeSources,
                        onSourceClick = onSourceClick,
                        onSourceLongClick = onSourceLongClick,
                        onForceRediscover = onForceRediscover,
                        onRemoveSource = onRemoveSource,
                    )
                }
            }

            if (sources.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No sources configured",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Add a JS scraper, import a script, or create a " +
                                    "heuristic profile to get started",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .height(80.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            contentColor = color,
        ),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SourceTypeSection(
    sourceType: SourceType,
    sources: List<UnifiedSource>,
    onSourceClick: (UnifiedSource) -> Unit,
    onSourceLongClick: (UnifiedSource) -> Unit,
    onForceRediscover: (UnifiedSource) -> Unit,
    onRemoveSource: (UnifiedSource) -> Unit,
) {
    val (icon, color) = when (sourceType) {
        SourceType.REMOTE_EXTENSION -> Icons.Outlined.Security to MaterialTheme.colorScheme.primary
        SourceType.REPOSITORY -> Icons.Outlined.Storage to MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .background(color.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = sourceType.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(color.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = sources.size.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Source items
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sources.forEach { source ->
                    SourceRow(
                        source = source,
                        onClick = { onSourceClick(source) },
                        onLongClick = { onSourceLongClick(source) },
                        onForceRediscover = { onForceRediscover(source) },
                        onRemoveSource = { onRemoveSource(source) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    source: UnifiedSource,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onForceRediscover: () -> Unit,
    onRemoveSource: () -> Unit,
) {
    val statusColor = if (source.enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (source.enabled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status indicator
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .background(statusColor, RoundedCornerShape(2.dp)),
            )
            Spacer(modifier = Modifier.width(12.dp))

            // Source info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = source.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (!source.enabled) {
                        Text(
                            text = "DISABLED",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = source.baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Source type badge
                    Box(
                        modifier = Modifier
                            .background(
                                source.sourceType.color.copy(alpha = 0.2f),
                                RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = source.sourceType.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = source.sourceType.color,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    if (source.extensionId != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "Extension: ${source.extensionId}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }

                    if (source.failureCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                                    RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Row {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(10.dp),
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "${source.failureCount} failures",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }

            // Actions
            Column(
                horizontalAlignment = Alignment.End,
            ) {
                IconButton(onClick = onForceRediscover) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Force rediscover",
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
                if (source.sourceType != SourceType.REMOTE_EXTENSION) {
                    IconButton(onClick = onRemoveSource) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Remove source",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddHeuristicDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit,
    url: String,
    onUrlChange: (String) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Heuristic Profile") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    label = { Text("Website Base URL") },
                    placeholder = { Text("https://example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Display Name (optional)") },
                    placeholder = { Text("Auto-detected from page title") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "The adaptive heuristic engine will analyze the page structure on first use.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (url.isNotBlank()) {
                    onConfirm(url, name.ifBlank { null })
                }
            }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

// Extensions
private val SourceType.displayName: String
    get() = when (this) {
        SourceType.REMOTE_EXTENSION -> "Extension Sources"
        SourceType.REPOSITORY -> "Repositories"
    }

private val SourceType.color: Color
    @Composable
    get() = when (this) {
        SourceType.REMOTE_EXTENSION -> MaterialTheme.colorScheme.primary
        SourceType.REPOSITORY -> MaterialTheme.colorScheme.outline
    }
