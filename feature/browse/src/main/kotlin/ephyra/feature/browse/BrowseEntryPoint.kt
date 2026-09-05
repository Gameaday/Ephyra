package ephyra.feature.browse

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ephyra.presentation.core.ui.AppInfo

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BrowseEntryPoint {
    fun appInfo(): AppInfo
}
