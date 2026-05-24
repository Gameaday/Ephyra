package ephyra.presentation.core.ui.viewer

import androidx.navigation.NavController
import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.model.ContentUnit

/**
 * Interface for launching a specific media viewer/reader/player UI.
 * Concrete implementations are registered via Hilt multibindings.
 */
interface MediaViewerLauncher {
    /**
     * Returns true if this launcher handles the given [ContentType].
     */
    fun canHandle(type: ContentType): Boolean

    /**
     * Performs navigation to launch the viewer for the specified [ContentItem] and [ContentUnit].
     */
    fun launch(navController: NavController, item: ContentItem, unit: ContentUnit)
}
