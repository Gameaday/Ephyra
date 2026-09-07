package ephyra.app.ui.deeplink

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.util.lang.launchIO
import ephyra.domain.chapter.interactor.GetChapterByUrlAndMangaId
import ephyra.domain.chapter.interactor.SyncChaptersWithSource
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.manga.interactor.NetworkToLocalManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.toDomainManga
import ephyra.domain.manga.model.toSManga
import ephyra.domain.source.service.SourceManager
import ephyra.presentation.core.udf.BaseUdfViewModel
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.online.ResolvableSource
import eu.kanade.tachiyomi.source.online.UriType
import javax.inject.Inject

@HiltViewModel
class DeepLinkViewModel @Inject constructor(
    private val sourceManager: SourceManager,
    private val networkToLocalManga: NetworkToLocalManga,
    private val getChapterByUrlAndMangaId: GetChapterByUrlAndMangaId,
    private val syncChaptersWithSource: SyncChaptersWithSource,
) : BaseUdfViewModel<DeepLinkViewModel.State, DeepLinkViewModel.Event, DeepLinkViewModel.Effect>(State.Loading) {

    private var isInitialized = false

    override fun onEvent(event: Event) {
        when (event) {
            is Event.Init -> init(event.query)
        }
    }

    fun init(query: String) {
        if (isInitialized) return
        isInitialized = true

        viewModelScope.launchIO {
            val source = sourceManager.getCatalogueSources()
                .filterIsInstance<ResolvableSource>()
                .firstOrNull { it.getUriType(query) != UriType.Unknown }

            val manga = source?.getManga(query)?.let {
                networkToLocalManga(it.toDomainManga(source.id))
            }

            val chapter = if (source?.getUriType(query) == UriType.Chapter && manga != null) {
                source.getChapter(query)?.let { getChapterFromSChapter(it, manga, source) }
            } else {
                null
            }

            if (manga == null) {
                updateState { State.NoResults }
                emitEffect(Effect.NavigateToGlobalSearch(query))
            } else {
                if (chapter == null) {
                    updateState { State.Result(manga) }
                    emitEffect(Effect.NavigateToMangaDetails(manga.id))
                } else {
                    updateState { State.Result(manga, chapter.id) }
                    emitEffect(Effect.OpenReader(manga.id, chapter.id))
                }
            }
        }
    }

    private suspend fun getChapterFromSChapter(sChapter: SChapter, manga: Manga, source: Source): Chapter? {
        val localChapter = getChapterByUrlAndMangaId.await(sChapter.url, manga.id)

        return if (localChapter == null) {
            val sourceChapters = source.getChapterList(manga.toSManga())
            val newChapters = syncChaptersWithSource.await(sourceChapters, manga, source, false)
            newChapters.find { it.url == sChapter.url }
        } else {
            localChapter
        }
    }

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data object NoResults : State

        @Immutable
        data class Result(val manga: Manga, val chapterId: Long? = null) : State
    }

    sealed interface Event {
        data class Init(val query: String) : Event
    }

    sealed interface Effect {
        data class NavigateToGlobalSearch(val query: String) : Effect
        data class NavigateToMangaDetails(val mangaId: Long) : Effect
        data class OpenReader(val mangaId: Long, val chapterId: Long) : Effect
    }
}
