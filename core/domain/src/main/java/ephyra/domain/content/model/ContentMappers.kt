package ephyra.domain.content.model

import ephyra.domain.chapter.model.Chapter
import ephyra.domain.chapter.model.ChapterUpdate
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaUpdate

/**
 * Maps a manga-specific [Manga] domain model to a generic, media-agnostic [ContentItem].
 */
fun Manga.toContentItem(): ContentItem = ContentItem(
    id = id,
    sourceId = source,
    url = url,
    title = title,
    author = author,
    artist = artist,
    description = description,
    genres = genre.orEmpty(),
    status = ContentStatus.fromLegacyInt(status),
    thumbnailUrl = thumbnailUrl,
    contentType = contentType,
    favorite = favorite,
    dateAdded = dateAdded,
    lastUpdate = lastUpdate,
    initialized = initialized,
)

/**
 * Maps a generic [ContentItem] to a [MangaUpdate] for database updates.
 */
fun ContentItem.toMangaUpdate(): MangaUpdate = MangaUpdate(
    id = id,
    source = sourceId,
    url = url,
    title = title,
    author = author,
    artist = artist,
    description = description,
    genre = genres,
    status = when (status) {
        ContentStatus.Ongoing -> 1L
        ContentStatus.Completed -> 2L
        ContentStatus.Licensed -> 4L
        ContentStatus.Cancelled -> 5L
        ContentStatus.Hiatus -> 6L
        else -> 0L
    },
    thumbnailUrl = thumbnailUrl,
    favorite = favorite,
    dateAdded = dateAdded,
    lastUpdate = lastUpdate,
    initialized = initialized,
    contentType = contentType,
)

/**
 * Maps a manga-specific [Chapter] domain model to a generic, media-agnostic [ContentUnit].
 */
fun Chapter.toContentUnit(progress: Long = 0L, totalLength: Long = 0L): ContentUnit = ContentUnit(
    id = id,
    contentItemId = mangaId,
    url = url,
    title = name,
    number = chapterNumber,
    dateUpload = dateUpload,
    progress = progress.takeIf { it > 0 } ?: lastPageRead,
    totalLength = totalLength,
    lastRead = lastModifiedAt,
    read = read,
    bookmark = bookmark,
    scanlator = scanlator,
)

/**
 * Maps a generic [ContentUnit] to a [ChapterUpdate] for database updates.
 */
fun ContentUnit.toChapterUpdate(): ChapterUpdate = ChapterUpdate(
    id = id,
    read = read,
    bookmark = bookmark,
    lastPageRead = progress,
)
