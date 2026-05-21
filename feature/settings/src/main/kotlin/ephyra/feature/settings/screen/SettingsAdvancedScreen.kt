package ephyra.feature.settings.screen

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import ephyra.core.common.util.lang.launchNonCancellable
import ephyra.core.common.util.system.GLUtil
import ephyra.core.common.util.system.logcat
import ephyra.core.common.util.system.setDefaultSettings
import ephyra.core.download.DownloadCache
import ephyra.domain.base.BasePreferences
import ephyra.domain.extension.interactor.TrustExtension
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.library.service.MetadataUpdateScheduler
import ephyra.domain.manga.interactor.ResetViewerFlags
import ephyra.feature.settings.Preference
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.ui.AppInfo
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import ephyra.presentation.core.util.CrashLogUtil
import ephyra.presentation.core.util.collectAsState
import ephyra.presentation.core.util.system.isShizukuInstalled
import ephyra.presentation.core.util.system.powerManager
import ephyra.presentation.core.util.system.toast
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.network.PREF_DOH_360
import eu.kanade.tachiyomi.network.PREF_DOH_ADGUARD
import eu.kanade.tachiyomi.network.PREF_DOH_ALIDNS
import eu.kanade.tachiyomi.network.PREF_DOH_CLOUDFLARE
import eu.kanade.tachiyomi.network.PREF_DOH_CONTROLD
import eu.kanade.tachiyomi.network.PREF_DOH_DNSPOD
import eu.kanade.tachiyomi.network.PREF_DOH_GOOGLE
import eu.kanade.tachiyomi.network.PREF_DOH_MULLVAD
import eu.kanade.tachiyomi.network.PREF_DOH_NJALLA
import eu.kanade.tachiyomi.network.PREF_DOH_QUAD101
import eu.kanade.tachiyomi.network.PREF_DOH_QUAD9
import eu.kanade.tachiyomi.network.PREF_DOH_SHECAN
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import okhttp3.Headers
import java.io.File

object SettingsAdvancedScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = ephyra.app.core.common.R.string.pref_category_advanced

    @Composable
    override fun getPreferences(): List<Preference> {
        val screenModel = hiltViewModel<SettingsAdvancedScreenModel>()
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val navController = LocalNavController.current
        val appInfo = screenModel.appInfo
        val extensionManager: ExtensionManager = screenModel.extensionManager

        val basePreferences = screenModel.basePreferences
        val networkPreferences = screenModel.networkPreferences
        val libraryPreferences = screenModel.libraryPreferences

        return listOf(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(ephyra.app.core.common.R.string.pref_dump_crash_logs),
                subtitle = stringResource(ephyra.app.core.common.R.string.pref_dump_crash_logs_summary),
                onClick = {
                    scope.launch {
                        CrashLogUtil(context, extensionManager).dumpLogs()
                    }
                },
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = networkPreferences.verboseLogging(),
                title = stringResource(ephyra.app.core.common.R.string.pref_verbose_logging),
                subtitle = stringResource(ephyra.app.core.common.R.string.pref_verbose_logging_summary),
                onValueChanged = {
                    context.toast(ephyra.app.core.common.R.string.requires_app_restart)
                    true
                },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(ephyra.app.core.common.R.string.pref_debug_info),
                onClick = { navController.navigate(ScreenRoutes.DebugInfo.route) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(ephyra.app.core.common.R.string.pref_onboarding_guide),
                onClick = { navController.navigate(ScreenRoutes.Onboarding.route) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(ephyra.app.core.common.R.string.pref_manage_notifications),
                onClick = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                },
            ),
            getBackgroundActivityGroup(),
            getDataGroup(downloadCache = screenModel.downloadCache, navController = navController),
            getNetworkGroup(
                networkPreferences = networkPreferences,
                networkHelper = screenModel.networkHelper,
            ),
            getLibraryGroup(
                libraryPreferences = libraryPreferences,
                resetViewerFlags = screenModel.resetViewerFlags,
                metadataUpdateScheduler = screenModel.metadataUpdateScheduler,
            ),
            getReaderGroup(basePreferences = basePreferences),
            getExtensionsGroup(
                appInfo = appInfo,
                basePreferences = basePreferences,
                trustExtension = screenModel.trustExtension,
            ),
        )
    }

    @Composable
    private fun getBackgroundActivityGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current

        return Preference.PreferenceGroup(
            title = stringResource(ephyra.app.core.common.R.string.label_background_activity),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(ephyra.app.core.common.R.string.pref_disable_battery_optimization),
                    subtitle = stringResource(
                        ephyra.app.core.common.R.string.pref_disable_battery_optimization_summary,
                    ),
                    onClick = {
                        val packageName: String = context.packageName
                        if (!context.powerManager.isIgnoringBatteryOptimizations(packageName)) {
                            try {
                                @SuppressLint("BatteryLife")
                                val intent = Intent().apply {
                                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                    data = "package:$packageName".toUri()
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: ActivityNotFoundException) {
                                context.toast(
                                    ephyra.app.core.common.R.string.battery_optimization_setting_activity_not_found,
                                )
                            }
                        } else {
                            context.toast(ephyra.app.core.common.R.string.battery_optimization_disabled)
                        }
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = "Don't kill my app!",
                    subtitle = stringResource(ephyra.app.core.common.R.string.about_dont_kill_my_app),
                    onClick = { uriHandler.openUri("https://dontkillmyapp.com/") },
                ),
            ),
        )
    }

    @Composable
    private fun getDataGroup(downloadCache: DownloadCache, navController: NavController): Preference.PreferenceGroup {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        return Preference.PreferenceGroup(
            title = stringResource(ephyra.app.core.common.R.string.label_data),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(ephyra.app.core.common.R.string.pref_invalidate_download_cache),
                    subtitle = stringResource(ephyra.app.core.common.R.string.pref_invalidate_download_cache_summary),
                    onClick = {
                        scope.launch {
                            downloadCache.invalidateCache()
                            context.toast(ephyra.app.core.common.R.string.download_cache_invalidated)
                        }
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(ephyra.app.core.common.R.string.pref_clear_database),
                    subtitle = stringResource(ephyra.app.core.common.R.string.pref_clear_database_summary),
                    onClick = { navController.navigate(ScreenRoutes.ClearDatabase.route) },
                ),
            ),
        )
    }

    @Composable
    private fun getNetworkGroup(
        networkPreferences: NetworkPreferences,
        networkHelper: NetworkHelper,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current

        val userAgentPref = networkPreferences.defaultUserAgent()
        val userAgent by userAgentPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(ephyra.app.core.common.R.string.label_network),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(ephyra.app.core.common.R.string.pref_clear_cookies),
                    onClick = {
                        networkHelper.cookieJar.removeAll()
                        context.toast(ephyra.app.core.common.R.string.cookies_cleared)
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(ephyra.app.core.common.R.string.pref_clear_webview_data),
                    onClick = {
                        try {
                            WebView(context).run {
                                setDefaultSettings()
                                clearCache(true)
                                clearFormData()
                                clearHistory()
                                clearSslPreferences()
                            }
                            WebStorage.getInstance().deleteAllData()
                            context.applicationInfo?.dataDir?.let { File("$it/app_webview/").deleteRecursively() }
                            context.toast(ephyra.app.core.common.R.string.webview_data_deleted)
                        } catch (e: Throwable) {
                            logcat(LogPriority.ERROR, e)
                            context.toast(ephyra.app.core.common.R.string.cache_delete_error)
                        }
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = networkPreferences.dohProvider(),
                    entries = persistentMapOf(
                        -1 to stringResource(ephyra.app.core.common.R.string.disabled),
                        PREF_DOH_CLOUDFLARE to "Cloudflare",
                        PREF_DOH_GOOGLE to "Google",
                        PREF_DOH_ADGUARD to "AdGuard",
                        PREF_DOH_QUAD9 to "Quad9",
                        PREF_DOH_ALIDNS to "AliDNS",
                        PREF_DOH_DNSPOD to "DNSPod",
                        PREF_DOH_360 to "360",
                        PREF_DOH_QUAD101 to "Quad 101",
                        PREF_DOH_MULLVAD to "Mullvad",
                        PREF_DOH_CONTROLD to "Control D",
                        PREF_DOH_NJALLA to "Njalla",
                        PREF_DOH_SHECAN to "Shecan",
                    ),
                    title = stringResource(ephyra.app.core.common.R.string.pref_dns_over_https),
                    onValueChanged = {
                        context.toast(ephyra.app.core.common.R.string.requires_app_restart)
                        true
                    },
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = userAgentPref,
                    title = stringResource(ephyra.app.core.common.R.string.pref_user_agent_string),
                    onValueChanged = {
                        try {
                            // OkHttp checks for valid values internally
                            Headers.Builder().add("User-Agent", it)
                            context.toast(ephyra.app.core.common.R.string.requires_app_restart)
                        } catch (_: IllegalArgumentException) {
                            context.toast(ephyra.app.core.common.R.string.error_user_agent_string_invalid)
                            return@EditTextPreference false
                        }
                        true
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(ephyra.app.core.common.R.string.pref_reset_user_agent_string),
                    enabled = remember(userAgent) { userAgent != userAgentPref.defaultValue() },
                    onClick = {
                        userAgentPref.delete()
                        context.toast(ephyra.app.core.common.R.string.requires_app_restart)
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getLibraryGroup(
        libraryPreferences: LibraryPreferences,
        resetViewerFlags: ResetViewerFlags,
        metadataUpdateScheduler: MetadataUpdateScheduler,
    ): Preference.PreferenceGroup {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        return Preference.PreferenceGroup(
            title = stringResource(ephyra.app.core.common.R.string.label_library),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(ephyra.app.core.common.R.string.pref_refresh_library_covers),
                    onClick = { metadataUpdateScheduler.startMetadataUpdateNow() },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(ephyra.app.core.common.R.string.pref_reset_viewer_flags),
                    subtitle = stringResource(ephyra.app.core.common.R.string.pref_reset_viewer_flags_summary),
                    onClick = {
                        scope.launchNonCancellable {
                            val success = resetViewerFlags.await()
                            withContext(Dispatchers.Main) {
                                val message = if (success) {
                                    ephyra.app.core.common.R.string.pref_reset_viewer_flags_success
                                } else {
                                    ephyra.app.core.common.R.string.pref_reset_viewer_flags_error
                                }
                                context.toast(message)
                            }
                        }
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.updateMangaTitles(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_update_library_manga_titles),
                    subtitle = stringResource(ephyra.app.core.common.R.string.pref_update_library_manga_titles_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.disallowNonAsciiFilenames(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_disallow_non_ascii_filenames),
                    subtitle = stringResource(
                        ephyra.app.core.common.R.string.pref_disallow_non_ascii_filenames_details,
                    ),
                ),
            ),
        )
    }

    @Composable
    private fun getReaderGroup(
        basePreferences: BasePreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val chooseColorProfile = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
                basePreferences.displayProfile().set(uri.toString())
            }
        }
        return Preference.PreferenceGroup(
            title = stringResource(ephyra.app.core.common.R.string.pref_category_reader),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = basePreferences.hardwareBitmapThreshold(),
                    entries = GLUtil.CUSTOM_TEXTURE_LIMIT_OPTIONS
                        .mapIndexed { index, option ->
                            val display = if (index == 0) {
                                stringResource(
                                    ephyra.app.core.common.R.string.pref_hardware_bitmap_threshold_default,
                                    option,
                                )
                            } else {
                                option.toString()
                            }
                            option to display
                        }
                        .toMap()
                        .toImmutableMap(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_hardware_bitmap_threshold),
                    subtitleProvider = { value, options ->
                        stringResource(
                            ephyra.app.core.common.R.string.pref_hardware_bitmap_threshold_summary,
                            options[value].orEmpty(),
                        )
                    },
                    enabled = GLUtil.DEVICE_TEXTURE_LIMIT > GLUtil.SAFE_TEXTURE_LIMIT,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = basePreferences.alwaysDecodeLongStripWithSSIV(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_always_decode_long_strip_with_ssiv_2),
                    subtitle = stringResource(
                        ephyra.app.core.common.R.string.pref_always_decode_long_strip_with_ssiv_summary,
                    ),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(ephyra.app.core.common.R.string.pref_display_profile),
                    subtitle = basePreferences.displayProfile().getSync(),
                    onClick = {
                        chooseColorProfile.launch(arrayOf("*/*"))
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getExtensionsGroup(
        appInfo: AppInfo,
        basePreferences: BasePreferences,
        trustExtension: TrustExtension,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current
        val extensionInstallerPref = basePreferences.extensionInstaller()
        var shizukuMissing by rememberSaveable { mutableStateOf(false) }

        if (shizukuMissing) {
            val dismiss = { shizukuMissing = false }
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(text = stringResource(ephyra.app.core.common.R.string.ext_installer_shizuku)) },
                text = {
                    Text(
                        text = stringResource(ephyra.app.core.common.R.string.ext_installer_shizuku_unavailable_dialog),
                    )
                },
                dismissButton = {
                    TextButton(onClick = dismiss) {
                        Text(text = stringResource(ephyra.app.core.common.R.string.action_cancel))
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            dismiss()
                            uriHandler.openUri("https://shizuku.rikka.app/download")
                        },
                    ) {
                        Text(text = stringResource(ephyra.app.core.common.R.string.action_ok))
                    }
                },
            )
        }
        return Preference.PreferenceGroup(
            title = stringResource(ephyra.app.core.common.R.string.label_extensions),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = extensionInstallerPref,
                    entries = extensionInstallerPref.entries
                        .filter {
                            // TODO: allow private option in stable versions once URL handling is more fleshed out
                            if (appInfo.isRelease) {
                                it != BasePreferences.ExtensionInstaller.PRIVATE
                            } else {
                                true
                            }
                        }
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    title = stringResource(ephyra.app.core.common.R.string.ext_installer_pref),
                    onValueChanged = {
                        if (it == BasePreferences.ExtensionInstaller.SHIZUKU &&
                            !context.isShizukuInstalled
                        ) {
                            shizukuMissing = true
                            false
                        } else {
                            true
                        }
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(ephyra.app.core.common.R.string.ext_revoke_trust),
                    onClick = {
                        trustExtension.revokeAll()
                        context.toast(ephyra.app.core.common.R.string.requires_app_restart)
                    },
                ),
            ),
        )
    }
}
