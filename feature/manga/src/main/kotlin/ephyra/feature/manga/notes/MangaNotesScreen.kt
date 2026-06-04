package ephyra.feature.manga.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.presentation.core.ui.navigation.LocalNavController

@Composable
fun MangaNotesScreen(
    mangaId: Long,
    navController: NavController = LocalNavController.current,
) {
    val screenModel = hiltViewModel<MangaNotesScreenModel>()
    LaunchedEffect(mangaId) {
        screenModel.onEvent(MangaNotesEvent.Init(mangaId))
    }
    val state by screenModel.state.collectAsStateWithLifecycle()

    if (state != null) {
        ephyra.feature.manga.presentation.MangaNotesScreen(
            state = state!!,
            navigateUp = { navController.popBackStack() },
            onUpdate = { screenModel.onEvent(MangaNotesEvent.UpdateNotes(it)) },
        )
    }
}
