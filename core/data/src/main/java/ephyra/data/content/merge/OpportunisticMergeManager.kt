package ephyra.data.content.merge

import com.aallam.similarity.NormalizedLevenshtein
import ephyra.domain.content.model.ContentItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles opportunistic matching and merging of duplicate series from local
 * file hierarchies and remote catalog sources using a normalized Levenshtein similarity threshold.
 */
@Singleton
class OpportunisticMergeManager @Inject constructor() {
    private val similarityService = NormalizedLevenshtein()
    private val similarityThreshold = 0.85

    /**
     * Merges lists of local and remote content items, uniting matches under single entries.
     */
    fun mergeContent(localItems: List<ContentItem>, remoteItems: List<ContentItem>): List<ContentItem> {
        val mergedList = mutableListOf<ContentItem>()
        val processedRemoteUrls = mutableSetOf<String>()

        for (local in localItems) {
            // Find a matching remote item using title similarity
            val match = remoteItems.firstOrNull { remote ->
                similarityService.similarity(local.title.lowercase(), remote.title.lowercase()) >= similarityThreshold
            }

            if (match != null) {
                processedRemoteUrls.add(match.url)
                // Merge properties: keep remote description/author/thumbnail but local source ID and URL references
                mergedList.add(
                    local.copy(
                        author = match.author ?: local.author,
                        artist = match.artist ?: local.artist,
                        description = match.description ?: local.description,
                        genres = (local.genres + match.genres).distinct(),
                        thumbnailUrl = match.thumbnailUrl ?: local.thumbnailUrl,
                        metadata = local.metadata + match.metadata + mapOf("canonical_match_url" to match.url),
                    ),
                )
            } else {
                mergedList.add(local)
            }
        }

        // Add remaining remote items that were not merged
        for (remote in remoteItems) {
            if (!processedRemoteUrls.contains(remote.url)) {
                mergedList.add(remote)
            }
        }

        return mergedList
    }
}
