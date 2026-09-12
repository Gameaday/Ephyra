package ephyra.feature.reader

import android.os.ParcelFileDescriptor
import androidx.navigation.NavController
import ephyra.core.archive.ArchiveReader
import ephyra.core.archive.EpubReader
import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.model.ContentUnit
import ephyra.presentation.core.ui.navigation.Screen
import ephyra.presentation.core.ui.viewer.MediaViewerLauncher
import java.io.File
import javax.inject.Inject

/**
 * Handles launching the text-based [BookReaderScreen] for Novels and Books.
 */
class BookViewerLauncher @Inject constructor() : MediaViewerLauncher {

    override fun canHandle(type: ContentType): Boolean {
        return type == ContentType.NOVEL || type == ContentType.BOOK
    }

    override fun launch(navController: NavController, item: ContentItem, unit: ContentUnit) {
        val file = File(unit.url)
        val content = if (file.exists() && file.isFile && file.extension.equals("epub", ignoreCase = true)) {
            runCatching {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                ArchiveReader(pfd).use { archive ->
                    EpubReader(archive).getTextPages().joinToString("\n\n")
                }
            }.getOrNull()?.takeIf { it.isNotBlank() }
        } else {
            null
        }

        val displayText = content ?: "${unit.title.ifBlank { item.title }}\n\n" +
            "No text content could be extracted from '${unit.url}'. " +
            "Ensure the local EPUB or novel file is accessible and contains valid text chapters."
        navController.navigate(Screen.BookReader(item.title, displayText))
    }
}
