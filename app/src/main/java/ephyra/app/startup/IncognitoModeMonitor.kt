package ephyra.app.startup

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import ephyra.app.BuildConfig
import ephyra.core.common.util.system.cancelNotification
import ephyra.core.common.util.system.logcat
import ephyra.core.common.util.system.notify
import ephyra.data.notification.Notifications
import ephyra.domain.base.BasePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority

/**
 * Owns the incognito-mode runtime surface: the ongoing "incognito is on" notification, the
 * broadcast receiver that turns it off, and the preference flow that drives both. Split out of
 * `App` by B5 (`OPS-001`) so `App` is startup coordination only; the behaviour is moved verbatim.
 *
 * [start] is called once from the reactive-bindings phase of `App.onCreate` with the process
 * lifecycle scope. While incognito is enabled the notification is shown — with a
 * PendingIntent broadcasting [ACTION_DISABLE_INCOGNITO_MODE] — and the receiver is registered;
 * on disable both are torn down.
 */
@SuppressLint("LaunchActivityFromNotification")
class IncognitoModeMonitor(
    private val app: Context,
    private val basePreferences: BasePreferences,
) {
    private val disableIncognitoReceiver = DisableIncognitoReceiver()

    /** Starts monitoring the incognito preference for as long as [scope] lives. */
    fun start(scope: CoroutineScope) {
        basePreferences.incognitoMode().changes()
            .onEach { enabled ->
                if (enabled) {
                    disableIncognitoReceiver.register()
                    if (ContextCompat.checkSelfPermission(
                            app,
                            android.Manifest.permission.POST_NOTIFICATIONS,
                        ) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        app.notify(
                            Notifications.ID_INCOGNITO_MODE,
                            Notifications.CHANNEL_INCOGNITO_MODE,
                        ) {
                            setContentTitle(app.getString(ephyra.app.core.common.R.string.pref_incognito_mode))
                            setContentText(app.getString(ephyra.app.core.common.R.string.notification_incognito_text))
                            setSmallIcon(ephyra.app.R.drawable.ic_glasses_24dp)
                            setOngoing(true)

                            val pendingIntent = PendingIntent.getBroadcast(
                                app,
                                0,
                                Intent(ACTION_DISABLE_INCOGNITO_MODE).setPackage(BuildConfig.APPLICATION_ID),
                                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
                            )
                            setContentIntent(pendingIntent)
                        }
                    }
                } else {
                    disableIncognitoReceiver.unregister()
                    app.cancelNotification(Notifications.ID_INCOGNITO_MODE)
                }
            }
            .catch { e -> logcat(LogPriority.ERROR, e) { "Failed to monitor incognito mode" } }
            .launchIn(scope)
    }

    private inner class DisableIncognitoReceiver : BroadcastReceiver() {
        private var registered = false

        override fun onReceive(context: Context, intent: Intent) {
            basePreferences.incognitoMode().set(value = false)
        }

        fun register() {
            if (!registered) {
                ContextCompat.registerReceiver(
                    app,
                    this,
                    IntentFilter(ACTION_DISABLE_INCOGNITO_MODE),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                registered = true
            }
        }

        fun unregister() {
            if (registered) {
                app.unregisterReceiver(this)
                registered = false
            }
        }
    }

    companion object {
        /** The action the notification's PendingIntent broadcasts to end incognito mode. */
        const val ACTION_DISABLE_INCOGNITO_MODE = "tachi.action.DISABLE_INCOGNITO_MODE"
    }
}
