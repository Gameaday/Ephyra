package ephyra.core.archive

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.io.Closeable
import java.io.File
import java.io.InputStream

/**
 * Represents an individual chapter extracted from an EPUB document.
 */
data class EpubChapter(
    val id: String,
    val title: String,
    val bodyText: String,
)

/**
 * Wrapper over ArchiveReader to load files in epub format.
 */
class EpubReader(private val reader: ArchiveReader) : Closeable by reader {

    /**
     * Path separator used by this epub.
     */
    private val pathSeparator = getPathSeparator()

    /**
     * Returns an input stream for reading the contents of the specified zip file entry.
     */
    fun getInputStream(entryName: String): InputStream? {
        return reader.getInputStream(entryName)
    }

    /**
     * Returns the path of all the images found in the epub file.
     */
    fun getImagesFromPages(): List<String> {
        val ref = getPackageHref()
        val doc = getPackageDocument(ref)
        val pages = getPagesFromDocument(doc)
        return getImagesFromPages(pages, ref)
    }

    /**
     * Returns the path to the package document.
     */
    fun getPackageHref(): String {
        val meta = getInputStream(resolveZipPath("META-INF", "container.xml"))
        if (meta != null) {
            val metaDoc = meta.use { Jsoup.parse(it, null, "", Parser.xmlParser()) }
            val path = metaDoc.getElementsByTag("rootfile").first()?.attr("full-path")
            if (path != null) {
                return path
            }
        }
        return resolveZipPath("OEBPS", "content.opf")
    }

    /**
     * Returns the package document where all the files are listed.
     */
    fun getPackageDocument(ref: String): Document {
        return getInputStream(ref)!!.use { Jsoup.parse(it, null, "", Parser.xmlParser()) }
    }

    /**
     * Returns all the pages from the epub.
     */
    private fun getPagesFromDocument(document: Document): List<String> {
        val pages = document.select("manifest > item")
            .filter { node -> "application/xhtml+xml" == node.attr("media-type") }
            .associateBy { it.attr("id") }

        val spine = document.select("spine > itemref").map { it.attr("idref") }
        return spine.mapNotNull { pages[it] }.map { it.attr("href") }
    }

    /**
     * Returns structured chapters with titles and text content from the epub spine.
     */
    fun getChapters(): List<EpubChapter> {
        val ref = getPackageHref()
        val doc = getPackageDocument(ref)
        val pages = getPagesFromDocument(doc)
        val basePath = getParentDirectory(ref)
        var chapterIndex = 1
        return pages.mapNotNull { page ->
            val entryPath = resolveZipPath(basePath, page)
            getInputStream(entryPath)?.use { stream ->
                val document = Jsoup.parse(stream, null, "")
                val text = document.body().text()
                if (text.isNotBlank()) {
                    val title = document.selectFirst("h1, h2, h3, title")?.text()
                        ?.takeIf { it.isNotBlank() }
                        ?: "Chapter $chapterIndex"
                    chapterIndex++
                    EpubChapter(
                        id = page,
                        title = title,
                        bodyText = text,
                    )
                } else {
                    null
                }
            }
        }
    }

    /**
     * Returns the text content for each chapter/page listed in the epub spine.
     */
    fun getTextPages(): List<String> = getChapters().map { it.bodyText }

    /**
     * Returns all the images contained in every page from the epub.
     */
    private fun getImagesFromPages(pages: List<String>, packageHref: String): List<String> {
        val result = mutableListOf<String>()
        val basePath = getParentDirectory(packageHref)
        pages.forEach { page ->
            val entryPath = resolveZipPath(basePath, page)
            val stream = getInputStream(entryPath) ?: return@forEach
            val document = stream.use { Jsoup.parse(it, null, "") }
            val imageBasePath = getParentDirectory(entryPath)

            document.allElements.forEach {
                when (it.tagName()) {
                    "img" -> result.add(resolveZipPath(imageBasePath, it.attr("src")))
                    "image" -> result.add(resolveZipPath(imageBasePath, it.attr("xlink:href")))
                }
            }
        }

        return result
    }

    /**
     * Returns the path separator used by the epub file.
     */
    private fun getPathSeparator(): String {
        val meta = getInputStream("META-INF\\container.xml")
        return if (meta != null) {
            meta.close()
            "\\"
        } else {
            "/"
        }
    }

    /**
     * Resolves an internal zip entry path from a base directory and relative path.
     */
    private fun resolveZipPath(basePath: String, relativePath: String): String {
        val sep = pathSeparator
        val normalizedRelative = relativePath.replace('\\', '/')
        if (normalizedRelative.startsWith('/')) {
            return normalizedRelative.trimStart('/')
        }
        if (basePath.isBlank()) {
            return normalizedRelative.trimStart('/')
        }

        val normalizedBase = basePath.replace('\\', '/').trim('/')
        val combined = "$normalizedBase/$normalizedRelative"
        val parts = combined.split('/').filter { it.isNotEmpty() && it != "." }
        val resolved = mutableListOf<String>()
        for (part in parts) {
            if (part == "..") {
                if (resolved.isNotEmpty()) resolved.removeAt(resolved.size - 1)
            } else {
                resolved.add(part)
            }
        }
        return resolved.joinToString(sep)
    }

    /**
     * Gets the parent directory of a path.
     */
    private fun getParentDirectory(path: String): String {
        val normalized = path.replace('\\', '/')
        val separatorIndex = normalized.lastIndexOf('/')
        return if (separatorIndex >= 0) {
            normalized.substring(0, separatorIndex).replace('/', pathSeparator[0])
        } else {
            ""
        }
    }
}
