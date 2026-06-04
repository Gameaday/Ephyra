package ephyra.feature.upcoming

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.Screen
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import kotlinx.coroutines.flow.collectLatest

@Composable
fun UpcomingScreen(
    ViewModel: UpcomingViewModel,
    navController: NavController = LocalNavController.current,
) {
    val state by ViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        ViewModel.effects.collectLatest { effect ->
            when (effect) {
                is UpcomingScreenEffect.NavigateToMangaDetails -> {
                    navController.navigate(Screen.MangaDetails(effect.mangaId, false))
                }
            }
        }
    }

    UpcomingScreenContent(
        state = state,
        setSelectedYearMonth = { ViewModel.onEvent(UpcomingScreenEvent.SetSelectedYearMonth(it)) },
        onClickUpcoming = { ViewModel.onEvent(UpcomingScreenEvent.ClickUpcoming(it.id)) },
    )
}
