package ephyra.presentation.core.util.lang

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * The one debounce value for reactive search queries.
 *
 * There used to be two: this operator defaulted to 300ms while `SEARCH_DEBOUNCE_MILLIS` in the app
 * bar was 250ms, and Library and Sources used the app-bar constant while everything routed through
 * this operator used 300ms. Two numbers for the same interaction means the same typing speed feels
 * different depending on which screen you are on, and neither value is visible where the other is
 * defined. Both names now resolve to this constant so there is exactly one place to change it.
 */
const val SEARCH_DEBOUNCE_MILLIS = 300L

/** @deprecated Use [SEARCH_DEBOUNCE_MILLIS]; kept so existing call sites keep compiling. */
@Deprecated("Use SEARCH_DEBOUNCE_MILLIS", ReplaceWith("SEARCH_DEBOUNCE_MILLIS"))
const val DEFAULT_SEARCH_DEBOUNCE_MILLIS = SEARCH_DEBOUNCE_MILLIS

/**
 * Reactive search operator — the debounced, distinct-until-changed, flatMapLatest
 * pattern repeated across Library, History, Updates, and Browse search.
 *
 * Chains: `map { query }.distinctUntilChanged().debounce(debounce).flatMapLatest { transform }`
 * with the result dispatched on [Dispatchers.IO] (the universal case for DB/network search).
 *
 * The [transform] receives a non-null, non-blank query string. Blank queries are
 * normalised to `""` so [transform] can decide how to handle "show all".
 *
 * Pass [debounce] = 0 to omit debounce (e.g. History search, which is instant).
 *
 * Usage:
 * ```
 * state.map { it.searchQuery }
 *     .searchResults { query -> getHistory.subscribe(query) }
 *     .collect { results -> ... }
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<String?>.searchResults(
    debounce: Long = SEARCH_DEBOUNCE_MILLIS,
    transform: suspend (query: String) -> Flow<T>,
): Flow<T> = this
    .map { it.orEmpty() }
    .distinctUntilChanged()
    .let { if (debounce > 0) it.debounce(debounce) else it }
    .flatMapLatest { query -> transform(query).flowOn(Dispatchers.IO) }
