package ephyra.domain.content.ingest

import ephyra.domain.content.model.ContentItem
import ephyra.domain.manga.interactor.TitleNormalizer

object CanonicalDeduplicator {
    /**
     * Stable content hash — delegates to [TitleNormalizer.canonicalKey], the single
     * source of truth for work identity (RFC-0001). Retained as a named entry point
     * for ingest callers; do not reintroduce local hashing here.
     */
    fun generateContentHash(title: String, author: String?, genres: List<String>): String =
        TitleNormalizer.canonicalKey(title, author, genres)

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
