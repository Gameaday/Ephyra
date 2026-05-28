package ephyra.feature.player

import androidx.navigation.NavController
import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.model.ContentUnit
import ephyra.presentation.core.ui.navigation.Screen
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
        navController.navigate(Screen.VideoPlayer(item.title, unit.url))
    }
}
