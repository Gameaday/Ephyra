package ephyra.data.content.merge

import ephyra.domain.content.model.ContentItem
import ephyra.domain.manga.interactor.TitleNormalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles opportunistic matching and merging of duplicate series from local
 * file hierarchies and remote catalog sources using title similarity.
 */
@Singleton
class OpportunisticMergeManager @Inject constructor() {
    private val similarityThreshold = 0.85

    /**
     * Merges lists of local and remote content items, uniting matches under single entries.
     */
    fun mergeContent(localItems: List<ContentItem>, remoteItems: List<ContentItem>): List<ContentItem> {
        val mergedList = mutableListOf<ContentItem>()
        val processedRemoteUrls = mutableSetOf<String>()

        for (local in localItems) {
            // Find a matching remote item using title similarity. Titles are normalized
            // via the shared TitleNormalizer so local/remote matching uses the same
            // contract as search dedup and tracker matching.
            val match = remoteItems.firstOrNull { remote ->
                TitleNormalizer.isFuzzyMatch(
                    TitleNormalizer.forEquality(local.title),
                    TitleNormalizer.forEquality(remote.title),
                    similarityThreshold,
                )
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
