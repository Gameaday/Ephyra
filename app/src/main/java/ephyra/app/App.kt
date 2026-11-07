package ephyra.app

import android.app.Application
import android.content.Context
import android.os.Looper
import android.webkit.WebView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import coil3.ImageLoader
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import ephyra.app.crash.CrashActivity
import ephyra.app.crash.GlobalExceptionHandler
import ephyra.app.di.initializeExtensionBridge
import ephyra.app.image.EphyraImageLoaderFactory
import ephyra.app.image.ImageCacheMemoryPolicy
import ephyra.app.startup.IncognitoModeMonitor
import ephyra.app.startup.SourceReconciler
import ephyra.app.startup.StartupTracker
import ephyra.app.startup.StartupWorkflowHost
import ephyra.core.common.core.security.PrivacyPreferences
import ephyra.core.common.core.security.SecurityPreferences
import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.util.lang.launchIO
import ephyra.core.common.util.system.GLUtil
import ephyra.core.common.util.system.ImageUtil
import ephyra.core.common.util.system.WebViewUtil
import ephyra.core.common.util.system.logcat
import ephyra.core.migration.Migrator
import ephyra.core.migration.migrations.migrations
import ephyra.data.cache.CoverCache
import ephyra.data.notification.Notifications
import ephyra.domain.base.BasePreferences
import ephyra.domain.source.diagnostics.SourceResolutionDiagnostics
import ephyra.domain.source.registry.SourceRegistry
import ephyra.domain.source.service.SourceManager
import ephyra.domain.ui.UiPreferences
import ephyra.domain.updates.interactor.GetUpdates
import ephyra.domain.workflow.StartupStep
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.ui.delegate.SecureActivityDelegateState
import ephyra.presentation.core.util.system.setAppCompatDelegateThemeMode
import ephyra.presentation.widget.WidgetManager
import ephyra.telemetry.TelemetryConfig
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import kotlinx.coroutines.flow.catch
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
    lateinit var sourceManagerProvider: javax.inject.Provider<SourceManager>

    /**
     * Defect instrument for `DEF-029`: reports where the app's authorities disagree about which
     * sources exist and what their ids are. Observational only; deleted when `SRC-011` gives that
     * fact a single owner.
     */
    @Inject
    lateinit var sourceResolutionDiagnostics: SourceResolutionDiagnostics

    /** The one source-existence authority (`SRC-011`/`ADR-0012`); the registry half of `RECONCILE_SOURCES`. */
    @Inject
    lateinit var sourceRegistry: SourceRegistry

    /**
     * A3: the startup workflow state model. The lifecycle callbacks remain the effect executor —
     * rethrow paths never settle (the state dies with the process) — so this host owns only the
     * reducer state and rule-8 correlation.
     */
    private val startupWorkflow = StartupWorkflowHost()

    /** Executes `RECONCILE_SOURCES`; built lazily because only the async init path uses it. */
    private val sourceReconciler by lazy { SourceReconciler(sourceRegistry, sourceManagerProvider) }

    /** B5/`OPS-001`: the Coil image pipeline, split out of this class's `newImageLoader`. */
    private val imageLoaderFactory by lazy {
        EphyraImageLoaderFactory(this, networkHelper, coverCache, sourceManagerProvider) { verboseLoggingEnabled }
    }

    /** B5/`OPS-001`: the image-cache trim/restore policy, split out of the lifecycle callbacks. */
    private val imageCacheMemoryPolicy = ImageCacheMemoryPolicy()

    /** B5/`OPS-001`: the incognito notification + receiver surface, split out of this class. */
    private val incognitoModeMonitor by lazy { IncognitoModeMonitor(this, basePreferences) }

    @Volatile
    private var verboseLoggingEnabled = false

    override val workManagerConfiguration: androidx.work.Configuration
        get() {
            StartupTracker.complete(StartupTracker.Milestone.WORKMANAGER_CONFIGURED)
            return androidx.work.Configuration.Builder()
                .setWorkerFactory(ephyra.app.data.work.AppWorkerFactory())
                .build()
        }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // A3: open the startup workflow before the first phase runs.
        startupWorkflow.begin()

        // Phase 1: Logging — must succeed first
        try {
            if (!LogcatLogger.isInstalled) {
                LogcatLogger.install()
                val minLogPriority = if (BuildConfig.DEBUG) LogPriority.DEBUG else LogPriority.INFO
                LogcatLogger.loggers += AndroidLogcatLogger(minLogPriority)
            }
        } catch (e: Throwable) {
            // Last resort — logging is unavailable
            android.util.Log.e("Ephyra", "Failed to initialize logcat", e)
        }
        startupWorkflow.settled(StartupStep.LOGGING, ok = true)

        // Phase 2: Global crash handler
        try {
            GlobalExceptionHandler.initialize(base, CrashActivity::class.java)
        } catch (e: Throwable) {
            android.util.Log.e("Ephyra", "Failed to initialize crash handler", e)
        }
        startupWorkflow.settled(StartupStep.CRASH_HANDLER, ok = true)
    }

    override fun onCreate() {
        // Phase 3: Extension bridge initialization — must happen BEFORE super.onCreate()
        // so Injekt is primed before Hilt injects members or starts background tasks
        try {
            initializeExtensionBridge(this)
            // The rethrow path above deliberately never settles: the state dies with the process.
            startupWorkflow.settled(StartupStep.DI_CONTAINER, ok = true)
        } catch (e: Throwable) {
            StartupTracker.recordError(StartupStep.APP_CREATED, e)
            logcat(LogPriority.ERROR, e) { "Critical DI container initialization failed" }
            throw e
        }

        // Initialize Tachiyomi extension-lib AppInfo ABI shim
        try {
            eu.kanade.tachiyomi.AppInfo.init(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "AppInfo init failed" }
        }

        super<Application>.onCreate()
        ephyra.app.data.work.CoverCacheMaintenanceWorker.setupTask(this)
        ephyra.app.data.work.CoverCacheMaintenanceWorker.enqueueOneTimeAudit(this)
        startupWorkflow.settled(StartupStep.APP_CREATED, ok = true)
        // The workflow's enum order is the declared contract: the worker registrations above
        // have already returned, and WorkManager's own configuration settles lazily on first use.
        startupWorkflow.settled(StartupStep.REGISTER_WORKERS, ok = true)

        // Phase 4: Telemetry (non-critical)
        try {
            TelemetryConfig.init(applicationContext)
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Telemetry init failed — non-fatal" }
        }
        startupWorkflow.settled(StartupStep.TELEMETRY, ok = true)

        // Avoid potential crashes from multiple WebView processes
        try {
            val process = getProcessName()
            if (packageName != process) WebView.setDataDirectorySuffix(process)
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "WebView data directory setup failed" }
        }

        // Phase 5: Notification channels (non-critical)
        setupNotificationChannels()
        startupWorkflow.settled(StartupStep.NOTIFICATIONS, ok = true)

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        val scope = ProcessLifecycleOwner.get().lifecycleScope

        // Phase 6: Reactive bindings (preference flows)
        try {
            // B5/`OPS-001`: the incognito notification + receiver surface lives in
            // IncognitoModeMonitor now; the flow's own catch moved with it.
            incognitoModeMonitor.start(scope)

            privacyPreferences.analytics()
                .changes()
                .onEach(TelemetryConfig::setAnalyticsEnabled)
                .catch { e -> logcat(LogPriority.ERROR, e) { "Failed to monitor analytics" } }
                .launchIn(scope)

            privacyPreferences.crashlytics()
                .changes()
                .onEach(TelemetryConfig::setCrashlyticsEnabled)
                .catch { e -> logcat(LogPriority.ERROR, e) { "Failed to monitor crashlytics" } }
                .launchIn(scope)

            // The "Custom hardware bitmap threshold" advanced setting was retired in the Mihon
            // 0.21.1 adoption (WS10): the device's real texture limit is the only value that makes
            // sense, so it is applied directly instead of being a user-tunable preference.
            ImageUtil.hardwareBitmapThreshold = GLUtil.DEVICE_TEXTURE_LIMIT
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Startup reactive binding setup failed" }
        }
        startupWorkflow.settled(StartupStep.REACTIVE_BINDINGS, ok = true)

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

                try {
                    sourceResolutionDiagnostics.reportRegistration("startup")
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Source registration diagnostic failed" }
                }

                // RECONCILE_SOURCES — the startup step the B-034 audit found absent (A3). It
                // executes here, in the async-init scope, because the source map builds
                // asynchronously. The reconciler never throws (a timeout or a registry failure
                // is outcome data), so this settles ok = true under the same containment policy
                // as every other non-critical phase; the disagreement lists are logged for the
                // audit trail.
                val reconciliation = sourceReconciler.reconcile(SOURCE_RECONCILE_TIMEOUT_MS)
                if (reconciliation.error == null) {
                    logcat {
                        "Source reconciliation: ready=" + reconciliation.ready +
                            " registry=" + reconciliation.registryCount +
                            " loaded=" + reconciliation.mapCount +
                            " registeredNotLoaded=" + reconciliation.registeredButNotLoaded +
                            " loadedNotRegistered=" + reconciliation.loadedButNotRegistered
                    }
                } else {
                    logcat(LogPriority.WARN, reconciliation.error) { "Source reconciliation failed" }
                }
                startupWorkflow.settled(StartupStep.RECONCILE_SOURCES, ok = true)
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e) { "Async startup initialization failed" }
            } finally {
                startupWorkflow.settled(StartupStep.ASYNC_INIT, ok = true)
            }
        }
    }

    private suspend fun initializeMigrator() {
        try {
            val preference = preferenceStore.getInt(Preference.appStateKey("last_version_code"), 0)
            logcat { "Migration from ${preference.get()} to ${BuildConfig.VERSION_CODE}" }
            StartupTracker.complete(StartupTracker.Milestone.MIGRATOR_STARTED)
            Migrator.initialize(
                context = this,
                old = preference.get(),
                new = BuildConfig.VERSION_CODE,
                migrations = migrations,
            ) {
                logcat { "Updating last version to ${BuildConfig.VERSION_CODE}" }
                preference.set(BuildConfig.VERSION_CODE)
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "initializeMigrator failed; canceling and releasing Migrator initGate" }
            StartupTracker.recordError(StartupTracker.Milestone.MIGRATOR_COMPLETE, e)
            Migrator.cancelAndRelease()
            throw e
        }
    }

    /**
     * B5/`OPS-001`: the builder logic lives in [EphyraImageLoaderFactory]; `App` remains the
     * [SingletonImageLoader.Factory] because Coil asks it for the application loader. The
     * baseline capture stays adjacent to the build (in [imageCacheMemoryPolicy]) so a trim can
     * never compute from a size another trim has already halved.
     */
    override fun newImageLoader(context: Context): ImageLoader =
        imageLoaderFactory.create(context).also { imageCacheMemoryPolicy.captureBaseline(it) }


    override fun onStart(owner: LifecycleOwner) {
        SecureActivityDelegateState.onApplicationStart(securityPreferences)
        imageCacheMemoryPolicy.restore(this)

        // Prime the content-source map as soon as the app is foregrounded. `SourceManager` builds
        // its source map asynchronously, and a screen restored after the system killed the process
        // would otherwise call `getOrStub` before that work had even started, resolve a placeholder
        // stub, and present it as "source not installed". Constructing the (singleton) manager here
        // starts the load up front; a screen that still has to wait observes `isInitialized`.
        try {
            sourceManagerProvider.get()
        } catch (e: Throwable) {
            logcat(LogPriority.WARN, e) { "Failed to prime source manager on foreground" }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        SecureActivityDelegateState.onApplicationStopped(securityPreferences)
    }

    /**
     * Called by the system when it determines that memory is running low. The policy — halve
     * to the configured size at foreground pressure, clear at critical, restore on the next
     * foreground — lives in [imageCacheMemoryPolicy] since B5; the rationale moved with it.
     */
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        imageCacheMemoryPolicy.trim(this, level)
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

    companion object {
                /** Bound on waiting for the source map to become ready during startup reconciliation (A3). */
        private const val SOURCE_RECONCILE_TIMEOUT_MS = 15_000L
    }
}
