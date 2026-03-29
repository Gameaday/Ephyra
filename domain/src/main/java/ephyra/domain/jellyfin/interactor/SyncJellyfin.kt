package ephyra.domain.jellyfin.interactor

import ephyra.domain.chapter.model.Chapter
import ephyra.domain.manga.model.Manga

class SyncJellyfin {

    suspend fun pushMetadataToJellyfinIfLinked(manga: Manga) {
        // TODO
    }

    suspend fun markJellyfinFavoriteIfLinked(manga: Manga, favorite: Boolean) {
        // TODO
    }

    suspend fun syncToJellyfin(
        manga: Manga,
        chapters: List<Chapter>,
        downloadStates: Map<Long, Boolean>,
        syncAction: SyncAction,
    ) {
        // TODO
    }

    enum class SyncAction {
        ALL,
        READ,
        UNREAD,
    }
}
