package ephyra.feature.reader.loader

import ephyra.core.archive.EpubReader
import ephyra.feature.reader.model.PageState
import ephyra.feature.reader.model.ReaderPage

/**
 * Loader used to load a chapter from a .epub file.
 */
internal class EpubPageLoader(private val reader: EpubReader) : PageLoader() {

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        check(!isRecycled)
        return reader.getImagesFromPages().mapIndexed { i, path ->
            ReaderPage(i).apply {
                resource.stream = { requireNotNull(reader.getInputStream(path)) { "Entry '$path' not found in EPUB" } }
                status = PageState.Ready
            }
        }
    }

    override suspend fun loadPage(page: ReaderPage) {
        check(!isRecycled)
    }

    override fun recycle() {
        super.recycle()
        reader.close()
    }
}
