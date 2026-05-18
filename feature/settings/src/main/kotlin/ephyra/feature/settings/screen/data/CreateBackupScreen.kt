package ephyra.feature.settings.screen.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ephyra.core.common.util.system.DeviceUtil
import ephyra.domain.backup.model.BackupOptions
import ephyra.domain.backup.service.BackupScheduler
import ephyra.presentation.core.components.AppBar
import ephyra.presentation.core.components.LabeledCheckbox
import ephyra.presentation.core.components.LazyColumnWithAction
import ephyra.presentation.core.components.SectionCard
import ephyra.presentation.core.components.WarningBanner
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.util.Screen
import ephyra.presentation.core.util.system.toast
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.update
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CreateBackupScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { CreateBackupScreenModel() }
        val state by model.state.collectAsStateWithLifecycle()

        val chooseBackupDir = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/*"),
        ) {
            if (it != null) {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                model.createBackup(it)
                navigator.pop()
            }
        }

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(ephyra.i18n.R.string.pref_create_backup),
                    navigateUp = navigator::pop,
                    scrollBehavior = it,
                )
            },
        ) { contentPadding ->
            LazyColumnWithAction(
                contentPadding = contentPadding,
                actionLabel = stringResource(ephyra.i18n.R.string.action_create),
                actionEnabled = state.options.canCreate(),
                onClickAction = {
                    if (!model.isBackupRunning()) {
                        try {
                            chooseBackupDir.launch(model.getBackupFilename())
                        } catch (e: ActivityNotFoundException) {
                            context.toast(ephyra.i18n.R.string.file_picker_error)
                        }
                    } else {
                        context.toast(ephyra.i18n.R.string.backup_in_progress)
                    }
                },
            ) {
                if (DeviceUtil.isMiui && DeviceUtil.isMiuiOptimizationDisabled()) {
                    item {
                        WarningBanner(stringResource(ephyra.i18n.R.string.restore_miui_warning))
                    }
                }

                item {
                    SectionCard(ephyra.i18n.R.string.label_library) {
                        Options(BackupOptions.libraryOptions, state, model)
                    }
                }

                item {
                    SectionCard(ephyra.i18n.R.string.label_settings) {
                        Options(BackupOptions.settingsOptions, state, model)
                    }
                }
            }
        }
    }

    @Composable
    private fun Options(
        options: ImmutableList<BackupOptions.Entry>,
        state: CreateBackupScreenModel.State,
        model: CreateBackupScreenModel,
    ) {
        options.forEach { option ->
            LabeledCheckbox(
                label = stringResource(option.label),
                checked = option.getter(state.options),
                onCheckedChange = {
                    model.toggle(option.setter, it)
                },
                enabled = option.enabled(state.options),
            )
        }
    }
}

private class CreateBackupScreenModel : StateScreenModel<CreateBackupScreenModel.State>(State()), KoinComponent {

    private val backupScheduler: BackupScheduler by inject()

    fun isBackupRunning(): Boolean = backupScheduler.isBackupRunning()

    fun getBackupFilename(): String = backupScheduler.getBackupFilename()

    fun toggle(setter: (BackupOptions, Boolean) -> BackupOptions, enabled: Boolean) {
        mutableState.update {
            it.copy(
                options = setter(it.options, enabled),
            )
        }
    }

    fun createBackup(uri: Uri) {
        backupScheduler.startBackupNow(uri.toString(), state.value.options.asBooleanArray())
    }

    @Immutable
    data class State(
        val options: BackupOptions = BackupOptions(),
    )
}
