package ephyra.presentation.core.components.adaptive

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ephyra.presentation.core.theme.MotionTokens
import ephyra.presentation.core.ui.navigation.PredictiveBackProgress
import ephyra.presentation.core.util.isExpandedWidthWindow
import ephyra.presentation.core.util.isTabletUi

/**
 * How much the pane dims during the back gesture.
 *
 * Gesture-coupled opacity, the same preview the activity boundary host uses: with the committed
 * pane change now a crossfade, the gesture can no longer preview a travel it does not perform, so
 * it previews "you are leaving" instead. A fifth rather than the boundary's deeper dim because
 * the pane is the whole surface here — a deeper dim would read as the pane already leaving
 * before the finger lifts.
 */
private const val PANE_BACK_GESTURE_FADE = 0.2f

/**
 * Roles for adaptive multi-pane scaffolds.
 */
enum class ListDetailPaneRole {
    List,
    Detail,
    Extra,
}

/**
 * Navigator state controlling active pane and navigation on adaptive scaffolds.
 */
@Stable
class ListDetailPaneScaffoldNavigator(
    initialRole: ListDetailPaneRole = ListDetailPaneRole.List,
) {
    var currentRole by mutableStateOf(initialRole)
        private set

    val isShowingDetail: Boolean
        get() = currentRole == ListDetailPaneRole.Detail

    fun navigateTo(role: ListDetailPaneRole) {
        currentRole = role
    }

    fun navigateBack(): Boolean {
        return if (currentRole != ListDetailPaneRole.List) {
            currentRole = ListDetailPaneRole.List
            true
        } else {
            false
        }
    }

    companion object {
        val Saver: Saver<ListDetailPaneScaffoldNavigator, String> = Saver(
            save = { it.currentRole.name },
            restore = { ListDetailPaneRole.valueOf(it).let(::ListDetailPaneScaffoldNavigator) },
        )
    }
}

@Composable
fun rememberListDetailPaneScaffoldNavigator(
    initialRole: ListDetailPaneRole = ListDetailPaneRole.List,
): ListDetailPaneScaffoldNavigator {
    return rememberSaveable(saver = ListDetailPaneScaffoldNavigator.Saver) {
        ListDetailPaneScaffoldNavigator(initialRole)
    }
}

/**
 * Material 3 Adaptive List-Detail Pane Scaffold.
 *
 * Implements Google's Modern Android Development adaptive layout patterns:
 * - Dual-pane layout on expanded/tablet/foldable viewports (width > 840dp or isTabletUi).
 * - Single-pane layout on compact/medium viewports with a crossfade transition and a
 *   predictive-back dim preview.
 * - Supports custom pane proportioning, vertical divider, and role navigation.
 */
@Composable
fun ListDetailPaneScaffold(
    listPane: @Composable () -> Unit,
    detailPane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    extraPane: (@Composable () -> Unit)? = null,
    navigator: ListDetailPaneScaffoldNavigator = rememberListDetailPaneScaffoldNavigator(),
    isDualPane: Boolean = isExpandedWidthWindow() || isTabletUi(),
    listPaneMaxWidth: Dp = 450.dp,
    showDivider: Boolean = true,
) {
    if (isDualPane) {
        // Dual Pane side-by-side for Foldables & Tablets
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val totalWidth = maxWidth
            val calculatedListWidth = (totalWidth * 0.45f).coerceIn(320.dp, listPaneMaxWidth)

            Row(modifier = Modifier.fillMaxSize()) {
                // List Pane (Primary / Master)
                Box(
                    modifier = Modifier
                        .width(calculatedListWidth)
                        .fillMaxHeight(),
                ) {
                    listPane()
                }

                if (showDivider) {
                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight(),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }

                // Detail Pane (Secondary / Detail)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    detailPane()
                }

                // Optional Extra Pane (Inspector / Metadata)
                if (extraPane != null && navigator.currentRole == ListDetailPaneRole.Extra) {
                    if (showDivider) {
                        VerticalDivider(
                            modifier = Modifier.fillMaxHeight(),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(calculatedListWidth)
                            .fillMaxHeight(),
                    ) {
                        extraPane()
                    }
                }
            }
        }
    } else {
        // Single-pane compact layout. The committed pane change is one crossfade, and the back
        // gesture previews it with a drag-proportional dim — the same gesture-coupled opacity the
        // activity boundary host uses, for the same reason: the owner's motion audit (2026-11-09)
        // removed pane movement, so the gesture previews "you are leaving" rather than a travel
        // it can no longer honestly show.
        //
        // `PredictiveBackProgress` is used rather than `BackHandler` precisely because this surface
        // has a live preview to show. With a bare `BackHandler` the user swipes, sees nothing
        // change, and then the pane disappears — which is worse than having no gesture at all,
        // because it advertises interactivity the surface does not have.
        var backProgress by remember { mutableFloatStateOf(0f) }
        PredictiveBackProgress(
            enabled = navigator.currentRole != ListDetailPaneRole.List,
            // The pane dims with the finger as the gesture advances.
            onProgress = { progress -> backProgress = progress },
            onCommit = {
                // The dim hands over to the committed crossfade: the outgoing pane fades out from
                // full alpha, so the dim is cleared here rather than lingering over the pane that
                // replaces it.
                backProgress = 0f
                navigator.navigateBack()
            },
            // Cancelled: the pane was never committed, so it is still there. Clearing progress
            // restores the dim to zero in the same frame the gesture gave up in.
            onCancelled = { backProgress = 0f },
        )

        Box(
            modifier = modifier
                .fillMaxSize()
                // While a back gesture is in flight the pane dims with the finger. A
                // `graphicsLayer` is render-only, so it cannot disturb the layout or the
                // `AnimatedContent` state machine underneath — important because that machine
                // owns the committed transition, and a second layout-affecting animation would be
                // the two-owners-one-axis problem again.
                .graphicsLayer {
                    alpha = 1f - (backProgress * PANE_BACK_GESTURE_FADE)
                },
        ) {
            AnimatedContent(
                targetState = navigator.currentRole,
                transitionSpec = {
                    // One crossfade, both directions: the pane slide was removed by the owner's
                    // motion audit (2026-11-09), so opening and dismissing a pane are the same
                    // minimal change rather than two travels.
                    (
                        fadeIn(animationSpec = tween(MotionTokens.DURATION_MEDIUM))
                        ).togetherWith(
                        fadeOut(animationSpec = tween(MotionTokens.DURATION_SHORT)),
                    )
                },
                label = "ListDetailPaneTransition",
            ) { role ->
                when (role) {
                    ListDetailPaneRole.List -> listPane()
                    ListDetailPaneRole.Detail -> detailPane()
                    ListDetailPaneRole.Extra -> extraPane?.invoke() ?: detailPane()
                }
            }
        }
    }
}
