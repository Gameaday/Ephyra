package ephyra.feature.library

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ephyra.presentation.core.ui.AppInfo

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LibraryEntryPoint {
    fun appInfo(): AppInfo
}
