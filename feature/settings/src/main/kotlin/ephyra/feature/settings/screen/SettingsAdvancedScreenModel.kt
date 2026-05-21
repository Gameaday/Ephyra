package ephyra.feature.settings.screen

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.download.DownloadCache
import ephyra.domain.base.BasePreferences
import ephyra.domain.extension.interactor.TrustExtension
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.library.service.MetadataUpdateScheduler
import ephyra.domain.manga.interactor.ResetViewerFlags
import ephyra.presentation.core.ui.AppInfo
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import javax.inject.Inject

@HiltViewModel
class SettingsAdvancedScreenModel @Inject constructor(
    val basePreferences: BasePreferences,
    val networkPreferences: NetworkPreferences,
    val libraryPreferences: LibraryPreferences,
    val downloadCache: DownloadCache,
    val networkHelper: NetworkHelper,
    val resetViewerFlags: ResetViewerFlags,
    val trustExtension: TrustExtension,
    val extensionManager: ExtensionManager,
    val appInfo: AppInfo,
    val metadataUpdateScheduler: MetadataUpdateScheduler,
) : ViewModel()
