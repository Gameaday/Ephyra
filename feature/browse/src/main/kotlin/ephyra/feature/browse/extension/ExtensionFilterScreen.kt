package ephyra.feature.browse.extension

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.core.common.i18n.stringResource
import ephyra.feature.browse.presentation.ExtensionFilterScreen
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.core.ui.navigation.LocalNavController
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ExtensionFilterScreen(
    navController: NavController = LocalNavController.current,
) {
    val context = LocalContext.current
    val screenModel = hiltViewModel<ExtensionFilterScreenModel>()
    val state by screenModel.state.collectAsStateWithLifecycle()

    if (state is ExtensionFilterState.Loading) {
        LoadingScreen()
        return
    }

    val successState = state as ExtensionFilterState.Success

    ExtensionFilterScreen(
        navigateUp = { navController.popBackStack() },
        state = successState,
        onClickToggle = screenModel::toggle,
    )

    LaunchedEffect(Unit) {
        screenModel.events.collectLatest {
            when (it) {
                ExtensionFilterEvent.FailedFetchingLanguages -> {
                    context.stringResource(ephyra.app.core.common.R.string.internal_error)
                }
            }
        }
    }
}
