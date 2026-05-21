package ephyra.presentation.core.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.source.service.SourceManager

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SourceUtilEntryPoint {
    fun sourceManager(): SourceManager
    fun extensionManager(): ExtensionManager
}

@Composable
fun ifSourcesLoaded(): Boolean {
    val context = LocalContext.current
    val sourceManager = androidx.compose.runtime.remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SourceUtilEntryPoint::class.java,
        ).sourceManager()
    }
    return sourceManager.isInitialized.collectAsStateWithLifecycle().value
}
