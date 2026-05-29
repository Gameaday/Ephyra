package ephyra.feature.stats

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.presentation.core.components.AppBar
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.core.ui.navigation.LocalNavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    navController: NavController = LocalNavController.current,
) {
    val screenModel = hiltViewModel<StatsScreenModel>()
    val state by screenModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(ephyra.app.core.common.R.string.label_stats),
                navigateUp = { navController.popBackStack() },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        if (state is StatsScreenState.Loading) {
            LoadingScreen()
            return@Scaffold
        }

        StatsScreenContent(
            state = state as StatsScreenState.Success,
            paddingValues = paddingValues,
        )
    }
}
