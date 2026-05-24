package ephyra.app

import android.annotation.SuppressLint
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Looper
import android.webkit.WebView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowRgb565
import coil3.request.bitmapConfig
import coil3.request.crossfade
import coil3.util.DebugLogger
import dagger.hilt.android.HiltAndroidApp
import ephyra.app.crash.CrashActivity
import ephyra.app.crash.GlobalExceptionHandler
import ephyra.app.di.initializeCoreContainer
import ephyra.app.startup.StartupGuard
import ephyra.app.util.system.animatorDurationScale
import ephyra.app.util.system.cancelNotification
import ephyra.app.util.system.notify
import ephyra.core.common.core.security.PrivacyPreferences
import ephyra.core.common.core.security.SecurityPreferences
import ephyra.core.common.di.CoreContainer
import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.util.lang.launchIO
import ephyra.core.common.util.system.DeviceUtil
import ephyra.core.common.util.system.GLUtil
import ephyra.core.common.util.system.ImageUtil
import ephyra.core.common.util.system.WebViewUtil
import ephyra.core.common.util.system.logcat
import ephyra.core.migration.Migrator
import ephyra.core.migration.migrations.migrations
import ephyra.data.cache.CoverCache
import ephyra.data.coil.BufferedSourceFetcher
import ephyra.data.coil.MangaCoverFetcher
import ephyra.data.coil.MangaCoverKeyer
import ephyra.data.coil.MangaKeyer
import ephyra.data.notification.Notifications
import ephyra.domain.base.BasePreferences
import ephyra.domain.source.service.SourceManager
import ephyra.domain.ui.UiPreferences
import ephyra.domain.updates.interactor.GetUpdates
import ephyra.presentation.core.data.coil.TachiyomiImageDecoder
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.ui.delegate.SecureActivityDelegateState
import ephyra.presentation.core.util.system.setAppCompatDelegateThemeMode
import ephyra.presentation.widget.WidgetManager
import ephyra.telemetry.TelemetryConfig
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import logcat.LogcatLogger
import javax.inject.Inject

@HiltAndroidApp
class App :
    Application(),
    DefaultLifecycleObserver,
    SingletonImageLoader.Factory,
    androidx.work.Configuration.Provider {

    @Inject
    lateinit var basePreferences: BasePreferences

    @Inject
    lateinit var privacyPreferences: PrivacyPreferences

    @Inject
    lateinit var networkPreferences: NetworkPreferences

    @Inject
    lateinit var securityPreferences: SecurityPreferences

    @Inject
    lateinit var preferenceStore: PreferenceStore

    @Inject
    lateinit var networkHelper: NetworkHelper

    @Inject
    lateinit var getUpdates: GetUpdates

    @Inject
    lateinit var uiPreferences: UiPreferences

    @Inject
    lateinit var coverCache: CoverCache

    @Inject
    lateinit var sourceManager: SourceManager

    @Volatile
    private var verboseLoggingEnabled = false

    override val workManagerConfiguration: androidx.work.Configuration
        get() {
            ephyra.app.startup.StartupTracker.complete(ephyra.app.startup.StartupTracker.Phase.WORKMANAGER_CONFIGURED)
            return androidx.work.Configuration.Builder()
                .setWorkerFactory(ephyra.app.data.work.AppWorkerFactory())
                .build()
        }

    private val disableIncognitoReceiver = DisableIncognitoReceiver()

    @SuppressLint("LaunchActivityFromNotification")
    override fun onCreate() {
        super<Application>.onCreate()

        // Phase 1: Logging — must succeed first
        try {
            if (!LogcatLogger.isInstalled) {
                LogcatLogger.install()
                val minLogPriority = if (BuildConfig.DEBUG) LogPriority.DEBUG else LogPriority.INFO
                LogcatLogger.loggers += AndroidLogcatLogger(minLogPriority)
            }
        } catch (e: Exception) {
            // Last resort — logging is unavailable
            android.util.Log.e("Ephyra", "Failed to initialize logcat", e)
        }
        StartupGuard.completePhase("logging")

        // Phase 2: Global crash handler
        try {
            GlobalExceptionHandler.initialize(applicationContext, CrashActivity::class.java)
        } catch (e: Exception) {
            android.util.Log.e("Ephyra", "Failed to initialize crash handler", e)
        }
        StartupGuard.completePhase("crash_handler")

        // Phase 3: DI container initialization — wrapped in try/catch
        try {
            initializeCoreContainer(this)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "DI container initialization failed — app will degrade" }
        }
        StartupGuard.completePhase("di_container")
        ephyra.app.startup.StartupTracker.complete(ephyra.app.startup.StartupTracker.Phase.APP_CREATED)

        // Phase 4: Telemetry (non-critical)
        try {
            TelemetryConfig.init(applicationContext)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Telemetry init failed — non-fatal" }
        }
        StartupGuard.completePhase("telemetry")

        // Avoid potential crashes from multiple WebView processes
        try {
            val process = getProcessName()
            if (packageName != process) WebView.setDataDirectorySuffix(process)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "WebView data directory setup failed" }
        }

        // Phase 5: Notification channels (non-critical)
        setupNotificationChannels()
        StartupGuard.completePhase("notifications")

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        val scope = ProcessLifecycleOwner.get().lifecycleScope

        // Phase 6: Reactive bindings (preference flows)
        try {
            basePreferences.incognitoMode().changes()
                .onEach { enabled ->
                    if (enabled) {
                        disableIncognitoReceiver.register()
                        if (ContextCompat.checkSelfPermission(
                                this@App,
                                android.Manifest.permission.POST_NOTIFICATIONS,
                            ) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            notify(
                                Notifications.ID_INCOGNITO_MODE,
                                Notifications.CHANNEL_INCOGNITO_MODE,
                            ) {
                                setContentTitle(this@App.getString(ephyra.app.core.common.R.string.pref_incognito_mode))
                                setContentText(
                                    this@App.getString(ephyra.app.core.common.R.string.notification_incognito_text),
                                )
                                setSmallIcon(R.drawable.ic_glasses_24dp)
                                setOngoing(true)

                                val pendingIntent = PendingIntent.getBroadcast(
                                    this@App,
                                    0,
                                    Intent(ACTION_DISABLE_INCOGNITO_MODE).setPackage(BuildConfig.APPLICATION_ID),
                                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
                                )
                                setContentIntent(pendingIntent)
                            }
                        }
                    } else {
                        disableIncognitoReceiver.unregister()
                        cancelNotification(Notifications.ID_INCOGNITO_MODE)
                    }
                }
                .launchIn(scope)

            privacyPreferences.analytics()
                .changes()
                .onEach(TelemetryConfig::setAnalyticsEnabled)
                .launchIn(scope)

            privacyPreferences.crashlytics()
                .changes()
                .onEach(TelemetryConfig::setCrashlyticsEnabled)
                .launchIn(scope)

            basePreferences.hardwareBitmapThreshold().let { preference ->
                if (!preference.isSet()) preference.set(GLUtil.DEVICE_TEXTURE_LIMIT)
            }

            basePreferences.hardwareBitmapThreshold().changes()
                .onEach { ImageUtil.hardwareBitmapThreshold = it }
                .launchIn(scope)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Startup reactive binding setup failed" }
        }
        StartupGuard.completePhase("reactive_bindings")

        // Phase 7: Async initialization (theme, widget, migration)
        scope.launchIO {
            try {
                verboseLoggingEnabled = try {
                    networkPreferences.verboseLogging().get()
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Failed to read verbose logging pref" }
                    false
                }

                try {
                    setAppCompatDelegateThemeMode(uiPreferences.themeMode().get())
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Failed to set theme — using default" }
                }

                try {
                    WidgetManager(getUpdates, securityPreferences).apply { init(scope) }
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Widget manager init failed" }
                }

                try {
                    initializeMigrator()
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Migration failed — continuing with current data" }
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Async startup initialization failed" }
            } finally {
                StartupGuard.completePhase("async_init")
            }
        }
    }

    private suspend fun initializeMigrator() {
        val preference = preferenceStore.getInt(Preference.appStateKey("last_version_code"), 0)
        logcat { "Migration from ${preference.get()} to ${BuildConfig.VERSION_CODE}" }
        ephyra.app.startup.StartupTracker.complete(ephyra.app.startup.StartupTracker.Phase.MIGRATOR_STARTED)
        Migrator.initialize(
            old = preference.get(),
            new = BuildConfig.VERSION_CODE,
            migrations = migrations,
        ) {
            logcat { "Updating last version to ${BuildConfig.VERSION_CODE}" }
            preference.set(BuildConfig.VERSION_CODE)
        }
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(this).apply {
            val callFactoryLazy = lazy { networkHelper.client }
            components {
                // NetworkFetcher.Factory
                add(OkHttpNetworkFetcherFactory(callFactoryLazy::value))
                // Decoder.Factory
                add(TachiyomiImageDecoder.Factory())
                // Fetcher.Factory
                add(BufferedSourceFetcher.Factory())
                add(MangaCoverFetcher.MangaCoverFactory(callFactoryLazy, coverCache, sourceManager))
                add(MangaCoverFetcher.MangaFactory(callFactoryLazy, coverCache, sourceManager))
                // Keyer
                add(MangaCoverKeyer(coverCache))
                add(MangaKeyer(coverCache))
            }

            memoryCache(
                MemoryCache.Builder()
                    .maxSizePercent(context)
                    .build(),
            )

            crossfade((300 * this@App.animatorDurationScale).toInt())
            val lowRam = DeviceUtil.isLowRamDevice(this@App)
            allowRgb565(lowRam)
            // On capable devices, request GPU-resident hardware bitmaps as the global default.
            // This eliminates the CPU→GPU upload on every render frame for covers and browse
            // images. getBitmapOrNull() handles the soft-copy needed for compress/notifications.
            if (!lowRam) bitmapConfig(Bitmap.Config.HARDWARE)
            if (verboseLoggingEnabled) logger(DebugLogger())

            // Coil spawns a new thread for every image load by default
            fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(8))
            decoderCoroutineContext(Dispatchers.IO.limitedParallelism(3))
        }
            .build()
    }

    override fun onStart(owner: LifecycleOwner) {
        SecureActivityDelegateState.onApplicationStart(securityPreferences)
    }

    override fun onStop(owner: LifecycleOwner) {
        SecureActivityDelegateState.onApplicationStopped(securityPreferences)
    }

    /**
     * Called by the system when it determines that memory is running low. Applies progressive
     * trimming of the Coil image memory cache so the system can reclaim RAM:
     * - `TRIM_MEMORY_RUNNING_LOW` (foreground, system low): trim to 50% capacity
     * - `TRIM_MEMORY_UI_HIDDEN` and above (app backgrounded or critical): clear entirely
     *
     * The gradual approach keeps the cache warm during normal reading while still shedding
     * weight in long sessions where memory pressure builds up.
     */
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val cache = SingletonImageLoader.get(this).memoryCache ?: return
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            cache.clear()
        } else if (level >= TRIM_MEMORY_RUNNING_LOW) {
            cache.trimToSize(cache.maxSize / 2)
        }
    }

    override fun getPackageName(): String {
        try {
            // Override the value passed as X-Requested-With in WebView requests
            val stackTrace = Looper.getMainLooper().thread.stackTrace
            val isChromiumCall = stackTrace.any { trace ->
                (trace.className.lowercase() in setOf("org.chromium.base.buildinfo", "org.chromium.base.apkinfo")) &&
                    (trace.methodName.lowercase() in setOf("getall", "getpackagename", "<init>"))
            }

            if (isChromiumCall) return WebViewUtil.spoofedPackageName(applicationContext)
        } catch (_: Exception) {
        }

        return super.getPackageName()
    }

    private fun setupNotificationChannels() {
        try {
            Notifications.createChannels(this)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to modify notification channels" }
        }
    }

    private inner class DisableIncognitoReceiver : BroadcastReceiver() {
        private var registered = false

        override fun onReceive(context: Context, intent: Intent) {
            basePreferences.incognitoMode().set(value = false)
        }

        fun register() {
            if (!registered) {
                ContextCompat.registerReceiver(
                    this@App,
                    this,
                    IntentFilter(ACTION_DISABLE_INCOGNITO_MODE),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                registered = true
            }
        }

        fun unregister() {
            if (registered) {
                unregisterReceiver(this)
                registered = false
            }
        }
    }

    companion object {
        private const val ACTION_DISABLE_INCOGNITO_MODE = "tachi.action.DISABLE_INCOGNITO_MODE"
    }
}
