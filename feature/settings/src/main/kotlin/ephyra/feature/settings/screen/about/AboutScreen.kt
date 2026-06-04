package ephyra.feature.settings.screen.about

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import ephyra.core.common.util.system.logcat
import ephyra.domain.release.interactor.GetApplicationRelease
import ephyra.feature.settings.widget.TextPreferenceWidget
import ephyra.presentation.core.components.AppBar
import ephyra.presentation.core.components.LinkIcon
import ephyra.presentation.core.components.LogoHeader
import ephyra.presentation.core.components.ScrollbarLazyColumn
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.icons.CustomIcons
import ephyra.presentation.core.icons.Discord
import ephyra.presentation.core.icons.Github
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import ephyra.presentation.core.util.CrashLogUtil
import ephyra.presentation.core.util.system.copyToClipboard
import ephyra.presentation.core.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import logcat.LogPriority

@Composable
fun AboutScreen(
    navController: NavController = LocalNavController.current,
) {
    val screenModel = hiltViewModel<AboutScreenModel>()
    val state by screenModel.state.collectAsState()

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) {
        screenModel.events.collectLatest { event ->
            when (event) {
                is AboutEvent.NewUpdate -> {
                    // TODO: handle new update navigation
                }

                is AboutEvent.UpdateError -> {
                    context.toast(event.error.message)
                    logcat(LogPriority.ERROR, event.error)
                }
            }
        }
    }

    val updateResult = state.updateResult

    LaunchedEffect(updateResult) {
        if (updateResult != null) {
            when (updateResult) {
                is GetApplicationRelease.Result.NewUpdate -> {
                    // Handled via Dialog
                }
                GetApplicationRelease.Result.NoNewUpdate -> {
                    context.toast(ephyra.app.core.common.R.string.update_check_no_new_updates)
                    screenModel.clearUpdateResult()
                }
                else -> {
                    screenModel.clearUpdateResult()
                }
            }
        }
    }

    if (updateResult is GetApplicationRelease.Result.NewUpdate) {
        val release = updateResult.release
        AlertDialog(
            onDismissRequest = { screenModel.clearUpdateResult() },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            val intent = Intent().apply {
                                setClassName(context.packageName, "ephyra.app.data.notification.NotificationReceiver")
                                action = "${context.packageName}.NotificationReceiver.ACTION_START_APP_UPDATE"
                                putExtra("DOWNLOAD_URL", release.downloadLink)
                                putExtra("DOWNLOAD_TITLE", release.version)
                            }
                            context.sendBroadcast(intent)
                        } catch (e: Exception) {
                            context.toast(e.message)
                        }
                        screenModel.clearUpdateResult()
                    },
                ) {
                    Text(stringResource(ephyra.app.core.common.R.string.update_check_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { screenModel.clearUpdateResult() }) {
                    Text(stringResource(ephyra.app.core.common.R.string.action_cancel))
                }
            },
            title = {
                Text(stringResource(ephyra.app.core.common.R.string.update_check_notification_update_available))
            },
            text = {
                Text(release.version)
            },
        )
    }

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(ephyra.app.core.common.R.string.pref_category_about),
                navigateUp = { navController.popBackStack() },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        ScrollbarLazyColumn(
            contentPadding = contentPadding,
        ) {
            item {
                LogoHeader()
            }

            item {
                TextPreferenceWidget(
                    title = stringResource(ephyra.app.core.common.R.string.version),
                    subtitle = screenModel.getVersionName(withBuildDate = true),
                    onPreferenceClick = {
                        val deviceInfo = CrashLogUtil(context, screenModel.extensionManager).getDebugInfo()
                        context.copyToClipboard("Debug information", deviceInfo)
                    },
                )
            }

            if (screenModel.appInfo.updaterEnabled) {
                item {
                    TextPreferenceWidget(
                        title = stringResource(ephyra.app.core.common.R.string.check_for_updates),
                        widget = {
                            AnimatedVisibility(visible = state.isCheckingUpdates) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 3.dp,
                                )
                            }
                        },
                        onPreferenceClick = { screenModel.checkVersion() },
                    )
                }
            }

            if (!screenModel.appInfo.isDebug) {
                item {
                    TextPreferenceWidget(
                        title = stringResource(ephyra.app.core.common.R.string.whats_new),
                        onPreferenceClick = { uriHandler.openUri(screenModel.appInfo.releaseUrl) },
                    )
                }
            }

            item {
                TextPreferenceWidget(
                    title = stringResource(ephyra.app.core.common.R.string.licenses),
                    onPreferenceClick = { navController.navigate(ScreenRoutes.OpenSourceLicenses.route) },
                )
            }

            item {
                TextPreferenceWidget(
                    title = stringResource(ephyra.app.core.common.R.string.privacy_policy),
                    onPreferenceClick = {
                        uriHandler.openUri("https://github.com/Gameaday/Ephyra/blob/main/PRIVACY.md")
                    },
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    LinkIcon(
                        label = stringResource(ephyra.app.core.common.R.string.website),
                        icon = Icons.Outlined.Public,
                        url = "https://github.com/Gameaday/Ephyra",
                    )
                    LinkIcon(
                        label = "GitHub",
                        icon = CustomIcons.Github,
                        url = "https://github.com/Gameaday/Ephyra",
                    )
                }
            }
        }
    }
}
