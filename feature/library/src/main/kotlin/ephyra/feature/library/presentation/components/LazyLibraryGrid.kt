package ephyra.feature.library.presentation.components

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import ephyra.presentation.core.components.FastScrollLazyVerticalGrid
import ephyra.presentation.core.theme.LocalBrandedTheme
import ephyra.presentation.core.util.plus

@Composable
internal fun LazyLibraryGrid(
    modifier: Modifier = Modifier,
    columns: Int,
    contentPadding: PaddingValues,
    onColumnsChange: ((Int) -> Unit)? = null,
    content: LazyGridScope.() -> Unit,
) {
    val config = LocalBrandedTheme.current
    var scaleAccumulator by remember(columns) { mutableStateOf(1f) }
    val gestureModifier = if (onColumnsChange != null && columns > 0) {
        Modifier.pointerInput(columns) {
            detectTransformGestures { _, _, zoom, _ ->
                scaleAccumulator *= zoom
                if (scaleAccumulator > 1.25f) {
                    if (columns > 1) {
                        onColumnsChange(columns - 1)
                    }
                    scaleAccumulator = 1f
                } else if (scaleAccumulator < 0.75f) {
                    if (columns < 10) {
                        onColumnsChange(columns + 1)
                    }
                    scaleAccumulator = 1f
                }
            }
        }
    } else {
        Modifier
    }

    FastScrollLazyVerticalGrid(
        columns = if (columns == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(columns),
        modifier = modifier.then(gestureModifier),
        contentPadding = contentPadding + PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(config.gridVerticalSpacing),
        horizontalArrangement = Arrangement.spacedBy(config.gridHorizontalSpacing),
        content = content,
    )
}

internal fun LazyGridScope.globalSearchItem(
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
) {
    if (!searchQuery.isNullOrEmpty()) {
        item(
            span = { GridItemSpan(maxLineSpan) },
            contentType = { "library_global_search_item" },
        ) {
            GlobalSearchItem(
                searchQuery = searchQuery,
                onClick = onGlobalSearchClicked,
            )
        }
    }
}
