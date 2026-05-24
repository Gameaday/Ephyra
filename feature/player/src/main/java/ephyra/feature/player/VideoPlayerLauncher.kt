package ephyra.feature.player

import androidx.navigation.NavController
import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.model.ContentUnit
import ephyra.presentation.core.ui.viewer.MediaViewerLauncher
import javax.inject.Inject

/**
 * Handles launching the [VideoPlayerScreen] for [ContentType.ANIME] content items.
 */
class VideoPlayerLauncher @Inject constructor() : MediaViewerLauncher {

    override fun canHandle(type: ContentType): Boolean {
        return type == ContentType.ANIME
    }

    override fun launch(navController: NavController, item: ContentItem, unit: ContentUnit) {
        val encodedUrl = java.net.URLEncoder.encode(unit.url, "UTF-8")
        val encodedTitle = java.net.URLEncoder.encode(item.title, "UTF-8")
        navController.navigate("player/$encodedTitle/$encodedUrl")
    }
}
