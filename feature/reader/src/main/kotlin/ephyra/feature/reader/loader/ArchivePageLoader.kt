package ephyra.feature.reader.loader

import ephyra.core.archive.ArchiveReader
import ephyra.core.common.util.lang.compareToCaseInsensitiveNaturalOrder
import ephyra.core.common.util.system.ImageUtil
import ephyra.feature.reader.model.ReaderPage
import eu.kanade.tachiyomi.source.model.Page

/**
 * Loader used to load a chapter from an archive file.
 *
 * This implementation is optimized to avoid the O(n²) archive rescanning issue
 * (B-055 finding v). The original implementation called [ArchiveReader.getInputStream]
 * for each entry during filtering, which performs a linear O(n) scan per call,
 * resulting in O(n²) total work for n entries.
 *
 * The fix: only use extension-based image detection via [ImageUtil.isImage] without
 * the stream fallback, which avoids opening streams during the filter phase.
 * Stream access is deferred to the actual page loading when needed.
 */
internal class ArchivePageLoader(private val reader: ArchiveReader) : PageLoader() {
    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        check(!isRecycled)
        return reader.useEntries { entries ->
            entries
                .filter { it.isFile && ImageUtil.isImage(it.name) }
                .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                .mapIndexed { i, entry ->
                    ReaderPage(i).apply {
                        resource.stream = {
                            requireNotNull(reader.getInputStream(entry.name)) {
                                "Entry '${entry.name}' not found in archive"
                            }
                        }
                        status = Page.State.Ready
                    }
                }
                .toList()
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
