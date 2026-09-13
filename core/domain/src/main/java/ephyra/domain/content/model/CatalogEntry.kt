package ephyra.domain.content.model

/**
 * Domain representation of an entry within a catalog/source listing.
 */
data class CatalogEntry(
    val key: String,
    val title: String,
    val url: String,
    val coverUrl: String? = null,
    val type: ContentType = ContentType.UNKNOWN,
    val description: String? = null,
    val author: String? = null,
    val genres: List<String> = emptyList(),
)
