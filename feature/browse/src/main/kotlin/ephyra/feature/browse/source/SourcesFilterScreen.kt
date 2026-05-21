package ephyra.feature.browse.source

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import ephyra.feature.browse.presentation.SourcesFilterScreen
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.core.util.system.toast

import androidx.navigation.NavController
import ephyra.presentation.core.ui.navigation.LocalNavController

@Composable
fun SourcesFilterScreen(
    navController: NavController = LocalNavController.current,
) {
    val screenModel = hiltViewModel<SourcesFilterScreenModel>()
    val state by screenModel.state.collectAsStateWithLifecycle()

    if (state is SourcesFilterScreenModel.State.Loading) {
        LoadingScreen()
        return
    }

    if (state is SourcesFilterScreenModel.State.Error) {
        val context = LocalContext.current
        LaunchedEffect(Unit) {
            context.toast(ephyra.app.core.common.R.string.internal_error)
            navController.popBackStack()
        }
        return
    }

    val successState = state as SourcesFilterScreenModel.State.Success

    SourcesFilterScreen(
        navigateUp = { navController.popBackStack() },
        state = successState,
        onClickLanguage = { screenModel.onEvent(SourcesFilterScreenEvent.ToggleLanguage(it)) },
        onClickSource = { screenModel.onEvent(SourcesFilterScreenEvent.ToggleSource(it)) },
    )
}
