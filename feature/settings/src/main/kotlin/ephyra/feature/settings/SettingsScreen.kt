package ephyra.feature.settings

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import ephyra.feature.settings.screen.SettingsAppearanceScreen
import ephyra.feature.settings.screen.SettingsDataScreen
import ephyra.feature.settings.screen.SettingsMainScreen
import ephyra.feature.settings.screen.SettingsTrackingScreen
import ephyra.feature.settings.screen.about.AboutScreen
import ephyra.presentation.core.components.TwoPanelBox
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.util.LocalBackPress
import ephyra.presentation.core.util.isTabletUi

@Composable
fun SettingsScreen(
    destinationId: Int? = null,
    navController: NavController = LocalNavController.current,
) {
    if (!isTabletUi()) {
        when (destinationId) {
            SettingsScreen.Destination.About.id -> AboutScreen(navController)
            SettingsScreen.Destination.DataAndStorage.id -> SettingsDataScreen.Content()
            SettingsScreen.Destination.Tracking.id -> SettingsTrackingScreen.Content()
            else -> SettingsMainScreen(twoPane = false, navController = navController)
        }
    } else {
        var currentDetail by remember {
            mutableStateOf(
                when (destinationId) {
                    SettingsScreen.Destination.About.id -> SettingsDetail.About
                    SettingsScreen.Destination.DataAndStorage.id -> SettingsDetail.Data
                    SettingsScreen.Destination.Tracking.id -> SettingsDetail.Tracking
                    else -> SettingsDetail.Appearance
                },
            )
        }
        val insets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
        TwoPanelBox(
            modifier = Modifier
                .windowInsetsPadding(insets)
                .consumeWindowInsets(insets),
            startContent = {
                CompositionLocalProvider(LocalBackPress provides { navController.popBackStack() }) {
                    SettingsMainScreen(twoPane = true, navController = navController)
                }
            },
            endContent = {
                when (currentDetail) {
                    SettingsDetail.Appearance -> SettingsAppearanceScreen.Content()
                    SettingsDetail.About -> AboutScreen(navController)
                    SettingsDetail.Data -> SettingsDataScreen.Content()
                    SettingsDetail.Tracking -> SettingsTrackingScreen.Content()
                }
            },
        )
    }
}

enum class SettingsDetail {
    Appearance,
    About,
    Data,
    Tracking,
}

object SettingsScreen {
    sealed class Destination(val id: Int) {
        data object About : Destination(0)
        data object DataAndStorage : Destination(1)
        data object Tracking : Destination(2)
    }
}
