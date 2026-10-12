package ephyra.presentation.core.ui.navigation

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the user has asked for reduced motion, threaded to every transition owner.
 *
 * Provided once at the navigation root so all screens in a graph agree. If each transition read
 * the platform setting independently, two screens could disagree mid-navigation and animate
 * inconsistently, which is worse than animating at all.
 */
val LocalMotionPreference = compositionLocalOf { false }

/**
 * The effective reduced-motion state for this app: the OS setting **or** the in-app preference.
 *
 * Extracted as a pure function so the merge rule is asserted directly rather than only through a
 * composition. The OS setting is a floor, not a default — a user who has turned animations off
 * system-wide must keep them off here — and the app preference is additive on top of it, so the two
 * can never cancel each other out.
 */
fun effectiveReducedMotion(systemReducedMotion: Boolean, appReduceMotion: Boolean): Boolean =
    systemReducedMotion || appReduceMotion

/**
 * True when the platform has animations disabled or scaled to zero.
 *
 * Read from the system rather than a private in-app toggle, because the setting users actually use
 * is the OS one. A value of exactly 0 disables animation; a small non-zero scale is a speed
 * preference, not a request to remove motion, so only the zero case is treated as reduced.
 */
@Composable
fun rememberSystemReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
            ) == 0f
        }.getOrDefault(false)
    }
}
