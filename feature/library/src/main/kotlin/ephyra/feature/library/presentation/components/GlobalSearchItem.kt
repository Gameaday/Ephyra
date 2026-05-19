package ephyra.feature.library.presentation.components

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import ephyra.presentation.core.i18n.stringResource

@Composable
internal fun GlobalSearchItem(
    searchQuery: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        modifier = modifier,
        onClick = onClick,
    ) {
        Text(
            text = stringResource(ephyra.app.core.common.R.string.action_global_search_query, searchQuery),
            modifier = Modifier.zIndex(99f),
        )
    }
}
