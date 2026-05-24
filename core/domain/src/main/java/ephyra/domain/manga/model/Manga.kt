package ephyra.domain.manga.model

import ephyra.core.common.preference.TriState
import ephyra.core.metadata.comicinfo.ComicInfo
import ephyra.core.metadata.comicinfo.ComicInfoPublishingStatus
import ephyra.domain.base.BasePreferences
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.content.model.ContentType
import ephyra.domain.manga.service.CoverCache
import ephyra.domain.reader.model.ReaderOrientation
import ephyra.domain.reader.model.ReadingMode
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import java.io.Serializable
import java.time.Instant

/** @suppress Compose compiler does not reach domain models; use `@Immutable` in UI mappers instead. */
data class Manga(
    val id: Long,
    val source: Long,
    val favorite: Boolean,
    val lastUpdate: Long,
    val nextUpdate: Long,
    val fetchInterval: Int,
    val dateAdded: Long,
    val viewerFlags: Long,
    val chapterFlags: Long,
    val coverLastModified: Long,
    val url: String,
    val title: String,
    val artist: String?,
    val author: String?,
    val description: String?,
    val genre: List<String>?,
    val status: Long,
    val thumbnailUrl: String?,
    val updateStrategy: UpdateStrategy,
    val initialized: Boolean,
    val lastModifiedAt: Long,
    val favoriteModifiedAt: Long?,
    val version: Long,
    val notes: String,
    val metadataSource: Long?,
    val metadataUrl: String?,
    val canonicalId: String?,
    val sourceStatus: Int,
    val alternativeTitles: List<String>,
    val deadSince: Long?,
    val contentType: ContentType,
    val lockedFields: Long,
) : Serializable {

    val expectedNextUpdate: Instant?
        get() = nextUpdate
            .takeIf { MangaStatus.fromValue(status) !is MangaStatus.Completed }
            ?.let { Instant.ofEpochMilli(it) }

    val sorting: Long
        get() = chapterFlags and CHAPTER_SORTING_MASK

    val displayMode: Long
        get() = chapterFlags and CHAPTER_DISPLAY_MASK

    val unreadFilterRaw: Long
        get() = chapterFlags and CHAPTER_UNREAD_MASK

    val downloadedFilterRaw: Long
        get() = chapterFlags and CHAPTER_DOWNLOADED_MASK

    val bookmarkedFilterRaw: Long
        get() = chapterFlags and CHAPTER_BOOKMARKED_MASK

    val unreadFilter: TriState
        get() = when (unreadFilterRaw) {
            CHAPTER_SHOW_UNREAD -> TriState.ENABLED_IS
            CHAPTER_SHOW_READ -> TriState.ENABLED_NOT
            else -> TriState.DISABLED
        }

    val bookmarkedFilter: TriState
        get() = when (bookmarkedFilterRaw) {
            CHAPTER_SHOW_BOOKMARKED -> TriState.ENABLED_IS
            CHAPTER_SHOW_NOT_BOOKMARKED -> TriState.ENABLED_NOT
            else -> TriState.DISABLED
        }

    fun sortDescending(): Boolean {
        return chapterFlags and CHAPTER_SORT_DIR_MASK == CHAPTER_SORT_DESC
    }

    companion object {
        // Generic filter that does not filter anything
        const val SHOW_ALL = 0x00000000L

        const val CHAPTER_SORT_DESC = 0x00000000L
        const val CHAPTER_SORT_ASC = 0x00000001L
        const val CHAPTER_SORT_DIR_MASK = 0x00000001L

        const val CHAPTER_SHOW_UNREAD = 0x00000002L
        const val CHAPTER_SHOW_READ = 0x00000004L
        const val CHAPTER_UNREAD_MASK = 0x00000006L

        const val CHAPTER_SHOW_DOWNLOADED = 0x00000008L
        const val CHAPTER_SHOW_NOT_DOWNLOADED = 0x00000010L
        const val CHAPTER_DOWNLOADED_MASK = 0x00000018L

        const val CHAPTER_SHOW_BOOKMARKED = 0x00000020L
        const val CHAPTER_SHOW_NOT_BOOKMARKED = 0x00000040L
        const val CHAPTER_BOOKMARKED_MASK = 0x00000060L

        const val CHAPTER_SORTING_SOURCE = 0x00000000L
        const val CHAPTER_SORTING_NUMBER = 0x00000100L
        const val CHAPTER_SORTING_UPLOAD_DATE = 0x00000200L
        const val CHAPTER_SORTING_ALPHABET = 0x00000300L
        const val CHAPTER_SORTING_MASK = 0x00000300L

        const val CHAPTER_DISPLAY_NAME = 0x00000000L
        const val CHAPTER_DISPLAY_NUMBER = 0x00100000L
        const val CHAPTER_DISPLAY_MASK = 0x00100000L

        fun create() = Manga(
            id = -1L,
            url = "",
            title = "",
            source = -1L,
            favorite = false,
            lastUpdate = 0L,
            nextUpdate = 0L,
            fetchInterval = 0,
            dateAdded = 0L,
            viewerFlags = 0L,
            chapterFlags = 0L,
            coverLastModified = 0L,
            artist = null,
            author = null,
            description = null,
            genre = null,
            status = 0L,
            thumbnailUrl = null,
            updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
            initialized = false,
            lastModifiedAt = 0L,
            favoriteModifiedAt = null,
            version = 0L,
            notes = "",
            metadataSource = null,
            metadataUrl = null,
            canonicalId = null,
            sourceStatus = 0,
            alternativeTitles = emptyList(),
            deadSince = null,
            contentType = ContentType.UNKNOWN,
            lockedFields = 0L,
        )
    }
}

// TODO: move these into the domain model
val Manga.readingMode: Long
    get() = viewerFlags and ReadingMode.MASK.toLong()

val Manga.readerOrientation: Long
    get() = viewerFlags and ReaderOrientation.MASK.toLong()

fun Manga.downloadedFilter(basePreferences: BasePreferences): TriState {
    if (basePreferences.downloadedOnly().getSync()) return TriState.ENABLED_IS
    return when (downloadedFilterRaw) {
        Manga.CHAPTER_SHOW_DOWNLOADED -> TriState.ENABLED_IS
        Manga.CHAPTER_SHOW_NOT_DOWNLOADED -> TriState.ENABLED_NOT
        else -> TriState.DISABLED
    }
}

fun Manga.chaptersFiltered(basePreferences: BasePreferences): Boolean {
    return unreadFilter != TriState.DISABLED ||
        downloadedFilter(basePreferences) != TriState.DISABLED ||
        bookmarkedFilter != TriState.DISABLED
}

fun Manga.toSManga(): SManga = SManga.create().also {
    it.url = url
    it.title = title
    it.artist = artist
    it.author = author
    it.description = description
    it.genre = genre.orEmpty().joinToString()
    it.status = status.toInt()
    it.thumbnail_url = thumbnailUrl
    it.initialized = initialized
}

fun Manga.copyFrom(other: SManga): Manga {
    val author = other.author ?: author
    val artist = other.artist ?: artist
    val description = other.description ?: description
    val genres = if (other.genre != null) {
        other.getGenres()
    } else {
        genre
    }
    val thumbnailUrl = other.thumbnail_url ?: thumbnailUrl
    return this.copy(
        author = author,
        artist = artist,
        description = description,
        genre = genres,
        thumbnailUrl = thumbnailUrl,
        status = other.status.toLong(),
        updateStrategy = other.update_strategy,
        initialized = other.initialized && initialized,
    )
}

fun Manga.hasCustomCover(coverCache: CoverCache): Boolean {
    return coverCache.getCustomCoverFile(this)?.exists() ?: false
}

/**
 * Creates a ComicInfo instance based on the manga and chapter metadata.
 */
fun getComicInfo(
    manga: Manga,
    chapter: Chapter,
    urls: List<String>,
    categories: List<String>?,
    sourceName: String,
    sourceLang: String? = null,
    startYear: Int = 0,
) = ComicInfo(
    title = ComicInfo.Title(chapter.name),
    series = ComicInfo.Series(manga.title),
    number = chapter.chapterNumber.takeIf { it >= 0 }?.let {
        if ((it.rem(1) == 0.0)) {
            ComicInfo.Number(it.toInt().toString())
        } else {
            ComicInfo.Number(it.toString())
        }
    },
    count = null,
    volume = null,
    web = ComicInfo.Web(urls.joinToString(" ")),
    summary = manga.description?.let { ComicInfo.Summary(it) },
    year = startYear.takeIf { it > 0 }?.let { ComicInfo.Year(it) },
    writer = manga.author?.let { ComicInfo.Writer(it) },
    penciller = manga.artist?.let { ComicInfo.Penciller(it) },
    translator = chapter.scanlator?.let { ComicInfo.Translator(it) },
    genre = manga.genre?.let { ComicInfo.Genre(it.joinToString()) },
    publishingStatus = ComicInfo.PublishingStatusTachiyomi(
        ComicInfoPublishingStatus.toComicInfoValue(manga.status),
    ),
    categories = categories?.let { ComicInfo.CategoriesTachiyomi(it.joinToString()) },
    source = ComicInfo.SourceEphyra(sourceName),
    languageISO = sourceLang?.takeIf { it.isNotBlank() && it != "all" }
        ?.let { ComicInfo.LanguageISO(it) },
    manga = determineMangaField(manga),
    inker = null,
    colorist = null,
    letterer = null,
    coverArtist = null,
    tags = null,
)

/**
 * Determines the ComicInfo Manga field value based on content type and reading mode.
 *
 * Values per ComicInfo v2.0 spec:
 * - "YesAndRightToLeft": manga with right-to-left reading order (default for manga)
 * - "Yes": manga/comic with left-to-right or vertical reading order
 * - null: not a comic/manga format (novels, books)
 */
private fun determineMangaField(manga: Manga): ComicInfo.Manga? {
    return when (manga.contentType) {
        ContentType.NOVEL, ContentType.BOOK, ContentType.ANIME, ContentType.AUDIO -> null
        ContentType.MANGA, ContentType.UNKNOWN -> {
            val mode = ReadingMode.fromPreference(manga.readingMode.toInt())
            when (mode) {
                ReadingMode.LEFT_TO_RIGHT,
                ReadingMode.WEBTOON,
                ReadingMode.CONTINUOUS_VERTICAL,
                ReadingMode.VERTICAL,
                -> ComicInfo.Manga("Yes")
                // RIGHT_TO_LEFT and DEFAULT both get manga RTL
                else -> ComicInfo.Manga("YesAndRightToLeft")
            }
        }
    }
}
