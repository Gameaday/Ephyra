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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import ephyra.presentation.core.util.system.toast
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.update

import androidx.navigation.NavController
import ephyra.presentation.core.ui.navigation.LocalNavController

@Composable
fun CreateBackupScreen(
    navController: NavController = LocalNavController.current,
) {
    val context = LocalContext.current
    val model = hiltViewModel<CreateBackupViewModel>()
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
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            AppBar(
                title = stringResource(ephyra.app.core.common.R.string.pref_create_backup),
                navigateUp = { navController.popBackStack() },
                scrollBehavior = it,
            )
        },
    ) { contentPadding ->
        LazyColumnWithAction(
            contentPadding = contentPadding,
            actionLabel = stringResource(ephyra.app.core.common.R.string.action_create),
            actionEnabled = state.options.canCreate(),
            onClickAction = {
                if (!model.isBackupRunning()) {
                    try {
                        chooseBackupDir.launch(model.getBackupFilename())
                    } catch (e: ActivityNotFoundException) {
                        context.toast(ephyra.app.core.common.R.string.file_picker_error)
                    }
                } else {
                    context.toast(ephyra.app.core.common.R.string.backup_in_progress)
                }
            },
        ) {
            if (DeviceUtil.isMiui && DeviceUtil.isMiuiOptimizationDisabled()) {
                item {
                    WarningBanner(stringResource(ephyra.app.core.common.R.string.restore_miui_warning))
                }
            }

            item {
                SectionCard(ephyra.app.core.common.R.string.label_library) {
                    Options(BackupOptions.libraryOptions, state, model)
                }
            }

            item {
                SectionCard(ephyra.app.core.common.R.string.label_settings) {
                    Options(BackupOptions.settingsOptions, state, model)
                }
            }
        }
    }
}

@Composable
private fun Options(
        options: ImmutableList<BackupOptions.Entry>,
        state: CreateBackupViewModel.State,
        model: CreateBackupViewModel,
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

@HiltViewModel
class CreateBackupViewModel @Inject constructor(
    private val backupScheduler: BackupScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun isBackupRunning(): Boolean = backupScheduler.isBackupRunning()

    fun getBackupFilename(): String = backupScheduler.getBackupFilename()

    fun toggle(setter: (BackupOptions, Boolean) -> BackupOptions, enabled: Boolean) {
        _state.update {
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
