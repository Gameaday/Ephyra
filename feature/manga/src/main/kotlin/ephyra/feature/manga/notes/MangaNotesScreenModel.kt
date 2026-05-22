package ephyra.feature.manga.notes

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.util.lang.launchNonCancellable
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.interactor.UpdateMangaNotes
import ephyra.domain.manga.model.Manga
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MangaNotesScreenModel @Inject constructor(
    private val getManga: GetManga,
    private val updateMangaNotes: UpdateMangaNotes,
) : ViewModel() {

    private val _state = MutableStateFlow<MangaNotesState?>(null)
    val state: StateFlow<MangaNotesState?> = _state.asStateFlow()

    private var isInitialized = false

    fun init(mangaId: Long) {
        if (isInitialized) return
        isInitialized = true
        viewModelScope.launch {
            val manga = getManga.await(mangaId)
            if (manga != null) {
                _state.value = MangaNotesState(manga, manga.notes ?: "")
            }
        }
    }

    fun updateNotes(content: String) {
        val currentState = state.value ?: return
        if (content == currentState.notes) return

        _state.update {
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
