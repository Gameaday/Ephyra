package ephyra.feature.browse.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LeakAdd
import androidx.compose.material.icons.outlined.NetworkLocked
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.domain.content.source.DataField
import ephyra.domain.content.source.SourceProfile
import ephyra.feature.browse.source.sourcing.ContentSourcingScreenModel
import ephyra.feature.browse.source.sourcing.RepositoryItem
import ephyra.feature.browse.source.sourcing.ScraperItem
import ephyra.presentation.core.components.AppBar
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.core.ui.navigation.LocalNavController
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ContentSourcingScreen(
    navController: NavController = LocalNavController.current,
) {
    val screenModel = hiltViewModel<ContentSourcingScreenModel>()
    val state by screenModel.state.collectAsStateWithLifecycle()

    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        screenModel.effects.collectLatest { effect ->
            when (effect) {
                is ContentSourcingScreenModel.Effect.ShowSnackbar -> {
                    snackbarMessage = effect.message
                }
            }
        }
    }

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = "Content Sourcing Hub",
                navigateUp = { navController.popBackStack() },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        if (state.isLoading) {
            LoadingScreen()
        } else {
            ContentSourcingLayout(
                contentPadding = contentPadding,
                state = state,
                onEvent = screenModel::onEvent,
            )
        }

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

        state.dialog?.let { dialog ->
            when (dialog) {
                is ContentSourcingScreenModel.Dialog.ScanComplete -> {
                    AlertDialog(
                        onDismissRequest = { screenModel.onEvent(ContentSourcingScreenModel.Event.DismissDialog) },
                        title = { Text("Scan Results: ${dialog.repoName}") },
                        text = {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "The auto-scanner discovered ${state.scanResults.size} series in this repository conforming to Jellyfin Bookshelf standards:",
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                LazyColumn(modifier = Modifier.height(250.dp)) {
                                    items(state.scanResults) { item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                    shape = RoundedCornerShape(4.dp),
                                                )
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Storage,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(item.title, style = MaterialTheme.typography.bodyMedium)
                                                Text(
                                                    "Type: ${item.contentType} | Units: ${item.metadata[ephyra.domain.content.model.ContentItem.META_CHAPTER_COUNT] ?: "0"}",
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
                            TextButton(onClick = {
                                screenModel.onEvent(ContentSourcingScreenModel.Event.DismissDialog)
                            }) {
                                Text("Close")
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ContentSourcingLayout(
    contentPadding: PaddingValues,
    state: ContentSourcingScreenModel.State,
    onEvent: (ContentSourcingScreenModel.Event) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        PrimaryTabRow(
            selectedTabIndex = state.selectedTab,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Tab(
                selected = state.selectedTab == 0,
                onClick = { onEvent(ContentSourcingScreenModel.Event.SelectTab(0)) },
                text = { Text("JS Scrapers") },
                icon = { Icon(Icons.Outlined.CloudSync, contentDescription = null) },
            )
            Tab(
                selected = state.selectedTab == 1,
                onClick = { onEvent(ContentSourcingScreenModel.Event.SelectTab(1)) },
                text = { Text("Local & SMB") },
                icon = { Icon(Icons.Outlined.Dns, contentDescription = null) },
            )
            Tab(
                selected = state.selectedTab == 2,
                onClick = { onEvent(ContentSourcingScreenModel.Event.SelectTab(2)) },
                text = { Text("Heuristics") },
                icon = { Icon(Icons.Outlined.Autorenew, contentDescription = null) },
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when (state.selectedTab) {
                0 -> ScrapersTabContent(state = state, onEvent = onEvent)
                1 -> RepositoriesTabContent(state = state, onEvent = onEvent)
                2 -> HeuristicsTabContent(state = state, onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun ScrapersTabContent(
    state: ContentSourcingScreenModel.State,
    onEvent: (ContentSourcingScreenModel.Event) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = "Add Play-Store Compliant Scraper",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Point Ephyra to any GitHub repository or paste a sandboxed QuickJS script " +
                            "to retrieve remote catalogs without external APK dependencies.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = state.githubUrl,
                        onValueChange = { onEvent(ContentSourcingScreenModel.Event.UpdateGithubUrl(it)) },
                        label = { Text("GitHub Scraper URL") },
                        placeholder = { Text("https://github.com/user/repo/scraper.js") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.scraperName,
                        onValueChange = { onEvent(ContentSourcingScreenModel.Event.UpdateScraperName(it)) },
                        label = { Text("Scraper Filename") },
                        placeholder = { Text("mangadex_scraper.js") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                if (state.githubUrl.isNotBlank() && state.scraperName.isNotBlank()) {
                                    onEvent(
                                        ContentSourcingScreenModel.Event.DownloadScraper(
                                            state.githubUrl,
                                            state.scraperName,
                                        ),
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Icon(Icons.Outlined.CloudDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download")
                        }

                        OutlinedButton(
                            onClick = { onEvent(ContentSourcingScreenModel.Event.ShowImportDialog(true)) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Outlined.UploadFile, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Paste Code")
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = "Link Website Domain to Scraper",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Route site catalog listings, search, and detail requests directly to " +
                            "your custom sandboxed QuickJS script.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = state.mapBaseUrl,
                        onValueChange = { onEvent(ContentSourcingScreenModel.Event.UpdateMapBaseUrl(it)) },
                        label = { Text("Website Base URL") },
                        placeholder = { Text("https://mangadex.org") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.mapScraperName,
                        onValueChange = { onEvent(ContentSourcingScreenModel.Event.UpdateMapScraperName(it)) },
                        label = { Text("Mapped Scraper Filename") },
                        placeholder = { Text("mangadex_scraper.js") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (state.mapBaseUrl.isNotBlank() && state.mapScraperName.isNotBlank()) {
                                onEvent(
                                    ContentSourcingScreenModel.Event.LinkBaseUrlToScraper(
                                        state.mapBaseUrl,
                                        state.mapScraperName,
                                    ),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                        ),
                    ) {
                        Text("Link Scraper Script")
                    }
                }
            }
        }

        if (state.scraperMappings.isNotEmpty()) {
            item {
                Text(
                    text = "Active Script Mappings",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            items(state.scraperMappings) { mapping ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mapping.baseUrl, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Mapped to: ${mapping.scraperName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = {
                            onEvent(ContentSourcingScreenModel.Event.RemoveScraperMapping(mapping.baseUrl))
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete mapping",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Sandboxed Scrapers",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        if (state.scrapers.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No scrapers imported yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(state.scrapers) { item ->
                ScraperRow(item = item, onEvent = onEvent)
            }
        }
    }

    if (state.showImportDialog) {
        AlertDialog(
            onDismissRequest = { onEvent(ContentSourcingScreenModel.Event.ShowImportDialog(false)) },
            title = { Text("Import Sandboxed JavaScript") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = state.importScriptName,
                        onValueChange = { onEvent(ContentSourcingScreenModel.Event.UpdateImportScriptName(it)) },
                        label = { Text("Script Name (e.g. custom.js)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.importScriptContent,
                        onValueChange = { onEvent(ContentSourcingScreenModel.Event.UpdateImportScriptContent(it)) },
                        label = { Text("JavaScript Content") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        shape = RoundedCornerShape(8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (state.importScriptName.isNotBlank() && state.importScriptContent.isNotBlank()) {
                        onEvent(
                            ContentSourcingScreenModel.Event.ImportLocalScraper(
                                state.importScriptName,
                                state.importScriptContent,
                            ),
                        )
                    }
                }) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(ContentSourcingScreenModel.Event.ShowImportDialog(false)) }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ScraperRow(
    item: ScraperItem,
    onEvent: (ContentSourcingScreenModel.Event) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "Sandboxed QuickJS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (item.hasUpdatesUrl) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                "Auto-Updates",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
            }

            if (item.hasUpdatesUrl) {
                IconButton(onClick = { onEvent(ContentSourcingScreenModel.Event.CheckScraperUpdate(item.name)) }) {
                    Icon(
                        imageVector = Icons.Outlined.CloudDownload,
                        contentDescription = "Check for updates",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun RepositoriesTabContent(
    state: ContentSourcingScreenModel.State,
    onEvent: (ContentSourcingScreenModel.Event) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = "Configure Media Repository",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Register custom folders or local network shares (SMB/NFS) holding content items " +
                            "configured in Jellyfin directories hierarchies.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (!state.showNetworkForm) {
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                    } else {
                                        Color.Transparent
                                    },
                                )
                                .clickable { onEvent(ContentSourcingScreenModel.Event.UpdateShowNetworkForm(false)) }
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Local SAF")
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (state.showNetworkForm) {
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                    } else {
                                        Color.Transparent
                                    },
                                )
                                .clickable { onEvent(ContentSourcingScreenModel.Event.UpdateShowNetworkForm(true)) }
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(Icons.Outlined.NetworkLocked, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SMB Network")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = state.repoName,
                        onValueChange = { onEvent(ContentSourcingScreenModel.Event.UpdateRepoName(it)) },
                        label = { Text("Repository Friendly Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (state.showNetworkForm) {
                        OutlinedTextField(
                            value = state.repoPath,
                            onValueChange = { onEvent(ContentSourcingScreenModel.Event.UpdateRepoPath(it)) },
                            label = { Text("SMB Connection String") },
                            placeholder = { Text("smb://user:password@192.168.1.50/share") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        )
                    } else {
                        OutlinedTextField(
                            value = state.repoPath,
                            onValueChange = { onEvent(ContentSourcingScreenModel.Event.UpdateRepoPath(it)) },
                            label = { Text("Storage Directory URI") },
                            placeholder = { Text("content://com.android.externalstorage...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (state.repoName.isNotBlank() && state.repoPath.isNotBlank()) {
                                onEvent(ContentSourcingScreenModel.Event.AddRepository(state.repoName, state.repoPath))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                        ),
                    ) {
                        Text("Add Repository")
                    }
                }
            }
        }

        item {
            Text(
                text = "Registered Repositories",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        if (state.repositories.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No repositories registered.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(state.repositories) { item ->
                RepositoryRow(item = item, onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun RepositoryRow(
    item: RepositoryItem,
    onEvent: (ContentSourcingScreenModel.Event) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    item.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .background(
                            if (item.isNetwork) {
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        if (item.isNetwork) "SMB Network Share" else "Local Repository",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.isNetwork) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            IconButton(onClick = { onEvent(ContentSourcingScreenModel.Event.ScanRepository(item)) }) {
                Icon(
                    imageVector = Icons.Outlined.PlayCircleOutline,
                    contentDescription = "Scan directory",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            IconButton(onClick = { onEvent(ContentSourcingScreenModel.Event.RemoveRepository(item.name)) }) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete repository",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun HeuristicsTabContent(
    state: ContentSourcingScreenModel.State,
    onEvent: (ContentSourcingScreenModel.Event) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    Column {
                        Text(
                            text = "Adaptive Layout Heuristics",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "The on-device layout heuristics engine automatically extracts structures and " +
                                "search pathways. If a remote website changes its elements, discovery re-triggers " +
                                "in the background dynamically without rewriting application code.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Learned Web Profiles",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        if (state.learnedProfiles.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No websites profiled yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(state.learnedProfiles) { profile ->
                ProfileRow(profile = profile, onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun ProfileRow(
    profile: SourceProfile,
    onEvent: (ContentSourcingScreenModel.Event) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        profile.baseUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(onClick = { onEvent(ContentSourcingScreenModel.Event.ForceRediscover(profile.baseUrl)) }) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Force discovery",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                IconButton(onClick = { onEvent(ContentSourcingScreenModel.Event.DeleteProfile(profile.baseUrl)) }) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete profile",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            profile.selectors?.let { selectors ->
                Text("Learned DOM Selectors:", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(6.dp),
                        )
                        .padding(8.dp),
                ) {
                    for ((field, value) in selectors) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "${field.name}: ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            Text(
                                value,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
