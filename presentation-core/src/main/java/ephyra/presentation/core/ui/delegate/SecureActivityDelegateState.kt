package ephyra.presentation.core.ui.delegate

import ephyra.core.common.core.security.SecurityPreferences
import ephyra.presentation.core.util.system.AuthenticatorUtil

/**
 * Global state and lifecycle hooks for app-wide security.
 *
 * Lives in presentation-core so that feature modules (e.g. feature:security) can
 * call [unlock] without depending on the main :app module.
 */
object SecureActivityDelegateState {
    /**
     * Set to true if we need the first activity to authenticate.
     *
     * Always require unlock if app is killed.
     */
    var requireUnlock = true

    @Suppress("DEPRECATION")
    fun onApplicationStopped(preferences: SecurityPreferences) {
        if (!preferences.useAuthenticator().getSync()) return

        if (!AuthenticatorUtil.isAuthenticating) {
            if (requireUnlock) return
            if (preferences.lockAppAfter().getSync() > 0) {
                preferences.lastAppClosed().set(System.currentTimeMillis())
            }
        }
    }

    /**
     * Checks if unlock is needed when the app comes to the foreground.
     */
    @Suppress("DEPRECATION")
    fun onApplicationStart(preferences: SecurityPreferences) {
        if (!preferences.useAuthenticator().getSync()) return

        val lastClosedPref = preferences.lastAppClosed()

        if (!AuthenticatorUtil.isAuthenticating && !requireUnlock) {
            requireUnlock = when (val lockDelay = preferences.lockAppAfter().getSync()) {
                -1 -> false
                0 -> true
                else -> lastClosedPref.getSync() + lockDelay * 60_000 <= System.currentTimeMillis()
            }
        }

        lastClosedPref.delete()
    }

    fun unlock() {
        requireUnlock = false
    }
}
