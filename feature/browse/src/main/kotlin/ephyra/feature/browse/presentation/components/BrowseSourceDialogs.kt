package ephyra.feature.browse.presentation.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import ephyra.domain.manga.model.Manga
import ephyra.i18n.MR
import ephyra.presentation.core.i18n.stringResource

@Composable
fun RemoveMangaDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    mangaToRemove: Manga,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(ephyra.i18n.R.string.action_cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    onConfirm()
                },
            ) {
                Text(text = stringResource(ephyra.i18n.R.string.action_remove))
            }
        },
        title = {
            Text(text = stringResource(ephyra.i18n.R.string.are_you_sure))
        },
        text = {
            Text(text = stringResource(ephyra.i18n.R.string.remove_manga, mangaToRemove.title))
        },
    )
}
