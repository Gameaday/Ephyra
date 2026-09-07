package ephyra.feature.more

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.Constants
import ephyra.domain.base.BasePreferences
import ephyra.domain.download.service.DownloadManager
import ephyra.feature.settings.widget.SwitchPreferenceWidget
import ephyra.feature.settings.widget.TextPreferenceWidget
import ephyra.presentation.core.R
import ephyra.presentation.core.components.ScrollbarLazyColumn
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.components.material.padding
import ephyra.presentation.core.i18n.pluralStringResource
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.udf.BaseUdfViewModel
import ephyra.presentation.core.ui.AppReadySignal
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
fun MoreTabScreen(
    navController: NavController = LocalNavController.current,
) {
    val context = LocalContext.current
    val viewModel = hiltViewModel<MoreViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    MoreScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onClickDownloadQueue = { navController.navigate(ScreenRoutes.DownloadQueue.route) },
        onClickCategories = { navController.navigate(ephyra.presentation.core.ui.navigation.Screen.Category) },
        onClickStats = { navController.navigate(ScreenRoutes.Stats.route) },
        onClickDataAndStorage = { navController.navigate(ScreenRoutes.SettingsData.route) },
        onClickSettings = { navController.navigate(ScreenRoutes.Settings.route) },
        onClickAbout = { navController.navigate(ScreenRoutes.About.route) },
    )

    LaunchedEffect(Unit) {
        (context as? AppReadySignal)?.signalReady()
    }
}

@Immutable
data class MoreState(
    val downloadedOnly: Boolean = false,
    val incognitoMode: Boolean = false,
    val downloadQueueState: DownloadQueueState = DownloadQueueState.Stopped,
)

sealed interface MoreEvent {
    data class SetDownloadedOnly(val enabled: Boolean) : MoreEvent
    data class SetIncognitoMode(val enabled: Boolean) : MoreEvent
}

sealed interface DownloadQueueState {
    data object Stopped : DownloadQueueState
    data class Paused(val pending: Int) : DownloadQueueState
    data class Downloading(val pending: Int) : DownloadQueueState
}

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val downloadManager: DownloadManager,
    private val preferences: BasePreferences,
) : BaseUdfViewModel<MoreState, MoreEvent, Nothing>(
    MoreState(
        downloadedOnly = preferences.downloadedOnly().getSync(),
        incognitoMode = preferences.incognitoMode().getSync(),
    ),
) {

    init {
        viewModelScope.launch {
            preferences.downloadedOnly().changes()
                .collectLatest { enabled ->
                    updateState { it.copy(downloadedOnly = enabled) }
                }
        }
        viewModelScope.launch {
            preferences.incognitoMode().changes()
                .collectLatest { enabled ->
                    updateState { it.copy(incognitoMode = enabled) }
                }
        }
        // Handle running/paused status change and queue progress updating
        viewModelScope.launch {
            combine(
                downloadManager.isDownloaderRunning,
                downloadManager.queueState,
            ) { isRunning, downloadQueue -> Pair(isRunning, downloadQueue.size) }
                .collectLatest { (isDownloading, downloadQueueSize) ->
                    val pendingDownloadExists = downloadQueueSize != 0
                    val queueState = when {
                        !pendingDownloadExists -> DownloadQueueState.Stopped
                        !isDownloading -> DownloadQueueState.Paused(downloadQueueSize)
                        else -> DownloadQueueState.Downloading(downloadQueueSize)
                    }
                    updateState { it.copy(downloadQueueState = queueState) }
                }
        }
    }

    override fun onEvent(event: MoreEvent) {
        when (event) {
            is MoreEvent.SetDownloadedOnly -> {
                preferences.downloadedOnly().set(event.enabled)
            }
            is MoreEvent.SetIncognitoMode -> {
                preferences.incognitoMode().set(event.enabled)
            }
        }
    }
}

@Composable
fun MoreScreen(
    downloadQueueStateProvider: () -> DownloadQueueState,
    downloadedOnly: Boolean,
    onDownloadedOnlyChange: (Boolean) -> Unit,
    incognitoMode: Boolean,
    onIncognitoModeChange: (Boolean) -> Unit,
    onClickDownloadQueue: () -> Unit,
    onClickCategories: () -> Unit,
    onClickStats: () -> Unit,
    onClickDataAndStorage: () -> Unit,
    onClickSettings: () -> Unit,
    onClickAbout: () -> Unit,
) {
    MoreScreen(
        state = MoreState(
            downloadedOnly = downloadedOnly,
            incognitoMode = incognitoMode,
            downloadQueueState = downloadQueueStateProvider(),
        ),
        onEvent = { event ->
            when (event) {
                is MoreEvent.SetDownloadedOnly -> onDownloadedOnlyChange(event.enabled)
                is MoreEvent.SetIncognitoMode -> onIncognitoModeChange(event.enabled)
            }
        },
        onClickDownloadQueue = onClickDownloadQueue,
        onClickCategories = onClickCategories,
        onClickStats = onClickStats,
        onClickDataAndStorage = onClickDataAndStorage,
        onClickSettings = onClickSettings,
        onClickAbout = onClickAbout,
    )
}

@Composable
fun MoreScreen(
    state: MoreState,
    onEvent: (MoreEvent) -> Unit,
    onClickDownloadQueue: () -> Unit,
    onClickCategories: () -> Unit,
    onClickStats: () -> Unit,
    onClickDataAndStorage: () -> Unit,
    onClickSettings: () -> Unit,
    onClickAbout: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    Scaffold { contentPadding ->
        ScrollbarLazyColumn(
            modifier = Modifier.padding(contentPadding),
        ) {
            item {
                LogoHeader()
            }
            item {
                SwitchPreferenceWidget(
                    title = stringResource(ephyra.app.core.common.R.string.label_downloaded_only),
                    subtitle = stringResource(ephyra.app.core.common.R.string.downloaded_only_summary),
                    icon = Icons.Outlined.CloudOff,
                    checked = state.downloadedOnly,
                    onCheckedChanged = { onEvent(MoreEvent.SetDownloadedOnly(it)) },
                )
            }
            item {
                SwitchPreferenceWidget(
                    title = stringResource(ephyra.app.core.common.R.string.pref_incognito_mode),
                    subtitle = stringResource(ephyra.app.core.common.R.string.pref_incognito_mode_summary),
                    icon = ImageVector.vectorResource(R.drawable.ic_glasses_24dp),
                    checked = state.incognitoMode,
                    onCheckedChanged = { onEvent(MoreEvent.SetIncognitoMode(it)) },
                )
            }

            item { HorizontalDivider() }

            item {
                val downloadQueueState = state.downloadQueueState
                TextPreferenceWidget(
                    title = stringResource(ephyra.app.core.common.R.string.label_download_queue),
                    subtitle = when (downloadQueueState) {
                        DownloadQueueState.Stopped -> null
                        is DownloadQueueState.Paused -> {
                            val pending = downloadQueueState.pending
                            if (pending == 0) {
                                stringResource(ephyra.app.core.common.R.string.paused)
                            } else {
                                "${stringResource(ephyra.app.core.common.R.string.paused)} • ${
                                    pluralStringResource(
                                        ephyra.app.core.common.R.plurals.download_queue_summary,
                                        count = pending,
                                        pending,
                                    )
                                }"
                            }
                        }
                        is DownloadQueueState.Downloading -> {
                            val pending = downloadQueueState.pending
                            pluralStringResource(
                                ephyra.app.core.common.R.plurals.download_queue_summary,
                                count = pending,
                                pending,
                            )
                        }
                    },
                    icon = Icons.Outlined.GetApp,
                    onPreferenceClick = onClickDownloadQueue,
                )
            }
            item {
                TextPreferenceWidget(
                    title = stringResource(ephyra.app.core.common.R.string.categories),
                    icon = Icons.AutoMirrored.Outlined.Label,
                    onPreferenceClick = onClickCategories,
                )
            }
            item {
                TextPreferenceWidget(
                    title = stringResource(ephyra.app.core.common.R.string.label_stats),
                    icon = Icons.Outlined.QueryStats,
                    onPreferenceClick = onClickStats,
                )
            }
            item {
                TextPreferenceWidget(
                    title = stringResource(ephyra.app.core.common.R.string.label_data_storage),
                    icon = Icons.Outlined.Storage,
                    onPreferenceClick = onClickDataAndStorage,
                )
            }

            item { HorizontalDivider() }

            item {
                TextPreferenceWidget(
                    title = stringResource(ephyra.app.core.common.R.string.label_settings),
                    icon = Icons.Outlined.Settings,
                    onPreferenceClick = onClickSettings,
                )
            }
            item {
                TextPreferenceWidget(
                    title = stringResource(ephyra.app.core.common.R.string.pref_category_about),
                    icon = Icons.Outlined.Info,
                    onPreferenceClick = onClickAbout,
                )
            }
            item {
                TextPreferenceWidget(
                    title = stringResource(ephyra.app.core.common.R.string.label_help),
                    icon = Icons.AutoMirrored.Outlined.HelpOutline,
                    onPreferenceClick = { uriHandler.openUri(Constants.URL_HELP) },
                )
            }
        }
    }
}
