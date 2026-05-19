package ephyra.feature.settings.screen

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.download.service.DownloadPreferences
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.track.service.TrackPreferences
import ephyra.domain.track.service.TrackerManager
import javax.inject.Inject

@HiltViewModel
class SettingsDownloadScreenModel @Inject constructor(
    val getCategories: GetCategories,
    val downloadPreferences: DownloadPreferences,
    val trackerManager: TrackerManager,
    val trackPreferences: TrackPreferences,
    val libraryPreferences: LibraryPreferences,
) : ViewModel()
