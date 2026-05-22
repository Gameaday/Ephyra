package ephyra.app.data.track.jellyfin

import ephyra.domain.chapter.model.Chapter
import ephyra.domain.jellyfin.interactor.SyncJellyfin
import ephyra.domain.manga.model.Manga

class SyncJellyfinImpl : SyncJellyfin {
    override suspend fun syncToJellyfin(
        manga: Manga,
        chapters: List<Chapter>,
        downloadStates: Map<Long, Boolean>,
        syncAction: SyncJellyfin.SyncAction,
    ) {
        // No-op stub implementation
    }

    override suspend fun pushMetadataToJellyfinIfLinked(manga: Manga) {
        // No-op stub implementation
    }

    override suspend fun markJellyfinFavoriteIfLinked(manga: Manga, favorite: Boolean) {
        // No-op stub implementation
    }
}
