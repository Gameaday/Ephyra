package ephyra.feature.more

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ephyra.domain.base.BasePreferences
import ephyra.feature.settings.SettingsScreen
import ephyra.feature.settings.screen.SearchableSettings
import ephyra.feature.settings.screen.SettingsDataScreen
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.ui.AppReadySignal
import ephyra.presentation.core.util.Screen
import ephyra.presentation.core.util.collectAsState
import org.koin.compose.koinInject
import ephyra.feature.more.onboarding.OnboardingScreen as OnboardingContent

class OnboardingScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val basePreferences = koinInject<BasePreferences>()
        val shownOnboardingFlow by basePreferences.shownOnboardingFlow().collectAsState()

        // Dismiss the splash screen promptly when onboarding is shown.  Without this,
        // the splash would linger until SPLASH_MAX_DURATION because tabs (LibraryTab,
        // etc.) are not visible while onboarding is on screen.
        LaunchedEffect(Unit) {
            (context as? AppReadySignal)?.signalReady()
        }

        val finishOnboarding: () -> Unit = {
            basePreferences.shownOnboardingFlow().set(true)
            navigator.pop()
        }

        val restoreSettingKey = stringResource(SettingsDataScreen.restorePreferenceKeyString)

        BackHandler(enabled = !shownOnboardingFlow) {
            // Prevent exiting if onboarding hasn't been completed
        }

        OnboardingContent(
            onComplete = finishOnboarding,
            onRestoreBackup = {
                finishOnboarding()
                SearchableSettings.highlightKey = restoreSettingKey
                navigator.push(SettingsScreen(SettingsScreen.Destination.DataAndStorage))
            },
        )
    }
}
