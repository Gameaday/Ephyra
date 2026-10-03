@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.source.model

import kotlinx.serialization.json.JsonObject
import java.io.Serializable

interface SManga : Serializable {

    var url: String

    var title: String

    var artist: String?

    var author: String?

    var description: String?

    /**
     * Single-genre string. Deprecated upstream in favour of [genres]; kept because extensions
     * compiled against older APIs still assign it and [effectiveGenres] reads it.
     */
    @Deprecated("Provide SManga.genres instead")
    var genre: String?

    /**
     * Genres, as the list the source actually reports.
     *
     * **Why this is not the same thing as [genre].** Upstream deprecates [genre] rather than
     * removing it, declares this authoritative, and does **not** mirror one into the other. So an
     * extension that sets only [genres] leaves [genre] null, and an app that reads [genre] sees
     * nothing from a source using the current field.
     *
     * That is only possible because the field exists. Assigning one this interface does not declare
     * fails with `NoSuchFieldError` — after the extension loaded, mid-browse, with no useful
     * message. See [effectiveGenres] for reading both.
     *
     * @since tachiyomix 1.7
     */
    var genres: List<String>

    /**
     * Preferred reading mode the source reports, or `null` when it offers a mix and is explicit that
     * it has no majority.
     *
     * @since tachiyomix 1.7
     */
    var readingMode: ReadingMode?

    /** A wide header image, when the source offers one. */
    var banner: String?

    /** Alternative titles — official translations, romanizations, regional names. */
    var altTitles: List<String>

    /**
     * BCP 47 tag for the work's primary language. `null` means "same as the source's language".
     *
     * @since tachiyomix 1.7
     */
    var language: String?

    /** Source-provided rating as a percentile, or `null` when there is none. */
    var score: Int?

    /**
     * Content rating, for sources that expose one.
     *
     * Non-null upstream with a `SAFE` default, because an app filtering or blurring covers has to
     * be able to read it without a null check on a field that is almost always set.
     *
     * @since tachiyomix 1.7
     */
    var contentRating: ContentRating

    var status: Int

    var thumbnail_url: String?

    var update_strategy: UpdateStrategy

    var initialized: Boolean

    /**
     * Extra metadata associated with the manga.
     *
     * The JSON object is not visible to users and is intended for internal or source-specific
     * purposes. Apps may define their own namespaced keys (e.g., `"ephyra.*"`) for sources to
     * populate. This mirrors the upstream `tachiyomix` 1.6 `SManga` API — extensions compiled
     * against it call `getMemo()`/`setMemo()` on instances, so this member must exist or the
     * extension crashes at runtime with an `IncompatibleClassChangeError`.
     *
     * @since tachiyomix 1.6
     */
    var memo: JsonObject

    fun effectiveGenres(): List<String>? {
        // Reads both, because upstream does not mirror one into the other: an extension compiled
        // against 1.7 sets `genres` and leaves `genre` null, and one compiled against 1.4 does the
        // reverse. Preferring the current field and falling back means neither source's genres are
        // silently invisible.
        if (genres.isNotEmpty()) return genres
        if (genre.isNullOrBlank()) return null
        return genre?.split(", ")?.map { it.trim() }?.filterNot { it.isBlank() }?.distinct()
    }

    fun copy() = create().also {
        it.url = url
        it.title = title
        it.artist = artist
        it.author = author
        it.description = description
        it.genre = genre
        it.genres = genres
        it.status = status
        it.thumbnail_url = thumbnail_url
        it.update_strategy = update_strategy
        it.initialized = initialized
        it.memo = memo
        it.banner = banner
        it.altTitles = altTitles
        it.language = language
        it.score = score
        it.contentRating = contentRating
        it.readingMode = readingMode
    }

    /**
     * Age or content rating, for sources that expose one.
     *
     * Defaults to [SAFE] upstream because an app blurring covers has to be able to read it without a
     * null check on a field that is almost always unset.
     *
     * @since tachiyomix 1.7
     */
    enum class ContentRating {
        SAFE,
        SUGGESTIVE,
        ADULT,
    }

    /**
     * Preferred reading mode the source reports.
     *
     * @since tachiyomix 1.7
     */
    enum class ReadingMode {
        RIGHT_TO_LEFT,
        LEFT_TO_RIGHT,
        LONG_STRIP,
    }

    companion object {
        const val UNKNOWN = 0
        const val ONGOING = 1
        const val COMPLETED = 2
        const val LICENSED = 3
        const val PUBLISHING_FINISHED = 4
        const val CANCELLED = 5
        const val ON_HIATUS = 6

        fun create(): SManga {
            return SMangaImpl()
        }
    }
}
