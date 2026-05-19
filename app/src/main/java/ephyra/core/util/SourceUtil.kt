package ephyra.core.util

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ephyra.domain.source.service.SourceManager

@Composable
fun ifSourcesLoaded(): Boolean {
    val sourceManager = androidx.compose.runtime.remember { ephyra.core.common.di.CoreContainer.get<SourceManager>() }
    return sourceManager.isInitialized.collectAsStateWithLifecycle().value
}
