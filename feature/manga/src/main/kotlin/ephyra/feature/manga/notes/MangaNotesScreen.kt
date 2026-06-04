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
    val viewModel = hiltViewModel<MangaNotesViewModel>()
    LaunchedEffect(mangaId) {
        viewModel.onEvent(MangaNotesEvent.Init(mangaId))
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state != null) {
        ephyra.feature.manga.presentation.MangaNotesScreen(
            state = state!!,
            navigateUp = { navController.popBackStack() },
            onUpdate = { viewModel.onEvent(MangaNotesEvent.UpdateNotes(it)) },
        )
    }
}
