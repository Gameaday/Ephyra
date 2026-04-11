package ephyra.feature.browse.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import ephyra.core.common.util.system.LocaleHelper
import ephyra.feature.browse.extension.ExtensionFilterState
import ephyra.i18n.MR
import ephyra.presentation.core.components.AppBar
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.components.material.padding
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.screens.EmptyScreen

@Composable
fun ExtensionFilterScreen(
    navigateUp: () -> Unit,
    state: ExtensionFilterState.Success,
    onClickToggle: (String) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.label_extensions),
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        if (state.isEmpty) {
            EmptyScreen(
                stringRes = MR.strings.empty_screen,
                modifier = Modifier.padding(contentPadding),
            )
            return@Scaffold
        }
        ExtensionFilterContent(
            contentPadding = contentPadding,
            state = state,
            onClickLang = onClickToggle,
        )
    }
}

@Composable
private fun ExtensionFilterContent(
    contentPadding: PaddingValues,
    state: ExtensionFilterState.Success,
    onClickLang: (String) -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        contentPadding = contentPadding,
    ) {
        items(state.languages, key = { it }, contentType = { "language" }) { language ->
            Row(
                modifier = Modifier
                    .animateItem()
                    .fillMaxWidth()
                    .padding(
                        horizontal = MaterialTheme.padding.medium,
                        vertical = MaterialTheme.padding.small,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = LocaleHelper.getSourceDisplayName(language, context),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = language in state.enabledLanguages,
                    onCheckedChange = { onClickLang(language) },
                )
            }
        }
    }
}
