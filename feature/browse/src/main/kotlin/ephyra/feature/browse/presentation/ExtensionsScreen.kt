package ephyra.feature.browse.presentation

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dagger.hilt.android.EntryPointAccessors
import ephyra.domain.content.source.SourceType
import ephyra.domain.content.source.interactor.UnifiedSource
import ephyra.domain.extension.model.Extension
import ephyra.domain.extensionrepo.interactor.CreateExtensionRepo
import ephyra.domain.extensionrepo.model.ExtensionRepo
import ephyra.feature.browse.BrowseEntryPoint
import ephyra.feature.browse.extension.ExtensionsViewModel
import ephyra.presentation.core.ui.navigation.LocalNavController

@Composable
fun ExtensionScreen(
    state: ExtensionsViewModel.State,
    contentPadding: PaddingValues,
    searchQuery: String?,
    onAddJsScraper: (String, String) -> Unit,
    onImportJsScraper: (String, String) -> Unit,
    onAddHeuristic: (String, String?) -> Unit,
    onLinkScraper: (String, String) -> Unit,
    onCheckUpdates: (String) -> Unit,
    onForceRediscover: (String) -> Unit,
    onRemoveSource: (String) -> Unit,
    onRefresh: () -> Unit,
    onAddRepository: (String) -> Unit,
    onDeleteRepository: (String) -> Unit,
    onInstallExtension: (Extension.Available, Set<String>?) -> Unit,
    onUninstallExtension: (Extension.Available) -> Unit,
    navController: NavController = LocalNavController.current,
) {
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var showAddJsScraperDialog by remember { mutableStateOf(false) }
    var showImportJsScraperDialog by remember { mutableStateOf(false) }
    var showAddHeuristicDialog by remember { mutableStateOf(false) }
    var showLinkScraperDialog by remember { mutableStateOf(false) }
    var showRemoveConfirmDialog by remember { mutableStateOf(false) }
    var showAddRepoDialog by remember { mutableStateOf(false) }
    var showExtensionSourcesDialog by remember { mutableStateOf<Extension.Available?>(null) }
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
    var repoUrlInput by remember { mutableStateOf("") }

    LaunchedEffect(state.error) {
        if (state.error != null) {
            snackbarMessage = state.error
        }
    }

    if (state.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    } else {
        val filteredSources = remember(state.sources, searchQuery) {
            if (searchQuery.isNullOrBlank()) {
                state.sources
            } else {
                state.sources.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                        it.baseUrl.contains(searchQuery, ignoreCase = true)
                }
            }
        }

        ExtensionScraperManagementLayout(
            contentPadding = contentPadding,
            sources = filteredSources,
            repos = state.repos,
            availableExtensions = state.availableExtensions,
            onAddJsScraperClick = { showAddJsScraperDialog = true },
            onImportJsScraperClick = { showImportJsScraperDialog = true },
            onAddHeuristicClick = { showAddHeuristicDialog = true },
            onAddRepoClick = { showAddRepoDialog = true },
            onDeleteRepoClick = onDeleteRepository,
            onInstallExtensionClick = { ext -> showExtensionSourcesDialog = ext },
            onUninstallExtensionClick = onUninstallExtension,
            onLinkScraperClick = { source ->
                selectedSourceForLink = source
                linkBaseUrl = source.baseUrl
                linkScraperName = ""
                showLinkScraperDialog = true
            },
            onRefresh = onRefresh,
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
            onCheckUpdates = { source ->
                onCheckUpdates(source.baseUrl)
            },
            onForceRediscover = { source ->
                onForceRediscover(source.baseUrl)
            },
            onRemoveSource = { source ->
                selectedSourceToRemove = source
                showRemoveConfirmDialog = true
            },
        )
    }

    showExtensionSourcesDialog?.let { ext ->
        ExtensionSourcesDialog(
            extension = ext,
            installedSources = state.sources,
            onDismiss = { showExtensionSourcesDialog = null },
            onConfirm = { selectedUrls ->
                onInstallExtension(ext, selectedUrls)
            },
        )
    }

    // Add Repository Dialog
    if (showAddRepoDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddRepoDialog = false
                repoUrlInput = ""
            },
            title = { Text("Add Repository") },
            text = {
                Column {
                    OutlinedTextField(
                        value = repoUrlInput,
                        onValueChange = { repoUrlInput = it },
                        label = { Text("Repository URL") },
                        placeholder = { Text("https://example.com/repo.json") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                    )
                    val context = LocalContext.current
                    val appInfo = remember(context) {
                        EntryPointAccessors.fromApplication(
                            context.applicationContext,
                            BrowseEntryPoint::class.java,
                        ).appInfo()
                    }
                    // Hard-coded catalog shortcuts (e.g. the official Mihon repo)
                    // are a piracy liability in shipped builds; only dev builds
                    // built with -Pinclude-catalog-shortcuts expose them.
                    if (appInfo.catalogShortcutsEnabled) {
                        TextButton(
                            onClick = { repoUrlInput = CreateExtensionRepo.OFFICIAL_MIHON_REPO_URL },
                        ) {
                            Text("Use official Mihon repo")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (repoUrlInput.isNotBlank()) {
                            onAddRepository(repoUrlInput)
                        }
                        showAddRepoDialog = false
                        repoUrlInput = ""
                    },
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddRepoDialog = false
                        repoUrlInput = ""
                    },
                ) {
                    Text("Cancel")
                }
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
                            onRemoveSource(source.baseUrl)
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
                onAddJsScraper(url, name)
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
                onImportJsScraper(name, content)
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
                onAddHeuristic(url, name?.ifBlank { null })
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
                onLinkScraper(baseUrl, scraperName)
                showLinkScraperDialog = false
                linkBaseUrl = ""
                linkScraperName = ""
                selectedSourceForLink = null
            },
            baseUrl = linkBaseUrl,
            onBaseUrlChange = { linkBaseUrl = it },
            scraperName = linkScraperName,
            onScraperNameChange = { linkScraperName = it },
            availableScrapers = state.sources
                .filter { it.sourceType == SourceType.JS_SCRAPER }
                .map { it.name },
        )
    }
}

@Composable
private fun ExtensionScraperManagementLayout(
    contentPadding: PaddingValues,
    sources: List<UnifiedSource>,
    repos: List<ExtensionRepo>,
    availableExtensions: List<Extension.Available>,
    onAddJsScraperClick: () -> Unit,
    onImportJsScraperClick: () -> Unit,
    onAddHeuristicClick: () -> Unit,
    onAddRepoClick: () -> Unit,
    onDeleteRepoClick: (String) -> Unit,
    onInstallExtensionClick: (Extension.Available) -> Unit,
    onUninstallExtensionClick: (Extension.Available) -> Unit,
    onLinkScraperClick: (UnifiedSource) -> Unit,
    onRefresh: () -> Unit,
    onSourceClick: (UnifiedSource) -> Unit,
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
                onClick = onAddJsScraperClick,
            )
            QuickActionButton(
                icon = Icons.Outlined.UploadFile,
                label = "Import Script",
                color = MaterialTheme.colorScheme.secondary,
                onClick = onImportJsScraperClick,
            )
            QuickActionButton(
                icon = Icons.Outlined.Autorenew,
                label = "Add Heuristic",
                color = MaterialTheme.colorScheme.tertiary,
                onClick = onAddHeuristicClick,
            )
            QuickActionButton(
                icon = Icons.Outlined.Storage,
                label = "Add Repo",
                color = MaterialTheme.colorScheme.outline,
                onClick = onAddRepoClick,
            )
        }

        // Sources grouped by type
        val grouped = sources.groupBy { it.sourceType }
        val typeOrder = listOf(
            SourceType.JS_SCRAPER,
            SourceType.HEURISTIC,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Repositories section
            if (repos.isNotEmpty()) {
                item {
                    RepositoriesSection(
                        repos = repos,
                        onDeleteRepo = onDeleteRepoClick,
                    )
                }
            }

            // Installed sources
            items(typeOrder) { sourceType ->
                val typeSources = grouped[sourceType] ?: emptyList()
                if (typeSources.isNotEmpty()) {
                    SourceTypeSection(
                        sourceType = sourceType,
                        sources = typeSources,
                        onSourceClick = onSourceClick,
                        onLinkScraper = onLinkScraperClick,
                        onCheckUpdates = onCheckUpdates,
                        onForceRediscover = onForceRediscover,
                        onRemoveSource = onRemoveSource,
                    )
                }
            }

            // Available extensions
            if (repos.isNotEmpty()) {
                item {
                    AvailableExtensionsSection(
                        availableExtensions = availableExtensions,
                        installedSources = sources,
                        onInstall = onInstallExtensionClick,
                        onUninstall = onUninstallExtensionClick,
                    )
                }
            }

            if (sources.isEmpty() && repos.isEmpty() && availableExtensions.isEmpty()) {
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
                                text = "Add a repository URL, JS scraper, or heuristic profile to get started",
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
private fun RepositoriesSection(
    repos: List<ExtensionRepo>,
    onDeleteRepo: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Extension Repositories",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repos.forEach { repo ->
                    RepoRow(repo = repo, onDelete = { onDeleteRepo(repo.baseUrl) })
                }
            }
        }
    }
}

@Composable
private fun RepoRow(
    repo: ExtensionRepo,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = repo.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = repo.baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete Repo",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AvailableExtensionsSection(
    availableExtensions: List<Extension.Available>,
    installedSources: List<UnifiedSource>,
    onInstall: (Extension.Available) -> Unit,
    onUninstall: (Extension.Available) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Available Repository Extensions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = availableExtensions.size.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (availableExtensions.isEmpty()) {
                Text(
                    text = "No extensions found in registered repos. Click 'Refresh All' to search.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableExtensions.forEach { ext ->
                        val isInstalled = installedSources.any { unified ->
                            unified.sourceType == SourceType.JS_SCRAPER &&
                                ext.sources.any { it.baseUrl == unified.baseUrl }
                        }
                        ExtensionItemRow(
                            extension = ext,
                            isInstalled = isInstalled,
                            onInstall = { onInstall(ext) },
                            onUninstall = { onUninstall(ext) },
                            onManageSources = { onInstall(ext) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtensionItemRow(
    extension: Extension.Available,
    isInstalled: Boolean,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onManageSources: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = extension.name.take(2).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = extension.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = extension.lang.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "v" + extension.versionName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (isInstalled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onManageSources,
                        shape = RoundedCornerShape(8.dp),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        Text("Sources", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = onUninstall,
                        shape = RoundedCornerShape(8.dp),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        Text("Uninstall", style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onInstall,
                    shape = RoundedCornerShape(8.dp),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text("Install", style = MaterialTheme.typography.labelMedium)
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

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sources.forEach { source ->
                    SourceRow(
                        source = source,
                        onClick = { onSourceClick(source) },
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
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .background(statusColor, RoundedCornerShape(2.dp)),
            )
            Spacer(modifier = Modifier.width(12.dp))

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

private val SourceType.displayName: String
    get() = when (this) {
        SourceType.LEGACY_EXTENSION -> "Legacy Extensions"
        SourceType.JS_SCRAPER -> "JS Scrapers"
        SourceType.HEURISTIC -> "Heuristic Profiles"
        SourceType.REPOSITORY -> "Repositories"
    }

private val SourceType.color: Color
    @Composable
    get() = when (this) {
        SourceType.LEGACY_EXTENSION -> MaterialTheme.colorScheme.primary
        SourceType.JS_SCRAPER -> MaterialTheme.colorScheme.secondary
        SourceType.HEURISTIC -> MaterialTheme.colorScheme.tertiary
        SourceType.REPOSITORY -> MaterialTheme.colorScheme.outline
    }

@Composable
private fun ExtensionSourcesDialog(
    extension: Extension.Available,
    installedSources: List<UnifiedSource>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    val initiallySelected = remember(extension, installedSources) {
        val mapped = extension.sources.filter { source ->
            installedSources.any { it.baseUrl == source.baseUrl && it.sourceType == SourceType.JS_SCRAPER }
        }.map { it.baseUrl }.toSet()
        if (mapped.isEmpty()) {
            extension.sources.map { it.baseUrl }.toSet()
        } else {
            mapped
        }
    }

    var selectedUrls by remember { mutableStateOf(initiallySelected) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Manage ${extension.name}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Select which sources to use from this extension:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = selectedUrls.size == extension.sources.size,
                        onCheckedChange = { checked ->
                            selectedUrls = if (checked) {
                                extension.sources.map { it.baseUrl }.toSet()
                            } else {
                                emptySet()
                            }
                        },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Select All", style = MaterialTheme.typography.bodyMedium)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(extension.sources) { source ->
                        val isChecked = selectedUrls.contains(source.baseUrl)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedUrls = if (isChecked) {
                                        selectedUrls - source.baseUrl
                                    } else {
                                        selectedUrls + source.baseUrl
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    selectedUrls = if (checked) {
                                        selectedUrls + source.baseUrl
                                    } else {
                                        selectedUrls - source.baseUrl
                                    }
                                },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(text = source.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "${source.lang.uppercase()} • ${source.baseUrl}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(selectedUrls)
                    onDismiss()
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
