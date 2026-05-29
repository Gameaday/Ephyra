package ephyra.feature.browse.extension.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.feature.browse.presentation.ExtensionDetailsScreen
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ExtensionDetailsScreen(
    pkgName: String,
    navController: NavController = LocalNavController.current,
) {
    val screenModel = hiltViewModel<ExtensionDetailsScreenModel, ExtensionDetailsScreenModel.Factory> { factory ->
        factory.create(pkgName)
    }
    val state by screenModel.state.collectAsStateWithLifecycle()

    if (state.isLoading) {
        LoadingScreen()
        return
    }

    ExtensionDetailsScreen(
        navigateUp = { navController.popBackStack() },
        state = state,
        onClickSourcePreferences = { navController.navigate(ScreenRoutes.SourcePreferences.createRoute(it)) },
        onClickEnableAll = { screenModel.onEvent(ExtensionDetailsScreenEvent.ToggleSources(true)) },
        onClickDisableAll = { screenModel.onEvent(ExtensionDetailsScreenEvent.ToggleSources(false)) },
        onClickClearCookies = { screenModel.onEvent(ExtensionDetailsScreenEvent.ClearCookies) },
        onClickUninstall = { screenModel.onEvent(ExtensionDetailsScreenEvent.UninstallExtension) },
        onClickSource = { screenModel.onEvent(ExtensionDetailsScreenEvent.ToggleSource(it)) },
        onClickIncognito = { screenModel.onEvent(ExtensionDetailsScreenEvent.ToggleIncognito(it)) },
    )

    LaunchedEffect(Unit) {
        screenModel.events.collectLatest { event ->
            if (event is ExtensionDetailsEvent.Uninstalled) {
                navController.popBackStack()
            }
        }
    }
}
