package ephyra.app.ui.deeplink

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.util.lang.launchIO
import ephyra.domain.chapter.interactor.SyncChaptersWithSource
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.chapter.repository.ChapterRepository
import ephyra.domain.manga.interactor.NetworkToLocalManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.toDomainManga
import ephyra.domain.manga.model.toSManga
import ephyra.domain.source.service.SourceManager
import ephyra.presentation.core.udf.BaseUdfViewModel
import ephyra.presentation.core.util.system.asHttpSource
import ephyra.presentation.core.util.system.asResolvableSource
import ephyra.source.api.ContentReference
import ephyra.source.api.SourceGateway
import ephyra.source.api.SourceResult
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.online.UriType
import javax.inject.Inject

@HiltViewModel
class DeepLinkViewModel @Inject constructor(
    private val sourceManager: SourceManager,
    private val networkToLocalManga: NetworkToLocalManga,
    private val chapterRepository: ChapterRepository,
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
            val gateway = sourceManager.getCatalogueSources()
                .firstOrNull { it.asResolvableSource()?.getUriType(query) != UriType.Unknown }

            val manga = gateway?.let { gw ->
                val result = gw.getDetails(ContentReference(url = query))
                if (result is SourceResult.Success) {
                    result.value.toDomainManga()
                } else {
                    null
                }
            }

            val chapter = if (gateway?.asResolvableSource()?.getUriType(query) == UriType.Chapter && manga != null) {
                val result = gateway?.getDetails(ContentReference(url = query))
                if (result is SourceResult.Success) {
                    getChapterFromSourceContentItem(result.value, manga, gateway!!)
                } else {
                    null
                }
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

    private suspend fun getChapterFromSChapter(sChapter: SChapter, manga: Manga, source: SourceGateway): Chapter? {
        val localChapter = runCatching {
            chapterRepository.getChapterByUrlAndMangaId(sChapter.url, manga.id)
        }.getOrNull()

        return if (localChapter == null) {
            val newChapters = syncChaptersWithSource.await(source, manga, false, Pair(0L, 0L))
            newChapters.find { it.url == sChapter.url }
        } else {
            localChapter
        }
    }

    private suspend fun getChapterFromSourceContentItem(
        contentItem: ephyra.source.api.SourceContentItem,
        manga: Manga,
        source: SourceGateway,
    ): Chapter? {
        // Convert SourceContentItem to SChapter-like object for URL matching
        val chapterUrl = contentItem.url
        val localChapter = runCatching {
            chapterRepository.getChapterByUrlAndMangaId(chapterUrl, manga.id)
        }.getOrNull()

        return if (localChapter == null) {
            val newChapters = syncChaptersWithSource.await(source, manga, false, Pair(0L, 0L))
            newChapters.find { it.url == chapterUrl }
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
