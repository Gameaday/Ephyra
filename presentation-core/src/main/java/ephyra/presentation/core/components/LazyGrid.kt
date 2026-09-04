package ephyra.presentation.core.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Applies [Modifier.testTag] only when [tag] is non-null (keeps call sites clean).
 *
 * Compose [testTag] makes the tag available as a View semantics/content-description
 * that is discoverable by uiautomator-based macrobenchmarks (`By.desc`) and the
 * Compose testing framework (`onNodeWithTag`). No additional semantics flag is needed.
 */
private fun Modifier.testTagIfNotNull(tag: String?): Modifier =
    if (tag != null) this.testTag(tag) else this

@Composable
fun FastScrollLazyVerticalGrid(
    columns: GridCells,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    thumbAllowed: () -> Boolean = { true },
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    topContentPadding: Dp = Dp.Hairline,
    bottomContentPadding: Dp = Dp.Hairline,
    endContentPadding: Dp = Dp.Hairline,
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical =
        if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    userScrollEnabled: Boolean = true,
    testTag: String? = null,
    content: LazyGridScope.() -> Unit,
) {
    VerticalGridFastScroller(
        state = state,
        columns = columns,
        arrangement = horizontalArrangement,
        contentPadding = contentPadding,
        modifier = modifier.testTagIfNotNull(testTag),
        thumbAllowed = thumbAllowed,
        thumbColor = thumbColor,
        topContentPadding = topContentPadding,
        bottomContentPadding = bottomContentPadding,
        endContentPadding = endContentPadding,
    ) {
        LazyVerticalGrid(
            columns = columns,
            state = state,
            contentPadding = contentPadding,
            reverseLayout = reverseLayout,
            verticalArrangement = verticalArrangement,
            horizontalArrangement = horizontalArrangement,
            userScrollEnabled = userScrollEnabled,
            modifier = Modifier.testTagIfNotNull(testTag),
            content = content,
        )
    }
}
