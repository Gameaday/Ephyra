package ephyra.domain.manga.model

import java.util.Locale

/**
 * Computes an updated alternative titles list by merging new titles into the manga's existing list.
 * Deduplicates case-insensitively, filters blanks, and excludes the primary title.
 * Returns null if no new titles were added (caller can skip the DB update).
 */
fun Manga.mergedAlternativeTitles(newTitles: List<String>): List<String>? {
    val primary = title
    val existing = alternativeTitles.toMutableList()
    val previousSize = existing.size
    // Locale.ROOT: this is a data dedup key, not display text. Using the device locale would make
    // two titles dedup differently depending on the phone's language (e.g. Turkish dotted/dotless I).
    val existingLower = existing.map { it.lowercase(Locale.ROOT) }.toMutableSet()
    val primaryLower = primary.lowercase(Locale.ROOT)

    for (t in newTitles) {
        if (t.isBlank()) continue
        val lower = t.lowercase(Locale.ROOT)
        if (lower == primaryLower) continue
        if (!existingLower.add(lower)) continue
        existing.add(t)
    }
    return if (existing.size > previousSize) existing else null
}
