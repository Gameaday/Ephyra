package ephyra.feature.more

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.core.common.util.storage.BackupStaging
import ephyra.feature.more.onboarding.OnboardingEvent
import ephyra.feature.more.onboarding.OnboardingViewModel
import ephyra.feature.settings.screen.SettingsDataScreen
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.ui.AppReadySignal
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import ephyra.presentation.core.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ephyra.feature.more.onboarding.OnboardingScreen as OnboardingContent

@Composable
fun OnboardingScreen(
    navController: NavController = LocalNavController.current,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    val state by viewModel.state.collectAsStateWithLifecycle()

    // Dismiss the splash screen promptly when onboarding is shown.  Without this,
    // the splash would linger until SPLASH_MAX_DURATION because tabs (LibraryTab,
    // etc.) are not visible while onboarding is on screen.
    LaunchedEffect(Unit) {
        (context as? AppReadySignal)?.signalReady()
    }

    val finishOnboarding: () -> Unit = {
        viewModel.onEvent(OnboardingEvent.FinishOnboarding)
        // popBackStack can fail when onboarding is the only destination on the
        // stack (fresh-install process-death restore) — land on Home instead of
        // stranding the user on a finished onboarding screen.
        if (!navController.popBackStack()) {
            navController.navigate(ScreenRoutes.Home.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val scope = rememberCoroutineScope()
    val chooseBackup = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            context.toast(ephyra.app.core.common.R.string.file_null_uri_error)
            return@rememberLauncherForActivityResult
        }

        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: Exception) {
            android.util.Log.w("Onboarding", "Failed to take persistable URI permission for backup file", e)
        }

        scope.launch(Dispatchers.IO) {
            try {
                val stagedUri = BackupStaging.stageBackupFile(context, uri)
                withContext(Dispatchers.Main) {
                    finishOnboarding()
                    navController.navigate(ScreenRoutes.RestoreBackup.createRoute(stagedUri.toString()))
                }
            } catch (e: Exception) {
                android.util.Log.e("Onboarding", "Failed to stage backup file: $uri", e)
                withContext(Dispatchers.Main) {
                    context.toast(ephyra.app.core.common.R.string.invalid_backup_file_error)
                }
            }
        }
    }

    BackHandler(enabled = !state.shownOnboarding) {
        // Prevent exiting if onboarding hasn't been completed
    }

    OnboardingContent(
        storageDirPref = viewModel.storageDirPref,
        telemetryIncluded = state.telemetryIncluded,
        onComplete = finishOnboarding,
        onRestoreBackup = {
            chooseBackup.launch(arrayOf("*/*"))
        },
    )
}
