package ephyra.domain.library.service

import android.net.Uri
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.library.model.LibraryManga
import ephyra.domain.manga.model.Manga

interface LibraryUpdateNotifier {
    suspend fun showProgressNotification(manga: List<Manga>, current: Int, total: Int)
    fun showQueueSizeWarningNotificationIfNeeded(mangaToUpdate: List<LibraryManga>)
    fun showUpdateErrorNotification(failed: Int, uri: Uri)
    suspend fun showUpdateNotifications(updates: List<Pair<Manga, Array<Chapter>>>)
    fun cancelProgressNotification()
    suspend fun showSourceHealthNotification(deadManga: List<Manga>, degradedManga: List<Manga>)
    suspend fun showMigrationSuggestionNotification(persistentlyDeadManga: List<Manga>)
}
