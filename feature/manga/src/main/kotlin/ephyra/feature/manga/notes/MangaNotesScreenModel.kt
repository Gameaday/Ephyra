package ephyra.feature.manga.notes

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.util.lang.launchNonCancellable
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.interactor.UpdateMangaNotes
import ephyra.domain.manga.model.Manga
import ephyra.presentation.core.udf.BaseUdfViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MangaNotesEvent {
    data class Init(val mangaId: Long) : MangaNotesEvent
    data class UpdateNotes(val content: String) : MangaNotesEvent
}

@HiltViewModel
class MangaNotesScreenModel @Inject constructor(
    private val getManga: GetManga,
    private val updateMangaNotes: UpdateMangaNotes,
) : BaseUdfViewModel<MangaNotesState?, MangaNotesEvent, Unit>(null) {

    private var isInitialized = false

    private fun initModel(mangaId: Long) {
        if (isInitialized) return
        isInitialized = true
        viewModelScope.launch {
            val manga = getManga.await(mangaId)
            if (manga != null) {
                updateState { MangaNotesState(manga, manga.notes) }
            }
        }
    }

    override fun onEvent(event: MangaNotesEvent) {
        when (event) {
            is MangaNotesEvent.Init -> initModel(event.mangaId)
            is MangaNotesEvent.UpdateNotes -> updateNotes(event.content)
        }
    }

    private fun updateNotes(content: String) {
        val currentState = state.value ?: return
        if (content == currentState.notes) return

        updateState {
            it?.copy(notes = content)
        }

        viewModelScope.launchNonCancellable {
            updateMangaNotes(currentState.manga.id, content)
        }
    }
}

@Immutable
data class MangaNotesState(
    val manga: Manga,
    val notes: String,
)
