package ephyra.feature.manga.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ephyra.core.common.di.CoreContainer
import ephyra.domain.manga.model.Manga
import ephyra.presentation.manga.MangaNotesScreen
import ephyra.presentation.util.Screen

class MangaNotesScreen(
    private val manga: Manga,
) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel {
            MangaNotesScreenModel(
                manga = manga,
                updateMangaNotes = CoreContainer.get(),
            )
        }
        val state by screenModel.state.collectAsStateWithLifecycle()

        MangaNotesScreen(
            state = state,
            navigateUp = navigator::pop,
            onUpdate = screenModel::updateNotes,
        )
    }
}
