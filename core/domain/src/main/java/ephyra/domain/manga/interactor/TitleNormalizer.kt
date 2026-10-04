package ephyra.domain.manga.interactor

import com.aallam.similarity.JaroWinkler

/**
 * Single source of truth for all title matching across the app.
 *
 * Title normalization appears in several features (search dedup, tracker matching,
 * deep search, cover search) with two genuinely different semantics:
 *
 *  - [forEquality]  strips every non-letter/digit character *including* spaces, so
 *    "Attack on Titan!" and "attackontitan" collapse to one key. Use this for
 *    exact-equality dedup where word boundaries must not matter.
 *
 *  - [forMatching]  preserves word boundaries (punctuation → space, whitespace
 *    collapsed), so "Re:Zero" becomes "re zero". Use this for substring and fuzzy
 *    matching, where tokenising on spaces is meaningful.
 *
 * [similarity] and [isFuzzyMatch] delegate to the shared `string-similarity-kotlin`
 * library's Jaro-Winkler, so every feature scores titles identically instead of each
 * re-implementing a string metric.
 *
 * Pure JVM, no Android dependencies — safe for unit tests and domain-layer use.
 */
object TitleNormalizer {

    private val jaroWinkler = JaroWinkler()

    // Drops everything except unicode letters and digits — NO spaces. For exact-equality dedup.
    private val equalityRegex = Regex("[^\\p{L}\\p{N}]")

    // Keeps letters, digits and whitespace; drops punctuation. For word-boundary matching.
    private val matchingPunctRegex = Regex("[^\\p{L}\\p{N}\\s]")
    private val multiSpaceRegex = Regex("\\s+")

    /** Aggressive normalization for exact-equality dedup: "Attack on Titan!" → "attackontitan". */
    fun forEquality(title: String): String =
        equalityRegex.replace(title.lowercase().trim(), "")

    /** Semantic normalization preserving word boundaries: "Re:Zero" → "re zero". */
    fun forMatching(title: String): String =
        title.lowercase()
            .replace(matchingPunctRegex, " ")
            .replace(multiSpaceRegex, " ")
            .trim()

    /** Jaro-Winkler similarity in [0, 1]; 1.0 means identical. */
    fun similarity(a: String, b: String): Double =
        jaroWinkler.similarity(a, b)

    /** True when [a] and [b] are at least [threshold] similar (length-guarded). */
    fun isFuzzyMatch(a: String, b: String, threshold: Double): Boolean {
        if (a == b) return true
        if (a.length <= 4 || b.length <= 4) return a == b
        return similarity(a, b) >= threshold
    }

    /**
     * The single canonical identity key for a work: normalized title + author + genres,
     * SHA-256 hashed so it is stable, reproducible, and source-independent.
     *
     * **This is the load-bearing definition for RFC-0001.** Every component goes
     * through [forEquality], so "Attack on Titan!" ingested from a filename, an
     * extension, and Jellyfin all produce the same key. Previously the ingest-side
     * hash (`CanonicalDeduplicator`) used bare lowercase/trim, so the same work keyed
     * differently depending on which path produced it — the exact divergence this
     * function exists to end. Callers needing a work key must use this; nothing else
     * may hash titles.
     */
    fun canonicalKey(title: String, author: String?, genres: List<String>): String {
        val rawInput = buildString {
            append(forEquality(title))
            author?.let { append(forEquality(it)) }
            genres.sortedBy { forEquality(it) }.forEach { append(forEquality(it)) }
        }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(rawInput.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
