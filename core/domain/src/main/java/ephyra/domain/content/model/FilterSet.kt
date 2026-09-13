package ephyra.domain.content.model

/**
 * Filter and query parameters used to browse or search content sources.
 */
data class FilterSet(
    val query: String = "",
    val sortOrder: SortOrder = SortOrder.POPULAR,
    val genres: Set<String> = emptySet(),
    val tags: Map<String, String> = emptyMap(),
) {
    enum class SortOrder {
        POPULAR,
        LATEST,
        ALPHABETICAL,
    }
}
