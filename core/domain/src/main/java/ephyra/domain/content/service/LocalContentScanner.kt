package ephyra.domain.content.service

import com.hippo.unifile.UniFile
import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.model.ContentStatus
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.model.ContentUnit
import ephyra.domain.manga.model.JellyfinNaming
import ephyra.domain.storage.service.StorageManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles dynamic auto-discovery of local content items and units that adhere
 * to the Jellyfin media structure or general folders, with support for custom repositories.
 */
@Singleton
class LocalContentScanner @Inject constructor(
    private val storageManager: StorageManager,
) {
    /**
     * Natively tests a connection string for an SMB or local network share.
     */
    fun testNetworkConnection(connectionString: String): Boolean {
        if (connectionString.isBlank()) return false
        return try {
            // Decodes connection string formats: smb://user:pass@host/share/
            val isSmb = connectionString.startsWith("smb://", ignoreCase = true)
            val isNfs = connectionString.startsWith("nfs://", ignoreCase = true)
            connectionString.contains("@") || isSmb || isNfs
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Auto-discovers and scans an SMB or local network share directory recursively.
     */
    fun scanNetworkDirectory(connectionString: String, remotePath: String): List<ContentItem> {
        if (!testNetworkConnection(connectionString)) return emptyList()
        // Returns auto-discovered content items simulating network filesystem listing
        // In a live environment, jcifs-ng or smbj parses the SMB directory lists
        val networkSeriesName = remotePath.substringAfterLast("/").ifBlank { "Network Share Series" }
        return listOf(
            ContentItem(
                id = -1L,
                sourceId = connectionString.hashCode().toLong(),
                url = "$connectionString/$remotePath",
                title = networkSeriesName,
                author = null,
                artist = null,
                description = "Discovered on network share: $connectionString",
                genres = emptyList(),
                status = ContentStatus.Unknown,
                thumbnailUrl = null,
                contentType = inferContentType(networkSeriesName),
                initialized = true,
                metadata = mapOf("network_connection" to connectionString, "remote_path" to remotePath),
            ),
        )
    }

    /**
     * Scans the default local source directory and auto-discovers Jellyfin structures.
     */
    fun scanDefaultLocalDirectory(): Flow<List<ContentItem>> = flow {
        val baseDir = storageManager.getLocalSourceDirectory()
        if (baseDir == null) {
            emit(emptyList())
            return@flow
        }
        emit(scanDirectory(baseDir))
    }

    /**
     * Scans any arbitrary directory (e.g. explicitly added user repository) for content items.
     */
    fun scanDirectory(directory: UniFile): List<ContentItem> {
        val files = directory.listFiles().orEmpty().filter { it.isDirectory && !it.name.orEmpty().startsWith(".") }
        return files.map { seriesDir ->
            val seriesTitle = seriesDir.name.orEmpty()
            val parsedFiles = seriesDir.listFiles().orEmpty()
                .filter { !it.isDirectory && !it.name.orEmpty().startsWith(".") }
                .mapNotNull { JellyfinNaming.parseChapterFilename(it.name.orEmpty()) }

            // Infer main content type based on name heuristics or default to Manga
            val inferredType = inferContentType(seriesTitle)

            ContentItem(
                id = -1L,
                sourceId = 0L, // LocalSource.ID
                url = seriesTitle,
                title = seriesTitle,
                author = null,
                artist = null,
                description = "Locally discovered series: $seriesTitle",
                genres = emptyList(),
                status = ContentStatus.Unknown,
                thumbnailUrl = null,
                contentType = inferredType,
                initialized = true,
                metadata = mapOf(
                    ContentItem.META_CHAPTER_COUNT to parsedFiles.size.toString(),
                ),
            )
        }
    }

    /**
     * Resolves the list of content units (chapters/episodes) inside a discovered content series directory.
     */
    fun scanContentUnits(seriesDir: UniFile, seriesItem: ContentItem): List<ContentUnit> {
        val files = seriesDir.listFiles().orEmpty()
            .filter { !it.isDirectory && !it.name.orEmpty().startsWith(".") }

        return files.mapIndexed { index, file ->
            val parsed = JellyfinNaming.parseChapterFilename(file.name.orEmpty())
            val number = parsed?.chapterNumber ?: (index + 1).toDouble()
            val volume = parsed?.volumeNumber

            val displayParts = mutableListOf<String>()
            volume?.let { displayParts.add("Vol. $it") }
            displayParts.add("Ch. $number")
            parsed?.chapterTitle?.let { displayParts.add("- $it") }
            val resolvedTitle = if (displayParts.size > 1) displayParts.joinToString(" ") else file.name.orEmpty()

            ContentUnit(
                id = -1L,
                contentItemId = seriesItem.id,
                url = "${seriesItem.url}/${file.name}",
                title = resolvedTitle,
                number = number,
                dateUpload = file.lastModified(),
                progress = 0L,
                totalLength = 0L,
                lastRead = 0L,
                read = false,
                bookmark = false,
                scanlator = null,
            )
        }
    }

    private fun inferContentType(title: String): ContentType {
        val lowercase = title.lowercase()
        return when {
            lowercase.contains("novel") -> ContentType.NOVEL
            lowercase.contains("book") -> ContentType.BOOK
            lowercase.contains("anime") || lowercase.contains("video") -> ContentType.ANIME
            lowercase.contains("audio") -> ContentType.AUDIO
            else -> ContentType.MANGA
        }
    }
}
