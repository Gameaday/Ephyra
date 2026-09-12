package ephyra.presentation.core.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import ephyra.presentation.core.components.adaptive.ListDetailPaneScaffold

@Composable
fun TwoPanelBox(
    startContent: @Composable BoxScope.() -> Unit,
    endContent: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    contentWindowInsets: WindowInsets = WindowInsets(0),
) {
    val direction = LocalLayoutDirection.current
    val padding = contentWindowInsets.asPaddingValues()
    val startPadding = padding.calculateStartPadding(direction)
    val endPadding = padding.calculateEndPadding(direction)
    ListDetailPaneScaffold(
        modifier = modifier.fillMaxSize(),
        isDualPane = true,
        showDivider = false,
        listPane = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = startPadding),
                content = startContent,
            )
        },
        detailPane = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = endPadding),
                content = endContent,
            )
        },
    )
}
