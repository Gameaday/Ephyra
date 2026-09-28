package ephyra.presentation.core.components.adaptive

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
 * How far across its own width the detail pane travels at the end of a back gesture.
 *
 * A quarter of the width, matching the `initialOffsetX` / `targetOffsetX` of the `AnimatedContent`
 * transition this gesture previews. The gesture must end where the committed animation begins, or
 * the pane visibly jumps at the moment the finger lifts -- which reads as the gesture being
 * rejected even though it was accepted.
 */
private const val PANE_BACK_GESTURE_TRAVEL_FRACTION = 0.25f

/**
 * How much the pane fades during the gesture.
 *
 * A fifth rather than a full crossfade: the destination pane is visible behind, and fading the
 * outgoing pane all the way to zero would flash the background where the shared cover should be.
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
 * - Single-pane layout on compact/medium viewports with animated slide/fade transitions
 *   and predictive-back-compatible BackHandler.
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
        // Single-pane compact layout. The back gesture drives the *same* slide the tap-driven
        // `AnimatedContent` below uses, rather than a separate animation, so a pane dismissed by
        // swipe and one dismissed by tap are visually the same transition.
        //
        // `PredictiveBackProgress` is used rather than `BackHandler` precisely because this surface
        // has a directional animation to preview. With a bare `BackHandler` the user swipes, sees
        // nothing move, and then the pane disappears — which is worse than having no gesture at
        // all, because it advertises interactivity the surface does not have.
        var backProgress by remember { mutableFloatStateOf(0f) }
        PredictiveBackProgress(
            enabled = navigator.currentRole != ListDetailPaneRole.List,
            // The outgoing pane slides towards the start edge as the gesture advances.
            onProgress = { progress -> backProgress = progress },
            onCommit = {
                backProgress = 0f
                navigator.navigateBack()
            },
            // Cancelled: the pane was never committed, so it is still there. Clearing progress
            // lets the existing `AnimatedContent` snap back on its own; a separate reverse
            // animation here would fight that one.
            onCancelled = { backProgress = 0f },
        )

        Box(
            modifier = modifier
                .fillMaxSize()
                // While a back gesture is in flight the pane tracks the finger. A `graphicsLayer`
                // is render-only, so it cannot disturb the layout or the `AnimatedContent` state
                // machine underneath -- important because that machine already owns the committed
                // transition, and a second layout-affecting animation of the same movement is the
                // two-owners-one-axis problem again. `1 - progress` because the gesture travels
                // inward from the edge while the pane travels outward.
                .graphicsLayer {
                    val travel = (size.width * PANE_BACK_GESTURE_TRAVEL_FRACTION * (1f - backProgress))
                    translationX = -travel
                    alpha = 1f - (backProgress * PANE_BACK_GESTURE_FADE)
                },
        ) {
            AnimatedContent(
                targetState = navigator.currentRole,
                transitionSpec = {
                    if (targetState.ordinal > initialState.ordinal) {
                        (
                            slideInHorizontally(
                                animationSpec = tween(
                                    durationMillis = MotionTokens.DURATION_MEDIUM,
                                    easing = MotionTokens.EasingDecelerate,
                                ),
                                initialOffsetX = { fullWidth -> fullWidth / 4 },
                            ) + fadeIn(animationSpec = tween(MotionTokens.DURATION_MEDIUM))
                            ).togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(
                                    durationMillis = MotionTokens.DURATION_SHORT,
                                    easing = MotionTokens.EasingAccelerate,
                                ),
                                targetOffsetX = { fullWidth -> -fullWidth / 4 },
                            ) + fadeOut(animationSpec = tween(MotionTokens.DURATION_SHORT)),
                        )
                    } else {
                        (
                            slideInHorizontally(
                                animationSpec = tween(
                                    durationMillis = MotionTokens.DURATION_MEDIUM,
                                    easing = MotionTokens.EasingDecelerate,
                                ),
                                initialOffsetX = { fullWidth -> -fullWidth / 4 },
                            ) + fadeIn(animationSpec = tween(MotionTokens.DURATION_MEDIUM))
                            ).togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(
                                    durationMillis = MotionTokens.DURATION_SHORT,
                                    easing = MotionTokens.EasingAccelerate,
                                ),
                                targetOffsetX = { fullWidth -> fullWidth / 4 },
                            ) + fadeOut(animationSpec = tween(MotionTokens.DURATION_SHORT)),
                        )
                    }
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
