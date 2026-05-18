package ephyra.presentation.core.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import ephyra.i18n.MR
import ephyra.presentation.core.screens.EmptyScreen
import ephyra.presentation.core.screens.EmptyScreenAction
import ephyra.presentation.theme.TachiyomiPreviewTheme
import kotlinx.collections.immutable.persistentListOf

@PreviewLightDark
@Composable
private fun NoActionPreview() {
    TachiyomiPreviewTheme {
        Surface {
            EmptyScreen(
                stringRes = ephyra.i18n.R.string.empty_screen,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun WithActionPreview() {
    TachiyomiPreviewTheme {
        Surface {
            EmptyScreen(
                stringRes = ephyra.i18n.R.string.empty_screen,
                actions = persistentListOf(
                    EmptyScreenAction(
                        stringRes = ephyra.i18n.R.string.action_retry,
                        icon = Icons.Outlined.Refresh,
                        onClick = {},
                    ),
                    EmptyScreenAction(
                        stringRes = ephyra.i18n.R.string.getting_started_guide,
                        icon = Icons.AutoMirrored.Outlined.HelpOutline,
                        onClick = {},
                    ),
                ),
            )
        }
    }
}
