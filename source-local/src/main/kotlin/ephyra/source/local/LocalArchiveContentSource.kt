package ephyra.source.local

import android.content.Context
import ephyra.core.archive.archiveReader
import ephyra.core.archive.epubReader
import ephyra.core.common.storage.extension
import ephyra.core.common.util.lang.compareToCaseInsensitiveNaturalOrder
import ephyra.core.common.util.system.ImageUtil
import ephyra.domain.chapter.service.ChapterRecognition
import ephyra.domain.content.model.CatalogEntry
import ephyra.domain.content.model.ChapterInfo
import ephyra.domain.content.model.ContentPage
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.model.FilterSet
import ephyra.domain.content.source.UnifiedContentSource
import ephyra.source.local.io.Archive
import ephyra.source.local.io.Format
import ephyra.source.local.io.LocalSourceFileSystem

/**
 * Pillar 1: Local Storage (SAF Streaming Archive).
 *
 * Implements [UnifiedContentSource] by accessing storage via Storage Access Framework
 * and decoding CBZ/CBR/ZIP/EPUB archives in-memory without disk extraction.
 */
class LocalArchiveContentSource(
    private val context: Context,
    private val fileSystem: LocalSourceFileSystem,
) : UnifiedContentSource {

    override val id: String = "local-archive"
    override val name: String = "Local Archives (SAF)"
    override val supportedTypes: Set<ContentType> = setOf(ContentType.MANGA, ContentType.BOOK)

    override suspend fun getCatalog(page: Int, filter: FilterSet): List<CatalogEntry> {
        val baseFiles = fileSystem.getFilesInBaseDirectory()
        return baseFiles
            .filter { it.isDirectory && !it.name.orEmpty().startsWith('.') }
            .filter {
                if (filter.query.isBlank()) {
                    true
                } else {
                    it.name.orEmpty().contains(filter.query, ignoreCase = true)
                }
            }
            .sortedWith { a, b ->
                when (filter.sortOrder) {
                    FilterSet.SortOrder.ALPHABETICAL -> a.name.orEmpty().compareToCaseInsensitiveNaturalOrder(
                        b.name.orEmpty(),
                    )
                    FilterSet.SortOrder.LATEST -> b.lastModified().compareTo(a.lastModified())
                    FilterSet.SortOrder.POPULAR -> a.name.orEmpty().compareToCaseInsensitiveNaturalOrder(
                        b.name.orEmpty(),
                    )
                }
            }
            .map { dir ->
                CatalogEntry(
                    key = dir.name.orEmpty(),
                    title = dir.name.orEmpty(),
                    url = dir.name.orEmpty(),
                    type = ContentType.MANGA,
                )
            }
    }

    override suspend fun getChapterManifest(entryKey: String): List<ChapterInfo> {
        val mangaDir = fileSystem.getBaseDirectory()?.findFile(entryKey) ?: return emptyList()
        val files = mangaDir.listFiles().orEmpty()
        return files
            .filter { file ->
                file.isDirectory || Archive.isSupported(file) || file.extension.equals("epub", ignoreCase = true)
            }
            .sortedWith { a, b -> a.name.orEmpty().compareToCaseInsensitiveNaturalOrder(b.name.orEmpty()) }
            .map { file ->
                val fileName = file.name.orEmpty()
                val chapterNumber = ChapterRecognition.parseChapterNumber(entryKey, fileName)
                ChapterInfo(
                    key = "$entryKey/$fileName",
                    title = fileName,
                    number = chapterNumber,
                    dateUpload = file.lastModified(),
                    url = "$entryKey/$fileName",
                )
            }
    }

    override suspend fun loadPage(chapterKey: String, pageIndex: Int): ContentPage {
        val pages = loadPages(chapterKey)
        return pages.getOrNull(pageIndex) ?: ContentPage.ImagePage(index = pageIndex)
    }

    override suspend fun loadPages(chapterKey: String): List<ContentPage> {
        val parts = chapterKey.split('/', limit = 2)
        if (parts.size != 2) return emptyList()
        val (mangaDirName, chapterName) = parts
        val file = fileSystem.getBaseDirectory()
            ?.findFile(mangaDirName)
            ?.findFile(chapterName)
            ?: return emptyList()

        return when (val format = Format.valueOf(file)) {
            is Format.Directory -> {
                format.file.listFiles().orEmpty()
                    .filter { !it.isDirectory && ImageUtil.isImage(it.name) { it.openInputStream() } }
                    .sortedWith { a, b -> a.name.orEmpty().compareToCaseInsensitiveNaturalOrder(b.name.orEmpty()) }
                    .mapIndexed { index, childFile ->
                        val bytes = childFile.openInputStream().use { it.readBytes() }
                        ContentPage.ImagePage(
                            index = index,
                            imageUrl = childFile.uri?.toString() ?: childFile.name.orEmpty(),
                            imageBytes = bytes,
                        )
                    }
            }

            is Format.Archive -> {
                format.file.archiveReader(context).use { reader ->
                    val imageEntryNames = reader.useEntries { entries ->
                        entries
                            .filter { entry ->
                                entry.isFile && ImageUtil.isImage(entry.name) {
                                    requireNotNull(reader.getInputStream(entry.name)) {
                                        "Entry '${entry.name}' not found"
                                    }
                                }
                            }
                            .map { it.name }
                            .sortedWith { a, b -> a.compareToCaseInsensitiveNaturalOrder(b) }
                            .toList()
                    }

                    imageEntryNames.mapIndexed { index, name ->
                        val bytes = reader.getInputStream(name)?.use { it.readBytes() }
                        ContentPage.ImagePage(
                            index = index,
                            imageUrl = name,
                            imageBytes = bytes,
                        )
                    }
                }
            }

            is Format.Epub -> {
                format.file.epubReader(context).use { epub ->
                    val images = epub.getImagesFromPages()
                    images.mapIndexed { index, name ->
                        val bytes = epub.getInputStream(name)?.use { it.readBytes() }
                        ContentPage.ImagePage(
                            index = index,
                            imageUrl = name,
                            imageBytes = bytes,
                        )
                    }
                }
            }
        }
    }
}
