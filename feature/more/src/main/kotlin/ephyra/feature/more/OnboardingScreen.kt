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
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import ephyra.feature.more.onboarding.OnboardingViewModel
import ephyra.feature.settings.screen.SettingsDataScreen
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.ui.AppReadySignal
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import ephyra.presentation.core.util.collectAsState
import ephyra.presentation.core.util.system.toast
import ephyra.feature.more.onboarding.OnboardingScreen as OnboardingContent

@Composable
fun OnboardingScreen(
    navController: NavController = LocalNavController.current,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    val shownOnboardingFlow by viewModel.shownOnboardingFlow.collectAsState()

    // Dismiss the splash screen promptly when onboarding is shown.  Without this,
    // the splash would linger until SPLASH_MAX_DURATION because tabs (LibraryTab,
    // etc.) are not visible while onboarding is on screen.
    LaunchedEffect(Unit) {
        (context as? AppReadySignal)?.signalReady()
    }

    val finishOnboarding: () -> Unit = {
        viewModel.finishOnboarding()
        // popBackStack can fail when onboarding is the only destination on the
        // stack (fresh-install process-death restore) — land on Home instead of
        // stranding the user on a finished onboarding screen.
        if (!navController.popBackStack()) {
            navController.navigate(ScreenRoutes.Home.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val chooseBackup = rememberLauncherForActivityResult(
        object : ActivityResultContracts.GetContent() {
            override fun createIntent(context: Context, input: String): Intent {
                val intent = super.createIntent(context, input)
                return Intent.createChooser(
                    intent,
                    context.getString(ephyra.app.core.common.R.string.file_select_backup),
                )
            }
        },
    ) { uri ->
        if (uri == null) {
            context.toast(ephyra.app.core.common.R.string.file_null_uri_error)
            return@rememberLauncherForActivityResult
        }
        finishOnboarding()
        navController.navigate(ScreenRoutes.RestoreBackup.createRoute(uri.toString()))
    }

    BackHandler(enabled = !shownOnboardingFlow) {
        // Prevent exiting if onboarding hasn't been completed
    }

    OnboardingContent(
        storageDirPref = viewModel.storageDirPref,
        telemetryIncluded = viewModel.telemetryIncluded,
        onComplete = finishOnboarding,
        onRestoreBackup = {
            chooseBackup.launch("*/*")
        },
    )
}
