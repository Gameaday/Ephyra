package ephyra.feature.library

interface ILibraryItem {
    val id: Long
    val title: String
    val subtitle: String?
    val coverUrl: String?
    val downloadCount: Long
    val unreadCount: Long
    val isLocal: Boolean
    val sourceLanguage: String
    val sourceId: Long
    val sourceName: String
    val isFavorite: Boolean
    val genres: List<String>?

    // Rich optional metadata for display badges
    val canonicalId: String?
    val coverLastModified: Long
    val sourceStatus: Long

    fun matches(constraint: String): Boolean
}
