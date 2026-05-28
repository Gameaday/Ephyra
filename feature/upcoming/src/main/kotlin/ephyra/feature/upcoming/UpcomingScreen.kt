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
    screenModel: UpcomingScreenModel,
    navController: NavController = LocalNavController.current,
) {
    val state by screenModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        screenModel.effects.collectLatest { effect ->
            when (effect) {
                is UpcomingScreenEffect.NavigateToMangaDetails -> {
                    navController.navigate(Screen.MangaDetails(effect.mangaId, false))
                }
            }
        }
    }

    UpcomingScreenContent(
        state = state,
        setSelectedYearMonth = { screenModel.onEvent(UpcomingScreenEvent.SetSelectedYearMonth(it)) },
        onClickUpcoming = { screenModel.onEvent(UpcomingScreenEvent.ClickUpcoming(it.id)) },
    )
}
