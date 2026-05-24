package ephyra.feature.reader

import androidx.navigation.NavController
import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.model.ContentUnit
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
        val encodedTitle = java.net.URLEncoder.encode(item.title, "UTF-8")
        val sampleContent = "Chapter 1: The Adventure Begins...\n\n" +
            "This is a premium, content-agnostic reflowable text reader " +
            "built using dynamic modern typography and interactive reading modes. " +
            "It is designed to give you a fluid, customizable reading experience " +
            "for all Books, Novels, and written media types.\n\n" +
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit. " +
            "Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. " +
            "Ut enim ad minim veniam, quis nostrud exercitation ullamco " +
            "laboris nisi ut aliquip ex ea commodo consequat. " +
            "Duis aute irure dolor in reprehenderit in voluptate velit " +
            "esse cillum dolore eu fugiat nulla pariatur."
        val encodedContent = java.net.URLEncoder.encode(sampleContent, "UTF-8")
        navController.navigate("book/$encodedTitle/$encodedContent")
    }
}
