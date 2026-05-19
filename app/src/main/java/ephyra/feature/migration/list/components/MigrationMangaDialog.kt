package ephyra.feature.migration.list.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import ephyra.presentation.core.i18n.pluralStringResource
import ephyra.presentation.core.i18n.stringResource

@Composable
fun MigrationMangaDialog(
    onDismissRequest: () -> Unit,
    copy: Boolean,
    totalCount: Int,
    skippedCount: Int,
    onMigrate: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = pluralStringResource(
                    resource = if (copy) {
                        ephyra.app.core.common.R.plurals.migrationListScreen_migrateDialog_copyTitle
                    } else {
                        ephyra.app.core.common.R.plurals.migrationListScreen_migrateDialog_migrateTitle
                    },
                    count = totalCount,
                    totalCount,
                ),
            )
        },
        text = {
            if (skippedCount > 0) {
                Text(
                    text = pluralStringResource(
                        resource = ephyra.app.core.common.R.plurals.migrationListScreen_migrateDialog_skipText,
                        count = skippedCount,
                        skippedCount,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onMigrate) {
                Text(
                    text = stringResource(
                        resource = if (copy) {
                            ephyra.app.core.common.R.string.migrationListScreen_migrateDialog_copyLabel
                        } else {
                            ephyra.app.core.common.R.string.migrationListScreen_migrateDialog_migrateLabel
                        },
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(ephyra.app.core.common.R.string.migrationListScreen_migrateDialog_cancelLabel))
            }
        },
    )
}
