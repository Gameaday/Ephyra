package ephyra.feature.reader

import androidx.navigation.NavController
import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.model.ContentUnit
import ephyra.presentation.core.ui.navigation.Screen
import ephyra.presentation.core.ui.viewer.MediaViewerLauncher
import javax.inject.Inject

/**
 * Handles launching the text-based [BookReaderScreen] for Novels and Books.
 */
class BookViewerLauncher @Inject constructor() : MediaViewerLauncher {

    override fun canHandle(type: ContentType): Boolean {
        return type == ContentType.NOVEL || type == ContentType.BOOK
    }

    override fun launch(navController: NavController, item: ContentItem, unit: ContentUnit) {
        val title = unit.title.ifBlank { item.title }
        navController.navigate(
            Screen.BookReader(
                title = title,
                bookUrl = unit.url,
            ),
        )
    }
}
