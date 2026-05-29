package ephyra.feature.settings.screen.debug

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import ephyra.domain.backup.model.Backup
import ephyra.presentation.core.components.AppBar
import ephyra.presentation.core.components.AppBarActions
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.util.system.copyToClipboard
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.protobuf.schema.ProtoBufSchemaGenerator

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Composable
fun BackupSchemaScreen(
    navController: NavController = LocalNavController.current,
) {
    val context = LocalContext.current
    val schema = remember { ProtoBufSchemaGenerator.generateSchemaText(Backup.serializer().descriptor) }

    Scaffold(
        topBar = {
            AppBar(
                title = BackupSchemaScreen.TITLE,
                navigateUp = { navController.popBackStack() },
                actions = {
                    AppBarActions(
                        persistentListOf(
                            AppBar.Action(
                                title = stringResource(ephyra.app.core.common.R.string.action_copy_to_clipboard),
                                icon = Icons.Default.ContentCopy,
                                onClick = {
                                    context.copyToClipboard(BackupSchemaScreen.TITLE, schema)
                                },
                            ),
                        ),
                    )
                },
                scrollBehavior = it,
            )
        },
    ) { contentPadding ->
        Text(
            text = schema,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(16.dp),
            fontFamily = FontFamily.Monospace,
        )
    }
}

object BackupSchemaScreen {
    const val TITLE = "Backup file schema"
}
