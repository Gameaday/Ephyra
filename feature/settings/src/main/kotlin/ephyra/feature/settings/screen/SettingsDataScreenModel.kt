package ephyra.feature.settings.screen

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.domain.backup.service.BackupPreferences
import ephyra.domain.backup.service.BackupScheduler
import ephyra.domain.backup.service.RestoreScheduler
import ephyra.domain.chapter.service.ChapterCache
import ephyra.domain.export.LibraryExporter
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.interactor.GetFavorites
import ephyra.domain.storage.service.StoragePreferences
import javax.inject.Inject

@HiltViewModel
class SettingsDataScreenModel @Inject constructor(
    val backupPreferences: BackupPreferences,
    val backupScheduler: BackupScheduler,
    val restoreScheduler: RestoreScheduler,
    val storagePreferences: StoragePreferences,
    val libraryPreferences: LibraryPreferences,
    val chapterCache: ChapterCache,
    val getFavorites: GetFavorites,
    val libraryExporter: LibraryExporter,
) : ViewModel()
