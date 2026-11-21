package ephyra.domain.chapter.model

import kotlinx.serialization.Serializable

/**
 * One page as [ephyra.domain.chapter.service.ChapterCache] persists it.
 *
 * These three fields are the whole of what a page list cache holds. The extension ABI's `Page`
 * carries `status` and `progress` too, but both are `@Transient`, so they were never written —
 * and a cache entry is a record, not a live object. Storing [CachedPage] makes the persisted
 * shape explicit instead of inheriting whatever a mutable source DTO happens to serialize.
 *
 * Field names match the previous `Page` wire format, so existing cache entries still decode.
 */
@Serializable
data class CachedPage(
    val index: Int,
    val url: String,
    val imageUrl: String? = null,
)
