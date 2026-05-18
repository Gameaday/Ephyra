package ephyra.feature.manga.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ephyra.domain.manga.model.Manga
import ephyra.presentation.manga.MangaNotesScreen
import ephyra.presentation.util.Screen

class MangaNotesScreen(
    private val manga: Manga,
) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = hiltViewModel<MangaNotesScreenModel>()
        LaunchedEffect(manga) {
            screenModel.init(manga)
        }
        val state by screenModel.state.collectAsStateWithLifecycle()

        if (state != null) {
            MangaNotesScreen(
                state = state!!,
                navigateUp = navigator::pop,
                onUpdate = screenModel::updateNotes,
            )
        }
    }
}
