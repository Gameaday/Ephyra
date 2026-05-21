package ephyra.feature.manga.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import ephyra.presentation.core.ui.navigation.LocalNavController

@Composable
fun MangaNotesScreen(
    mangaId: Long,
    navController: NavController = LocalNavController.current,
) {
    val screenModel = hiltViewModel<MangaNotesScreenModel>()
    LaunchedEffect(mangaId) {
        screenModel.init(mangaId)
    }
    val state by screenModel.state.collectAsStateWithLifecycle()

    if (state != null) {
        ephyra.presentation.manga.MangaNotesScreen(
            state = state!!,
            navigateUp = { navController.popBackStack() },
            onUpdate = screenModel::updateNotes,
        )
    }
}
