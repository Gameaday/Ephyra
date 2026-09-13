package ephyra.presentation.core.util

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.source.service.SourceManager

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SourceUtilEntryPoint {
    fun sourceManager(): SourceManager
    fun domainExtensionManager(): ExtensionManager
}
