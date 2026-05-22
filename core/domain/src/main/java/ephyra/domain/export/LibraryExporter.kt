package ephyra.domain.export

import ephyra.domain.manga.model.Manga

/**
 * Domain interface for exporting library data.
 *
 * Lives in `domain` so that feature modules (`feature/settings`) can depend on it
 * without importing the `data` layer.  The concrete implementation
 * ([ephyra.data.export.LibraryExporterImpl]) lives in `:data` and requires an
 * Android [android.content.Context] at construction time.
 *
 * The export destination URI is accepted as a [String] to keep this interface free of
 * `android.*` imports and JVM-testable.
 */
interface LibraryExporter {

    /**
     * Exports [favorites] to a CSV file at [uriString] using the given [options].
     *
     * @param uriString  Destination file URI as a String.
     * @param favorites  List of favourite manga to export.
     * @param options    Column-selection options for the export.
     * @param onExportComplete  Callback invoked on the calling dispatcher when the export finishes.
     */
    suspend fun exportToCsv(
        uriString: String,
        favorites: List<Manga>,
        options: ExportOptions,
        onExportComplete: () -> Unit,
    )

    /**
     * Column-selection options for a CSV export.
     */
    data class ExportOptions(
        val includeTitle: Boolean,
        val includeAuthor: Boolean,
        val includeArtist: Boolean,
    )
}
