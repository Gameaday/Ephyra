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
}
