package ephyra.feature.browse.source.globalsearch

import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.preference.Preference.Companion.appStateKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persisted list of recent global-search queries, newest first, capped at [MAX_ENTRIES].
 *
 * Stored as a single newline-delimited string (order-preserving, unlike string-set
 * preferences) under the app-state preference namespace. Powers the "recent searches"
 * chip row on the Global Search screen so users can one-tap back into prior queries.
 */
@Singleton
class RecentSearches @Inject constructor(
    preferenceStore: PreferenceStore,
) {

    private val pref = preferenceStore.getString(
        appStateKey("recent_global_searches"),
        "",
    )

    fun get(): List<String> = parse(pref.getSync())

    fun observe(): Flow<List<String>> = pref.changes().map { parse(it) }

    /** Moves [query] to the front (or inserts it), trimming the list to [MAX_ENTRIES]. */
    fun record(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val next = buildList {
            add(trimmed)
            parse(pref.getSync()).filterNot { it.equals(trimmed, ignoreCase = true) }.let { addAll(it) }
        }.take(MAX_ENTRIES)
        pref.set(next.joinToString(SEPARATOR))
    }

    fun clear() = pref.set("")

    private fun parse(raw: String): List<String> =
        raw.split(SEPARATOR).filter { it.isNotBlank() }.take(MAX_ENTRIES)

    private companion object {
        const val SEPARATOR = "\n"
        const val MAX_ENTRIES = 8
    }
}
