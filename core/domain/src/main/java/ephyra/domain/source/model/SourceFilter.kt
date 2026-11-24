package ephyra.domain.source.model

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

/**
 * Domain model for source search/browse filters.
 * Wraps the legacy [FilterList] to avoid leaking legacy types into the domain layer.
 */
data class SourceFilter(
    val filters: List<Filter<*>> = emptyList()
) : List<Filter<*>> by filters {

    constructor(vararg filters: Filter<*>) : this(if (filters.isNotEmpty()) filters.asList() else emptyList())

    companion object {
        val EMPTY = SourceFilter()
    }

    /**
     * Converts to the legacy [FilterList] for interop with paging sources.
     */
    fun toLegacyFilterList(): FilterList = FilterList(filters)

    /**
     * Creates a [SourceFilter] from a legacy [FilterList].
     */
    fun fromLegacyFilterList(filterList: FilterList): SourceFilter = SourceFilter(filterList.list)
}