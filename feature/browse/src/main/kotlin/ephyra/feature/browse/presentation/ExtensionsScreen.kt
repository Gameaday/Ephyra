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
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.material3.Surface
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
import androidx.navigation.NavController
import ephyra.domain.content.source.SourceType
import ephyra.domain.content.source.interactor.UnifiedSource
import ephyra.domain.extension.model.Extension
import ephyra.domain.extension.model.LoadFailureReason
import ephyra.domain.extensionrepo.model.ExtensionRepo
import ephyra.feature.browse.extension.ExtensionsViewModel
import ephyra.feature.browse.presentation.components.AddExtensionRepositoryDialog
import ephyra.presentation.core.components.ExtensionIcon
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.Screen
import ephyra.presentation.core.ui.navigation.ScreenRoutes

@Composable
fun ExtensionScreen(
    state: ExtensionsViewModel.State,
    contentPadding: PaddingValues,
    searchQuery: String?,
    onForceRediscover: (String) -> Unit,
    onRemoveSource: (String) -> Unit,
    onRefresh: () -> Unit,
    onAddRepository: (String) -> Unit,
    onDeleteRepository: (String) -> Unit,
    onInstallExtension: (Extension.Available, Set<String>?) -> Unit,
    onUninstallExtension: (Extension.Available) -> Unit,
    onTrustExtension: (Extension.Untrusted) -> Unit = {},
    onUninstallByPkgName: (String) -> Unit = {},
    onUpdateExtension: (Extension.Installed) -> Unit = {},
    onUninstallInstalledExtension: (Extension.Installed) -> Unit = {},
    navController: NavController = LocalNavController.current,
) {
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var showRemoveConfirmDialog by remember { mutableStateOf(false) }
    var showAddRepoDialog by remember { mutableStateOf(false) }
    var selectedSourceToRemove by remember { mutableStateOf<UnifiedSource?>(null) }
    var showUninstallConfirmDialog by remember { mutableStateOf(false) }
    var selectedExtensionToUninstall by remember { mutableStateOf<Extension.Installed?>(null) }
    var showDeleteRepoConfirmDialog by remember { mutableStateOf(false) }
    var selectedRepoToDelete by remember { mutableStateOf<ExtensionRepo?>(null) }

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

        val filteredAvailableExtensions = remember(state.availableExtensions, searchQuery) {
            if (searchQuery.isNullOrBlank()) {
                state.availableExtensions
            } else {
                state.availableExtensions.filter { ext ->
                    ext.name.contains(searchQuery, ignoreCase = true) ||
                        ext.pkgName.contains(searchQuery, ignoreCase = true) ||
                        ext.lang.contains(searchQuery, ignoreCase = true) ||
                        ext.sources.any {
                            it.name.contains(searchQuery, ignoreCase = true) ||
                                it.baseUrl.contains(searchQuery, ignoreCase = true)
                        }
                }
            }
        }

        val filteredInstalledExtensions = remember(state.installedExtensions, searchQuery) {
            if (searchQuery.isNullOrBlank()) {
                state.installedExtensions
            } else {
                state.installedExtensions.filter { ext ->
                    ext.name.contains(searchQuery, ignoreCase = true) ||
                        ext.pkgName.contains(searchQuery, ignoreCase = true) ||
                        ext.lang.contains(searchQuery, ignoreCase = true) ||
                        ext.sources.any { it.name.contains(searchQuery, ignoreCase = true) }
                }
            }
        }

        ExtensionScraperManagementLayout(
            contentPadding = contentPadding,
            sources = filteredSources,
            repos = state.repos,
            availableExtensions = filteredAvailableExtensions,
            installedExtensions = filteredInstalledExtensions,
            untrustedExtensions = state.untrustedExtensions,
            failedExtensions = state.failedExtensions,
            onAddRepoClick = { showAddRepoDialog = true },
            onDeleteRepoClick = { url ->
                selectedRepoToDelete = state.repos.firstOrNull { it.baseUrl == url }
                showDeleteRepoConfirmDialog = true
            },
            onInstallExtensionClick = { ext -> onInstallExtension(ext, null) },
            onUninstallExtensionClick = onUninstallExtension,
            onUpdateExtension = onUpdateExtension,
            onUninstallInstalledExtension = { ext ->
                selectedExtensionToUninstall = ext
                showUninstallConfirmDialog = true
            },
            onTrustExtensionClick = onTrustExtension,
            onUninstallByPkgName = onUninstallByPkgName,
            onClickExtension = { pkgName -> navController.navigate(Screen.ExtensionDetails(pkgName)) },
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
            onForceRediscover = { source ->
                onForceRediscover(source.baseUrl)
            },
            onRemoveSource = { source ->
                selectedSourceToRemove = source
                showRemoveConfirmDialog = true
            },
            navController = navController,
            searchQuery = searchQuery,
        )
    }

    // Universal Add Repository & Source Dialog
    if (showAddRepoDialog) {
        AddExtensionRepositoryDialog(
            onDismissRequest = { showAddRepoDialog = false },
            onAddRepo = { repoUrl ->
                onAddRepository(repoUrl)
                showAddRepoDialog = false
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

    // Uninstall installed extension confirmation dialog
    if (showUninstallConfirmDialog && selectedExtensionToUninstall != null) {
        AlertDialog(
            onDismissRequest = {
                showUninstallConfirmDialog = false
                selectedExtensionToUninstall = null
            },
            title = { Text("Uninstall Extension") },
            text = {
                Text(
                    "Are you sure you want to uninstall \"${selectedExtensionToUninstall!!.name}\"? " +
                        "This action cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedExtensionToUninstall?.let { ext ->
                            onUninstallInstalledExtension(ext)
                        }
                        showUninstallConfirmDialog = false
                        selectedExtensionToUninstall = null
                    },
                ) {
                    Text("Uninstall", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showUninstallConfirmDialog = false
                        selectedExtensionToUninstall = null
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    // Delete repository confirmation dialog
    if (showDeleteRepoConfirmDialog && selectedRepoToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteRepoConfirmDialog = false
                selectedRepoToDelete = null
            },
            title = { Text("Delete Repository") },
            text = {
                Text(
                    "Are you sure you want to delete the repository \"${selectedRepoToDelete!!.name}\"? " +
                        "This action cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedRepoToDelete?.let { repo ->
                            onDeleteRepository(repo.baseUrl)
                        }
                        showDeleteRepoConfirmDialog = false
                        selectedRepoToDelete = null
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteRepoConfirmDialog = false
                        selectedRepoToDelete = null
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ExtensionScraperManagementLayout(
    contentPadding: PaddingValues,
    sources: List<UnifiedSource>,
    repos: List<ExtensionRepo>,
    availableExtensions: List<Extension.Available>,
    installedExtensions: List<Extension.Installed>,
    untrustedExtensions: List<Extension.Untrusted>,
    failedExtensions: List<Extension.Failed>,
    onAddRepoClick: () -> Unit,
    onDeleteRepoClick: (String) -> Unit,
    onInstallExtensionClick: (Extension.Available) -> Unit,
    onUninstallExtensionClick: (Extension.Available) -> Unit,
    onUpdateExtension: (Extension.Installed) -> Unit,
    onUninstallInstalledExtension: (Extension.Installed) -> Unit,
    onTrustExtensionClick: (Extension.Untrusted) -> Unit,
    onUninstallByPkgName: (String) -> Unit,
    onClickExtension: (String) -> Unit,
    onRefresh: () -> Unit,
    onSourceClick: (UnifiedSource) -> Unit,
    onForceRediscover: (UnifiedSource) -> Unit,
    onRemoveSource: (UnifiedSource) -> Unit,
    navController: NavController,
    searchQuery: String? = null,
) {
    var showDevTools by remember { mutableStateOf(false) }
    // O(1) lookup per row; the previous per-row firstOrNull was O(n*m) across the section.
    val installedByPackage = remember(installedExtensions) {
        installedExtensions.associateBy { it.pkgName }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Empty State Card or Active Repositories Section
        if (repos.isEmpty()) {
            item {
                EmptyRepositoriesCard(
                    onAddRepo = onAddRepoClick,
                )
            }
        } else {
            item {
                RepositoriesSection(
                    repos = repos,
                    onAddRepo = onAddRepoClick,
                    onManageRepos = { navController.navigate(ScreenRoutes.ExtensionRepos.route) },
                    onDeleteRepo = onDeleteRepoClick,
                )
            }
        }

        // Installed Extensions (Sandboxed DEX)
        if (installedExtensions.isNotEmpty()) {
            item {
                InstalledExtensionsSection(
                    installedExtensions = installedExtensions,
                    onUninstall = onUninstallInstalledExtension,
                    onUpdate = onUpdateExtension,
                    onClickExtension = onClickExtension,
                )
            }
        }

        // Untrusted extensions
        if (untrustedExtensions.isNotEmpty()) {
            item {
                UntrustedExtensionsSection(
                    untrustedExtensions = untrustedExtensions,
                    onTrust = onTrustExtensionClick,
                    onUninstall = onUninstallByPkgName,
                )
            }
        }

        // Failed extensions
        if (failedExtensions.isNotEmpty()) {
            item {
                FailedExtensionsSection(
                    failedExtensions = failedExtensions,
                    onUninstall = onUninstallByPkgName,
                )
            }
        }

        // Available Extensions (from connected repos).
        //
        // The header is one item and the extensions are lazy items, so a large repository only
        // composes the rows near the viewport instead of every row at once.
        if (repos.isNotEmpty()) {
            item(key = "available_extensions_header") {
                AvailableExtensionsHeader(
                    availableExtensionCount = availableExtensions.size,
                    onRefresh = onRefresh,
                    searchQuery = searchQuery,
                )
            }
            items(
                items = availableExtensions,
                key = { it.pkgName },
            ) { ext ->
                val installedExt = installedByPackage[ext.pkgName]
                ExtensionItemRow(
                    extension = ext,
                    installedExtension = installedExt,
                    onInstall = { onInstallExtensionClick(ext) },
                    onUninstall = { installedExt?.let(onUninstallInstalledExtension) },
                    onUpdate = { installedExt?.let(onUpdateExtension) },
                    onClickExtension = { onClickExtension(ext.pkgName) },
                )
            }
        }

        // Developer & Custom Script Tools (Collapsible)
        item {
            DeveloperToolsSection(
                isExpanded = showDevTools,
                onToggleExpand = { showDevTools = !showDevTools },
                sources = sources,
                onSourceClick = onSourceClick,
                onForceRediscover = onForceRediscover,
                onRemoveSource = onRemoveSource,
            )
        }

        // Empty state when absolutely nothing is configured
        if (sources.isEmpty() && repos.isEmpty() && availableExtensions.isEmpty() &&
            installedExtensions.isEmpty() && untrustedExtensions.isEmpty() && failedExtensions.isEmpty()
        ) {
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
                            text = "Add an extension repository or custom source to get started",
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

@Composable
private fun EmptyRepositoriesCard(
    onAddRepo: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "No Extension Repositories",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "Add a community extension repository URL or deep link to discover and " +
                    "install content extensions in a sandboxed runner.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onAddRepo,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Link,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Repository")
            }
        }
    }
}

@Composable
private fun DeveloperToolsSection(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    sources: List<UnifiedSource>,
    onSourceClick: (UnifiedSource) -> Unit,
    onForceRediscover: (UnifiedSource) -> Unit,
    onRemoveSource: (UnifiedSource) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Developer & Custom Script Tools",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InstalledExtensionsSection(
    installedExtensions: List<Extension.Installed>,
    onUninstall: (Extension.Installed) -> Unit,
    onUpdate: (Extension.Installed) -> Unit,
    onClickExtension: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Extension,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Installed Extensions (${installedExtensions.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                installedExtensions.forEach { ext ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onClickExtension(ext.pkgName) },
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
                            ExtensionIcon(
                                extension = ext,
                                modifier = Modifier.size(44.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ext.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = buildString {
                                        append("v${ext.versionName}")
                                        append(" · ${ext.sources.size} source(s)")
                                        if (ext.lang.isNotBlank()) append(" · ${ext.lang.uppercase()}")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            if (ext.hasUpdate) {
                                Button(
                                    onClick = { onUpdate(ext) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                ) {
                                    Text("Update", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            IconButton(onClick = { onClickExtension(ext.pkgName) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onUninstall(ext) }) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = "Uninstall",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UntrustedExtensionsSection(
    untrustedExtensions: List<Extension.Untrusted>,
    onTrust: (Extension.Untrusted) -> Unit,
    onUninstall: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.GppMaybe,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Needs Trust (${untrustedExtensions.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                untrustedExtensions.forEach { ext ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ext.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "v${ext.versionName} · signature not trusted — sources hidden until trusted",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onTrust(ext) }) {
                            Text("Approve")
                        }
                        IconButton(onClick = { onUninstall(ext.pkgName) }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Uninstall",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FailedExtensionsSection(
    failedExtensions: List<Extension.Failed>,
    onUninstall: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Couldn't Load (${failedExtensions.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                failedExtensions.forEach { ext ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ext.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = buildString {
                                    append(ext.displayText())
                                    ext.detail?.let { append(" — $it") }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onUninstall(ext.pkgName) }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Uninstall",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Extension.Failed.displayText(): String = when (reason) {
    LoadFailureReason.PACKAGE_NOT_FOUND -> "Package not found"
    LoadFailureReason.NO_METADATA -> "Invalid extension metadata"
    LoadFailureReason.MISSING_VERSION_NAME -> "Missing version name"
    LoadFailureReason.UNSUPPORTED_LIB_VERSION -> "Unsupported extension API version"
    LoadFailureReason.UNSIGNED -> "Extension APK is not signed"
    LoadFailureReason.NSFW_NOT_ALLOWED -> "Disabled by NSFW setting"
    LoadFailureReason.CLASSLOADER_ERROR -> "Failed to load extension code"
    LoadFailureReason.SOURCE_INSTANTIATION_FAILED -> "Failed to create sources"
    LoadFailureReason.UNEXPECTED_ERROR -> "Unexpected error"
}

@Composable
private fun RepositoriesSection(
    repos: List<ExtensionRepo>,
    onAddRepo: () -> Unit,
    onManageRepos: () -> Unit,
    onDeleteRepo: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
                    text = "Repositories (${repos.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onAddRepo) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
                TextButton(onClick = onManageRepos) {
                    Text("Manage")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
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

/**
 * Header for the available-extensions section.
 *
 * The rows are emitted as lazy items by the parent `LazyColumn`, not rendered here. This section
 * used to be one `item { }` containing a `forEach` over every available extension, so a repository
 * with a few hundred extensions composed and measured all of them the moment the section scrolled
 * into view -- the reported "slow loading when scrolling into available extensions". Only the
 * header is eager now; rows compose as they approach the viewport.
 */
@Composable
private fun AvailableExtensionsHeader(
    availableExtensionCount: Int,
    onRefresh: () -> Unit,
    searchQuery: String? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
                    text = "Available Extensions",
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
                        text = availableExtensionCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (availableExtensionCount == 0) {
                val message = if (!searchQuery.isNullOrBlank()) {
                    "No extensions match \"$searchQuery\""
                } else {
                    "No extensions found in registered repos. Click 'Refresh Repositories' to update."
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (searchQuery.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Refresh Repositories")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtensionItemRow(
    extension: Extension.Available,
    installedExtension: Extension.Installed?,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onUpdate: () -> Unit,
    onClickExtension: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (installedExtension != null) {
                    Modifier.clickable(onClick = onClickExtension)
                } else {
                    Modifier
                },
            ),
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
            ExtensionIcon(
                extension = extension,
                modifier = Modifier.size(44.dp),
            )
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
                    text = "v${extension.versionName} · ${extension.sources.size} source(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (installedExtension != null) {
                if (installedExtension.hasUpdate) {
                    Button(
                        onClick = onUpdate,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        Text("Update", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = onClickExtension,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                        ) {
                            Text("Installed", style = MaterialTheme.typography.labelMedium)
                        }
                        IconButton(onClick = onUninstall) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Uninstall",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            } else {
                Button(
                    onClick = onInstall,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Text("Install", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/**
 * A human label for a source type.
 *
 * Kept here rather than on [SourceType] itself because it is a presentation concern and the enum
 * lives in `:core:domain`, which knows nothing about wording. It is an exhaustive `when` on
 * purpose: adding a type to the enum is meant to force a decision about how it is named here, and
 * `SourceTypeRegistryStructuralTest` fails the build if the enum grows and this does not.
 *
 * `REPOSITORY` is the Jellyfin slot. It is listed because it is a real declared type, not because
 * anything can currently be one -- the engine registry is empty, so a `REPOSITORY` profile raises
 * `NoEngineBoundException` rather than resolving. Naming it now means the label is ready when
 * something can actually answer to it.
 */
private val SourceType.displayName: String
    get() = when (this) {
        SourceType.REMOTE_EXTENSION -> "Extension Sources"
        SourceType.REPOSITORY -> "Repositories"
    }
