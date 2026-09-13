package ephyra.presentation.core.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import ephyra.presentation.core.screens.EmptyScreen
import ephyra.presentation.core.screens.EmptyScreenAction
import ephyra.presentation.theme.EphyraPreviewTheme
import kotlinx.collections.immutable.persistentListOf

@PreviewLightDark
@Composable
private fun NoActionPreview() {
    EphyraPreviewTheme {
        Surface {
            EmptyScreen(
                stringRes = ephyra.app.core.common.R.string.empty_screen,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun WithActionPreview() {
    EphyraPreviewTheme {
        Surface {
            EmptyScreen(
                stringRes = ephyra.app.core.common.R.string.empty_screen,
                actions = persistentListOf(
                    EmptyScreenAction(
                        stringRes = ephyra.app.core.common.R.string.action_retry,
                        icon = Icons.Outlined.Refresh,
                        onClick = {},
                    ),
                    EmptyScreenAction(
                        stringRes = ephyra.app.core.common.R.string.getting_started_guide,
                        icon = Icons.AutoMirrored.Outlined.HelpOutline,
                        onClick = {},
                    ),
                ),
            )
        }
    }
}
