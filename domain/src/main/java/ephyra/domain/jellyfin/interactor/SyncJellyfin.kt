package ephyra.domain.jellyfin.interactor

import ephyra.domain.manga.model.Manga
import ephyra.domain.chapter.model.Chapter

interface SyncJellyfin {

    enum class SyncAction {
        SYNC_ALL_TO_JELLYFIN,
        SYNC_READ_TO_JELLYFIN,
        SYNC_UNREAD_TO_JELLYFIN
    }

    suspend fun syncToJellyfin(
        manga: Manga,
        chapters: List<Chapter>,
        downloadStates: Map<Long, Boolean>,
        syncAction: SyncAction
    )

    suspend fun pushMetadataToJellyfinIfLinked(manga: Manga)

    suspend fun markJellyfinFavoriteIfLinked(manga: Manga, favorite: Boolean)
}
