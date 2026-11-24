package ephyra.domain.manga.model

import ephyra.source.api.SourceContentItem
import ephyra.source.api.SourceId
import eu.kanade.tachiyomi.source.model.SManga

fun SourceContentItem.toDomainManga(): Manga {
    val sourceId = sourceId.value
    return Manga.create().copy(
        url = url,
        title = title,
        artist = artist,
        author = author,
        description = description,
        genre = genres,
        status = status?.toLongOrNull() ?: 0L,
        thumbnailUrl = thumbnailUrl,
        updateStrategy =
        metadata["updateStrategy"]?.let { UpdateStrategy.valueOf(it) } ?: UpdateStrategy.ONLY_FETCH_ONCE,
        initialized = metadata["initialized"]?.toBoolean() ?: false,
        source = sourceId.toLongOrNull() ?: -1L,
    )
}

/** Temporary bridge for legacy search path that still returns SManga. */
fun SManga.toDomainManga(sourceId: Long): Manga {
    return Manga.create().copy(
        url = url,
        title = title,
        artist = artist,
        author = author,
        description = description,
        genre = effectiveGenres(),
        status = status.toLong(),
        thumbnailUrl = thumbnail_url,
        updateStrategy = update_strategy.toDomainModel(),
        initialized = initialized,
        source = sourceId,
    )
}
