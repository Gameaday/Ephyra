package ephyra.domain.content.ingest

import ephyra.domain.content.model.ContentItem
import java.security.MessageDigest

object CanonicalDeduplicator {
    /**
     * Generates a stable, reproducible content hash based on structural metadata.
     * Keeps content identical regardless of what source or file naming was used.
     */
    fun generateContentHash(title: String, author: String?, genres: List<String>): String {
        val rawInput = buildString {
            append(title.lowercase().trim())
            author?.let { append(it.lowercase().trim()) }
            genres.sorted().forEach { append(it.lowercase().trim()) }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(rawInput.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Deduplicates a list of content items, merging items with matching content hashes or canonical IDs.
     */
    fun deduplicate(items: List<ContentItem>): List<ContentItem> {
        val uniqueItems = mutableMapOf<String, ContentItem>()

        for (item in items) {
            val key = item.metadata["canonical_hash"]
                ?: generateContentHash(item.title, item.author, item.genres)

            val existing = uniqueItems[key]
            if (existing == null) {
                uniqueItems[key] = item
            } else {
                val mergedMetadata = existing.metadata.toMutableMap().apply {
                    putAll(item.metadata)
                    put("is_merged", "true")
                }
                uniqueItems[key] = existing.copy(
                    description = existing.description ?: item.description,
                    author = existing.author ?: item.author,
                    artist = existing.artist ?: item.artist,
                    genres = (existing.genres + item.genres).distinct(),
                    metadata = mergedMetadata,
                )
            }
        }
        return uniqueItems.values.toList()
    }
}
