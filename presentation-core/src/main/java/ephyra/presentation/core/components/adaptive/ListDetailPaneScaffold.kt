package ephyra.presentation.core.components.adaptive

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ephyra.presentation.core.theme.MotionTokens
import ephyra.presentation.core.util.isExpandedWidthWindow
import ephyra.presentation.core.util.isTabletUi

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
        // Single Pane for Compact / Phone viewports with smooth animated transitions
        BackHandler(enabled = navigator.currentRole != ListDetailPaneRole.List) {
            navigator.navigateBack()
        }

        Box(modifier = modifier.fillMaxSize()) {
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
