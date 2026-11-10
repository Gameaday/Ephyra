package ephyra.presentation.core.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/*
 * The activity boundary's gesture host: one crossfade, no movement, every path.
 *
 * ## History
 *
 * PR #339 made the reader and WebView boundaries platform-owned ("the activity boundary motion
 * belongs to the platform"), reasoning that `enableOnBackInvokedCallback` already gave the system a
 * predictive cross-activity animation. In practice that system reveal is a scale-and-slide that
 * reads as elements flying and zooming into the background — the exact complaint in the owner's
 * motion audit — and the open path had no preview at all. The owner directive of 2026-11-09 locked
 * the replacement: a minimal crossfade with **no movement** at this boundary, and the app owns it.
 *
 * ## The two halves
 *
 * The window animations live in the theme (`Theme.Tachiyomi.ActivityBoundary` →
 * `ActivityBoundaryWindowAnimations` → pure-alpha `activity_boundary_fade_in/out`), because a theme
 * applies to *every* start path — intents, deep links, shortcuts — with no starter cooperation,
 * on all API levels. What a theme cannot do is intercept the predictive back gesture: when the
 * system finishes the activity after a completed swipe, it plays its own cross-activity reveal
 * and bypasses the theme entirely. This host is the second half: `PredictiveBackProgress` commits
 * through `onExit` (the activity's `finish()`), so the *theme's* close crossfade plays instead, and
 * a drag-proportional dim gives the gesture a live preview while the finger is still down.
 *
 * ## Why a dim and not a movement
 *
 * The preview is gesture-coupled opacity — the scrim tracks the finger's progress and resolves to
 * zero on cancellation — which participates in the gesture without introducing the autonomous
 * movement the directive removes. A cross-activity scale reveal that "peeks" the parent activity
 * cannot be faithful here anyway: the activity beneath is not on screen, so there is nothing real
 * to reveal, and the peek is a mockup of one.
 *
 * Reduced motion skips the dim scrim but the window fades still honour the OS animator scale,
 * which already reduces them to nothing at scale 0.
 */

/** The dim the back gesture drives toward at full swipe. Barely-there by design: it previews, not narrates. */
private const val EXIT_DIM = 0.25f

/**
 * Wraps an activity's content so the predictive back gesture previews a dim and commits through
 * [onExit] (normally `::finish`), keeping the activity boundary a single app-owned crossfade.
 *
 * Compose inside [content] owns its own back handling as usual; this host only participates at the
 * activity boundary, where Compose navigation has ended and the system gesture would otherwise
 * finish the activity with the system reveal.
 */
@Composable
fun ActivityBoundaryCrossfadeHost(
    onExit: () -> Unit,
    content: @Composable () -> Unit,
) {
    var gestureProgress by remember { mutableFloatStateOf(0f) }
    val reducedMotion = effectiveReducedMotion(rememberSystemReducedMotion(), LocalMotionPreference.current)

    PredictiveBackProgress(
        enabled = true,
        onProgress = { progress -> gestureProgress = progress },
        onCommit = onExit,
        onCancelled = { gestureProgress = 0f },
    )

    Box {
        content()
        if (!reducedMotion && gestureProgress > 0f) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = gestureProgress * EXIT_DIM)),
            )
        }
    }
}
