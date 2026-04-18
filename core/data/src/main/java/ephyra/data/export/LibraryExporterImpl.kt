package ephyra.data.export

import android.content.Context
import androidx.core.net.toUri
import ephyra.domain.export.LibraryExporter
import ephyra.domain.manga.model.Manga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of [LibraryExporter].
 *
 * Lives in `:data` because it depends on Android [Context] (for ContentResolver) and
 * [android.net.Uri].  Feature modules receive [LibraryExporter] (the domain interface)
 * via constructor injection.
 */
class LibraryExporterImpl(private val context: Context) : LibraryExporter {

    override suspend fun exportToCsv(
        uriString: String,
        favorites: List<Manga>,
        options: LibraryExporter.ExportOptions,
        onExportComplete: () -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uriString.toUri())?.use { outputStream ->
                val csvData = generateCsvData(favorites, options)
                outputStream.write(csvData.toByteArray())
            }
            onExportComplete()
        }
    }

    private val escapeRequired = listOf("\r", "\n", "\"", ",")

    private fun generateCsvData(favorites: List<Manga>, options: LibraryExporter.ExportOptions): String {
        val columnSize = listOf(
            options.includeTitle,
            options.includeAuthor,
            options.includeArtist,
        )
            .count { it }

        val rows = buildList(favorites.size) {
            favorites.forEach { manga ->
                buildList(columnSize) {
                    if (options.includeTitle) add(manga.title)
                    if (options.includeAuthor) add(manga.author)
                    if (options.includeArtist) add(manga.artist)
                }
                    .let(::add)
            }
        }
        return rows.joinToString("\r\n") { columns ->
            columns.joinToString(",") columns@{ column ->
                if (column.isNullOrBlank()) return@columns ""
                if (escapeRequired.any { column.contains(it) }) {
                    column.replace("\"", "\"\"").let { "\"$it\"" }
                } else {
                    column
                }
            }
        }
    }
}
