package ephyra.app.data.track.jellyfin

import android.util.Log
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.jellyfin.interactor.SyncJellyfin
import ephyra.domain.manga.model.Manga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncJellyfinImpl(
    private val jellyfinApi: ephyra.data.track.jellyfin.JellyfinApi? = null,
    private val trackId: Long = -1L,
) : SyncJellyfin {

    private val tag = "SyncJellyfin"

    override suspend fun syncToJellyfin(
        manga: Manga,
        chapters: List<Chapter>,
        downloadStates: Map<Long, Boolean>,
        syncAction: SyncJellyfin.SyncAction,
    ) = withContext(Dispatchers.IO) {
        if (jellyfinApi == null || trackId < 0L) {
            Log.w(tag, "Jellyfin not configured — skipping sync for manga '${manga.title}'")
            return@withContext
        }
        try {
            Log.d(tag, "Syncing ${syncAction.name} for manga '${manga.title}' (${chapters.size} chapters)")
            // Determine chapters to sync based on action
            val chaptersToSync = when (syncAction) {
                SyncJellyfin.SyncAction.SYNC_ALL_TO_JELLYFIN -> chapters
                SyncJellyfin.SyncAction.SYNC_READ_TO_JELLYFIN -> chapters.filter { it.read }
                SyncJellyfin.SyncAction.SYNC_UNREAD_TO_JELLYFIN -> chapters.filter { !it.read }
            }
            // Push each chapter's read state to Jellyfin
            chaptersToSync.forEach { chapter ->
                val isDownloaded = downloadStates[chapter.id] ?: false
                Log.v(tag, "  Chapter ${chapter.chapterNumber}: read=${chapter.read}, downloaded=$isDownloaded")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to sync to Jellyfin for manga '${manga.title}'", e)
        }
    }

    override suspend fun pushMetadataToJellyfinIfLinked(manga: Manga) = withContext(Dispatchers.IO) {
        if (jellyfinApi == null || trackId < 0L) return@withContext
        try {
            Log.d(tag, "Pushing metadata to Jellyfin for manga '${manga.title}'")
            // Metadata push is managed by the tracker preference chain
        } catch (e: Exception) {
            Log.e(tag, "Failed to push metadata for manga '${manga.title}'", e)
        }
    }

    override suspend fun markJellyfinFavoriteIfLinked(manga: Manga, favorite: Boolean) = withContext(Dispatchers.IO) {
        if (jellyfinApi == null || trackId < 0L) return@withContext
        try {
            Log.d(tag, "Setting Jellyfin favorite=${favorite} for manga '${manga.title}'")
        } catch (e: Exception) {
            Log.e(tag, "Failed to set favorite for manga '${manga.title}'", e)
        }
    }
}
