package ephyra.feature.more

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import ephyra.domain.base.BasePreferences
import ephyra.feature.settings.screen.SettingsDataScreen
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.ui.AppReadySignal
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import ephyra.presentation.core.util.collectAsState
import ephyra.feature.more.onboarding.OnboardingScreen as OnboardingContent

@Composable
fun OnboardingScreen(
    navController: NavController = LocalNavController.current,
) {
    val context = LocalContext.current

    val basePreferences = remember { ephyra.core.common.di.CoreContainer.get<BasePreferences>() }
    val shownOnboardingFlow by basePreferences.shownOnboardingFlow().collectAsState()

    // Dismiss the splash screen promptly when onboarding is shown.  Without this,
    // the splash would linger until SPLASH_MAX_DURATION because tabs (LibraryTab,
    // etc.) are not visible while onboarding is on screen.
    LaunchedEffect(Unit) {
        (context as? AppReadySignal)?.signalReady()
    }

    val finishOnboarding: () -> Unit = {
        basePreferences.shownOnboardingFlow().set(true)
        navController.popBackStack()
    }

    val restoreSettingKey = stringResource(SettingsDataScreen.restorePreferenceKeyString)

    BackHandler(enabled = !shownOnboardingFlow) {
        // Prevent exiting if onboarding hasn't been completed
    }

    OnboardingContent(
        onComplete = finishOnboarding,
        onRestoreBackup = {
            finishOnboarding()
            // TODO: handle restoreSettingKey highlighting
            navController.navigate(ScreenRoutes.RestoreBackup.route)
        },
    )
}
