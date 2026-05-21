package ephyra.feature.upcoming

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.ScreenRoutes

@Composable
fun UpcomingScreen(
    navController: NavController = LocalNavController.current,
) {
    val screenModel = hiltViewModel<UpcomingScreenModel>()
    val state by screenModel.state.collectAsStateWithLifecycle()

    UpcomingScreenContent(
        state = state,
        setSelectedYearMonth = { screenModel.onEvent(UpcomingScreenEvent.SetSelectedYearMonth(it)) },
        onClickUpcoming = { navController.navigate(ScreenRoutes.MangaDetails.createRoute(it.id, false)) },
    )
}
