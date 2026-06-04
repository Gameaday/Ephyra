package ephyra.feature.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.domain.content.source.SourceType
import ephyra.domain.content.source.model.UnifiedSource
import ephyra.presentation.core.components.AppBar
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.ui.navigation.LocalNavController

@Composable
fun SourceManagementScreen(
    navController: NavController = LocalNavController.current,
) {
    val ViewModel = hiltViewModel<SourceManagementViewModel>()
    val sources by ViewModel.sources.collectAsStateWithLifecycle()
    val isLoading by ViewModel.isLoading.collectAsStateWithLifecycle()
    val error by ViewModel.error.collectAsStateWithLifecycle()

    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var showAddJsScraperDialog by remember { mutableStateOf(false) }
    var showImportJsScraperDialog by remember { mutableStateOf(false) }
    var showAddHeuristicDialog by remember { mutableStateOf(false) }
    var showLinkScraperDialog by remember { mutableStateOf(false) }
    var showRemoveConfirmDialog by remember { mutableStateOf(false) }
    var selectedSourceToRemove by remember { mutableStateOf<UnifiedSource?>(null) }
    var selectedSourceForLink by remember { mutableStateOf<UnifiedSource?>(null) }

    var githubUrl by remember { mutableStateOf("") }
    var scraperFilename by remember { mutableStateOf("") }
    var importFilename by remember { mutableStateOf("") }
    var importScriptContent by remember { mutableStateOf("") }
    var heuristicUrl by remember { mutableStateOf("") }
    var heuristicName by remember { mutableStateOf("") }
    var linkBaseUrl by remember { mutableStateOf("") }
    var linkScraperName by remember { mutableStateOf("") }

    LaunchedEffect(error) {
        if (error != null) {
            snackbarMessage = error
            ViewModel.clearError()
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
                onAddJsScraper = { showAddJsScraperDialog = true },
                onImportJsScraper = { showImportJsScraperDialog = true },
                onAddHeuristic = { showAddHeuristicDialog = true },
                onLinkScraper = { source ->
                    selectedSourceForLink = source
                    linkBaseUrl = source.baseUrl
                    linkScraperName = ""
                    showLinkScraperDialog = true
                },
                onRefresh = { ViewModel.loadSources() },
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
                onCheckUpdates = { source ->
                    ViewModel.checkAndUpdateScraper(source.baseUrl)
                },
                onForceRediscover = { source ->
                    ViewModel.forceRediscover(source.baseUrl)
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
                                ViewModel.removeSource(source.baseUrl)
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

        // Add JS Scraper Dialog
        if (showAddJsScraperDialog) {
            AddJsScraperDialog(
                onDismiss = {
                    showAddJsScraperDialog = false
                    githubUrl = ""
                    scraperFilename = ""
                },
                onConfirm = { url, name ->
                    ViewModel.addJsScraper(url, name)
                    showAddJsScraperDialog = false
                    githubUrl = ""
                    scraperFilename = ""
                },
                githubUrl = githubUrl,
                onGithubUrlChange = { githubUrl = it },
                scraperFilename = scraperFilename,
                onScraperFilenameChange = { scraperFilename = it },
            )
        }

        // Import JS Scraper Dialog
        if (showImportJsScraperDialog) {
            ImportJsScraperDialog(
                onDismiss = {
                    showImportJsScraperDialog = false
                    importFilename = ""
                    importScriptContent = ""
                },
                onConfirm = { name, content ->
                    ViewModel.importJsScraper(name, content)
                    showImportJsScraperDialog = false
                    importFilename = ""
                    importScriptContent = ""
                },
                filename = importFilename,
                onFilenameChange = { importFilename = it },
                scriptContent = importScriptContent,
                onScriptContentChange = { importScriptContent = it },
            )
        }

        // Add Heuristic Profile Dialog
        if (showAddHeuristicDialog) {
            AddHeuristicDialog(
                onDismiss = {
                    showAddHeuristicDialog = false
                    heuristicUrl = ""
                    heuristicName = ""
                },
                onConfirm = { url, name ->
                    ViewModel.addHeuristicProfile(url, name.ifBlank { null })
                    showAddHeuristicDialog = false
                    heuristicUrl = ""
                    heuristicName = ""
                },
                url = heuristicUrl,
                onUrlChange = { heuristicUrl = it },
                name = heuristicName,
                onNameChange = { heuristicName = it },
            )
        }

        // Link Scraper Dialog
        if (showLinkScraperDialog) {
            LinkScraperDialog(
                onDismiss = {
                    showLinkScraperDialog = false
                    linkBaseUrl = ""
                    linkScraperName = ""
                    selectedSourceForLink = null
                },
                onConfirm = { baseUrl, scraperName ->
                    ViewModel.linkScraperToUrl(baseUrl, scraperName)
                    showLinkScraperDialog = false
                    linkBaseUrl = ""
                    linkScraperName = ""
                    selectedSourceForLink = null
                },
                baseUrl = linkBaseUrl,
                onBaseUrlChange = { linkBaseUrl = it },
                scraperName = linkScraperName,
                onScraperNameChange = { linkScraperName = it },
                availableScrapers = sources
                    .filter { it.sourceType == SourceType.JS_SCRAPER }
                    .map { it.name },
            )
        }
    }
}

@Composable
private fun SourceManagementLayout(
    contentPadding: PaddingValues,
    sources: List<UnifiedSource>,
    onAddJsScraper: () -> Unit,
    onImportJsScraper: () -> Unit,
    onAddHeuristic: () -> Unit,
    onLinkScraper: (UnifiedSource) -> Unit,
    onRefresh: () -> Unit,
    onSourceClick: (UnifiedSource) -> Unit,
    onSourceLongClick: (UnifiedSource) -> Unit,
    onCheckUpdates: (UnifiedSource) -> Unit,
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
                icon = Icons.Outlined.Code,
                label = "Add JS Scraper",
                color = MaterialTheme.colorScheme.primary,
                onClick = onAddJsScraper,
            )
            QuickActionButton(
                icon = Icons.Outlined.UploadFile,
                label = "Import Script",
                color = MaterialTheme.colorScheme.secondary,
                onClick = onImportJsScraper,
            )
            QuickActionButton(
                icon = Icons.Outlined.Autorenew,
                label = "Add Heuristic",
                color = MaterialTheme.colorScheme.tertiary,
                onClick = onAddHeuristic,
            )
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
            SourceType.LEGACY_EXTENSION,
            SourceType.JS_SCRAPER,
            SourceType.HEURISTIC,
            SourceType.REPOSITORY,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(typeOrder) { sourceType ->
                val typeSources = grouped[sourceType] ?: emptyList()
                if (typeSources.isNotEmpty()) {
                    item {
                        SourceTypeSection(
                            sourceType = sourceType,
                            sources = typeSources,
                            onSourceClick = onSourceClick,
                            onSourceLongClick = onSourceLongClick,
                            onLinkScraper = onLinkScraper,
                            onCheckUpdates = onCheckUpdates,
                            onForceRediscover = onForceRediscover,
                            onRemoveSource = onRemoveSource,
                        )
                    }
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
                                textAlign = androidx.compose.ui.text.TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionButton(
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
            borderColor = color.copy(alpha = 0.5f),
        ),
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
    onLinkScraper: (UnifiedSource) -> Unit,
    onCheckUpdates: (UnifiedSource) -> Unit,
    onForceRediscover: (UnifiedSource) -> Unit,
    onRemoveSource: (UnifiedSource) -> Unit,
) {
    val (icon, color) = when (sourceType) {
        SourceType.LEGACY_EXTENSION -> Icons.Outlined.Security to MaterialTheme.colorScheme.primary
        SourceType.JS_SCRAPER -> Icons.Outlined.Code to MaterialTheme.colorScheme.secondary
        SourceType.HEURISTIC -> Icons.Outlined.Autorenew to MaterialTheme.colorScheme.tertiary
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
                        onLinkScraper = { onLinkScraper(source) },
                        onCheckUpdates = { onCheckUpdates(source) },
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
    onLinkScraper: () -> Unit,
    onCheckUpdates: () -> Unit,
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
                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
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
                if (source.sourceType == SourceType.HEURISTIC || source.sourceType == SourceType.REPOSITORY) {
                    IconButton(onClick = onLinkScraper) {
                        Icon(
                            imageVector = Icons.Outlined.Link,
                            contentDescription = "Link scraper",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (source.sourceType == SourceType.JS_SCRAPER) {
                    IconButton(onClick = onCheckUpdates) {
                        Icon(
                            imageVector = Icons.Outlined.CloudDownload,
                            contentDescription = "Check for updates",
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                IconButton(onClick = onForceRediscover) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Force rediscover",
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
                if (source.sourceType != SourceType.LEGACY_EXTENSION) {
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

// Dialog Composables
@Composable
private fun AddJsScraperDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
    githubUrl: String,
    onGithubUrlChange: (String) -> Unit,
    scraperFilename: String,
    onScraperFilenameChange: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add JS Scraper from GitHub") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = githubUrl,
                    onValueChange = onGithubUrlChange,
                    label = { Text("GitHub URL") },
                    placeholder = { Text("https://github.com/user/repo/scraper.js") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = scraperFilename,
                    onValueChange = onScraperFilenameChange,
                    label = { Text("Filename") },
                    placeholder = { Text("mangadex_scraper.js") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "The script will be downloaded and sandboxed. Auto-updates enabled if from GitHub.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (githubUrl.isNotBlank() && scraperFilename.isNotBlank()) {
                    onConfirm(githubUrl, scraperFilename)
                }
            }) {
                Text("Download")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ImportJsScraperDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
    filename: String,
    onFilenameChange: (String) -> Unit,
    scriptContent: String,
    onScriptContentChange: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Local JS Script") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = filename,
                    onValueChange = onFilenameChange,
                    label = { Text("Script Name") },
                    placeholder = { Text("custom_scraper.js") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = scriptContent,
                    onValueChange = onScriptContentChange,
                    label = { Text("JavaScript Content") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    shape = RoundedCornerShape(8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (filename.isNotBlank() && scriptContent.isNotBlank()) {
                    onConfirm(filename, scriptContent)
                }
            }) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
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

@Composable
private fun LinkScraperDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    scraperName: String,
    onScraperNameChange: (String) -> Unit,
    availableScrapers: List<String>,
) {
    var dropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link Scraper to Website") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = onBaseUrlChange,
                    label = { Text("Website Base URL") },
                    placeholder = { Text("https://example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (availableScrapers.isNotEmpty()) {
                    Box {
                        OutlinedTextField(
                            value = scraperName,
                            onValueChange = onScraperNameChange,
                            label = { Text("Select Scraper") },
                            placeholder = { Text("Choose a scraper...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            trailingIcon = {
                                IconButton(onClick = { dropdownExpanded = true }) {
                                    Icon(
                                        imageVector = if (dropdownExpanded) {
                                            Icons.Outlined.ExpandLess
                                        } else {
                                            Icons.Outlined.ExpandMore
                                        },
                                        contentDescription = "Show available scrapers",
                                    )
                                }
                            },
                            readOnly = false,
                        )
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                        ) {
                            availableScrapers.forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        onScraperNameChange(name)
                                        dropdownExpanded = false
                                    },
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = scraperName,
                        onValueChange = onScraperNameChange,
                        label = { Text("Scraper Filename") },
                        placeholder = { Text("No JS scrapers available. Add one first.") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (baseUrl.isNotBlank() && scraperName.isNotBlank()) {
                    onConfirm(baseUrl, scraperName)
                }
            }) {
                Text("Link")
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
        SourceType.LEGACY_EXTENSION -> "Legacy Extensions"
        SourceType.JS_SCRAPER -> "JS Scrapers"
        SourceType.HEURISTIC -> "Heuristic Profiles"
        SourceType.REPOSITORY -> "Repositories"
    }

private val SourceType.color: Color
    get() = when (this) {
        SourceType.LEGACY_EXTENSION -> MaterialTheme.colorScheme.primary
        SourceType.JS_SCRAPER -> MaterialTheme.colorScheme.secondary
        SourceType.HEURISTIC -> MaterialTheme.colorScheme.tertiary
        SourceType.REPOSITORY -> MaterialTheme.colorScheme.outline
    }