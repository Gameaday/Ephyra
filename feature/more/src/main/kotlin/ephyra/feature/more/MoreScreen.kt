package ephyra.feature.more

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.core.common.Constants
import ephyra.feature.settings.widget.SwitchPreferenceWidget
import ephyra.feature.settings.widget.TextPreferenceWidget
import ephyra.presentation.core.R
import ephyra.presentation.core.components.ScrollbarLazyColumn
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.components.material.padding
import ephyra.presentation.core.i18n.pluralStringResource
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.ui.AppReadySignal
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import ephyra.core.common.util.lang.launchIO
import ephyra.domain.base.BasePreferences
import ephyra.domain.download.service.DownloadManager
import ephyra.presentation.core.util.asState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine

@Composable
fun MoreTabScreen(
    navController: NavController = LocalNavController.current,
) {
    val context = LocalContext.current
    val screenModel = hiltViewModel<MoreScreenModel>()
    val downloadQueueState by screenModel.downloadQueueState.collectAsStateWithLifecycle()
    MoreScreen(
        downloadQueueStateProvider = { downloadQueueState },
        downloadedOnly = screenModel.downloadedOnly,
        onDownloadedOnlyChange = { screenModel.downloadedOnly = it },
        incognitoMode = screenModel.incognitoMode,
        onIncognitoModeChange = { screenModel.incognitoMode = it },
        onClickDownloadQueue = { navController.navigate(ScreenRoutes.DownloadQueue.route) },
        onClickCategories = { navController.navigate(ScreenRoutes.Category.route) },
        onClickStats = { navController.navigate(ScreenRoutes.Stats.route) },
        onClickDataAndStorage = { navController.navigate(ScreenRoutes.Settings.route) }, // TODO: sub-route
        onClickSettings = { navController.navigate(ScreenRoutes.Settings.route) },
        onClickAbout = { navController.navigate(ScreenRoutes.Settings.route) }, // TODO: sub-route
    )

    LaunchedEffect(Unit) {
        (context as? AppReadySignal)?.signalReady()
    }
}

@HiltViewModel
class MoreScreenModel @Inject constructor(
    private val downloadManager: DownloadManager,
    private val preferences: BasePreferences,
) : ViewModel() {

    var downloadedOnly by preferences.downloadedOnly().asState(viewModelScope)
    var incognitoMode by preferences.incognitoMode().asState(viewModelScope)

    private var _downloadQueueState: MutableStateFlow<DownloadQueueState> = MutableStateFlow(DownloadQueueState.Stopped)
    val downloadQueueState: StateFlow<DownloadQueueState> = _downloadQueueState.asStateFlow()

    init {
        // Handle running/paused status change and queue progress updating
        viewModelScope.launchIO {
            combine(
                downloadManager.isDownloaderRunning,
                downloadManager.queueState,
            ) { isRunning, downloadQueue -> Pair(isRunning, downloadQueue.size) }
                .collectLatest { (isDownloading, downloadQueueSize) ->
                    val pendingDownloadExists = downloadQueueSize != 0
                    _downloadQueueState.value = when {
                        !pendingDownloadExists -> DownloadQueueState.Stopped
                        !isDownloading -> DownloadQueueState.Paused(downloadQueueSize)
                        else -> DownloadQueueState.Downloading(downloadQueueSize)
                    }
                }
        }
    }
}

sealed interface DownloadQueueState {
    data object Stopped : DownloadQueueState
    data class Paused(val pending: Int) : DownloadQueueState
    data class Downloading(val pending: Int) : DownloadQueueState
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
                    checked = downloadedOnly,
                    onCheckedChanged = onDownloadedOnlyChange,
                )
            }
            item {
                SwitchPreferenceWidget(
                    title = stringResource(ephyra.app.core.common.R.string.pref_incognito_mode),
                    subtitle = stringResource(ephyra.app.core.common.R.string.pref_incognito_mode_summary),
                    icon = ImageVector.vectorResource(R.drawable.ic_glasses_24dp),
                    checked = incognitoMode,
                    onCheckedChanged = onIncognitoModeChange,
                )
            }

            item { HorizontalDivider() }

            item {
                val downloadQueueState = downloadQueueStateProvider()
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
                            pluralStringResource(ephyra.app.core.common.R.plurals.download_queue_summary, count = pending, pending)
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
