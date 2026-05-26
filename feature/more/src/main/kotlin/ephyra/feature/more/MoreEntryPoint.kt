package ephyra.feature.more

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ephyra.domain.base.BasePreferences
import ephyra.domain.release.service.AppUpdateDownloader
import ephyra.domain.storage.service.StoragePreferences
import ephyra.presentation.core.ui.AppInfo

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MoreEntryPoint {
    fun basePreferences(): BasePreferences
    fun appInfo(): AppInfo
    fun storagePreferences(): StoragePreferences
    fun appUpdateDownloader(): AppUpdateDownloader
}
