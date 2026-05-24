package ephyra.presentation.core.ui.viewer

import androidx.navigation.NavController
import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.model.ContentUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry coordinates the dispatching of various media viewers.
 * It resolves the matching [MediaViewerLauncher] for a given [ContentType] at runtime.
 */
@Singleton
class MediaViewerRegistry @Inject constructor(
    private val launchers: Set<@JvmSuppressWildcards MediaViewerLauncher>,
) {
    /**
     * Finds a launcher capable of handling the specified [ContentType].
     */
    fun getLauncherFor(type: ContentType): MediaViewerLauncher? {
        return launchers.find { it.canHandle(type) }
    }

    /**
     * Resolves the appropriate viewer and launches it.
     * Returns true if a viewer was successfully resolved and launched, false otherwise.
     */
    fun launch(navController: NavController, item: ContentItem, unit: ContentUnit): Boolean {
        val launcher = getLauncherFor(item.contentType) ?: return false
        launcher.launch(navController, item, unit)
        return true
    }
}
