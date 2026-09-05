package ephyra.feature.browse.source.globalsearch

import ephyra.domain.manga.interactor.TitleNormalizer
import ephyra.domain.manga.model.Manga

/**
 * Cross-source result merger — the search-side application of Smart Merge.
 *
 * Multiple catalogues frequently expose the same series (a Mihon APK extension and an
 * Ephyra JS profile for the same site, or aggregators). [merge] collapses entries that
 * refer to the same work into a single [MergedSearchResult] so users see one row with
 * a "merged from N sources" badge instead of near-duplicate rows.
 *
 * Matching is deliberately conservative:
 *  - exact match on [TitleNormalizer.forEquality] (aggressive normalization), or
 *  - near-identical titles ([TitleNormalizer.isFuzzyMatch], Jaro-Winkler >= [FUZZY_THRESHOLD]).
 *
 * Pure JVM logic: fully unit-testable, no Android or network dependencies.
 */
object SearchResultMerger {

    /** Two titles at or above this Jaro-Winkler similarity are treated as the same work. */
    const val FUZZY_THRESHOLD = 0.97

    /**
     * Merges results from every source into deduplicated entries.
     *
     * Entries matched to more sources sort first (highest confidence), preserving
     * first-seen order otherwise so the UI stays stable between searches.
     */
    fun merge(results: List<Manga>): List<MergedSearchResult> {
        if (results.size <= 1) return results.map { MergedSearchResult(it, mutableListOf(it.source)) }

        val merged = ArrayList<MergedSearchResult>(results.size)
        for (manga in results) {
            val key = TitleNormalizer.forEquality(manga.title)
            if (key.isEmpty()) {
                merged.add(MergedSearchResult(manga, mutableListOf(manga.source)))
                continue
            }
            val existing = merged.firstOrNull { candidate ->
                candidate.manga.source != manga.source &&
                    (
                        candidate.normalizedKey == key ||
                            TitleNormalizer.isFuzzyMatch(candidate.normalizedKey, key, FUZZY_THRESHOLD)
                        )
            }
            if (existing != null) {
                existing.sourceIds.add(manga.source)
            } else {
                merged.add(MergedSearchResult(manga, mutableListOf(manga.source), key))
            }
        }
        return merged.sortedWith(
            compareByDescending<MergedSearchResult> { it.sourceIds.size }
                .thenBy {
                    results.indexOfFirst { m ->
                        m === it.manga ||
                            (m.id == it.manga.id && m.source == it.manga.source)
                    }
                },
        )
    }
}

/**
 * A single search row: the representative manga plus the IDs of every source whose
 * result was merged into it. `sourceIds.size > 1` enables the "merged" badge in the UI.
 */
data class MergedSearchResult(
    val manga: Manga,
    val sourceIds: MutableList<Long>,
    val normalizedKey: String = TitleNormalizer.forEquality(manga.title),
)
