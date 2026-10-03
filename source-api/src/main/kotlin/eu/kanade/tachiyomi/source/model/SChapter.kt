@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.source.model

import kotlinx.serialization.json.JsonObject
import java.io.Serializable

interface SChapter : Serializable {

    var url: String

    var name: String

    var date_upload: Long

    /**
     * Chapter number as a float. Deprecated upstream in favour of [number].
     *
     * Kept because extensions compiled against older APIs still assign it. It is a **lossy** stand-in
     * for [number], which is a `String`: a chapter labelled `"12.5a"` cannot round-trip through a
     * `Float`. [effectiveNumber] prefers the string form for that reason.
     */
    @Deprecated("Provide SChapter.number instead")
    var chapter_number: Float

    /**
     * Chapter number in string form — `"1"`, `"1.5"`, `"1a"`, `"-1"`, or `"nan"`.
     *
     * A `String` because upstream widened it from `Float` precisely so suffixes survive. Declared
     * here so an extension assigning it does not fail with `NoSuchFieldError` mid-browse.
     *
     * @since tachiyomix 1.7
     */
    var number: String?

    /** Volume number in string form. `null` for unnumbered volumes. */
    var volume: String?

    /** Single scanlator. Deprecated upstream in favour of [scanlators]. */
    @Deprecated("Provide SChapter.scanlators instead")
    var scanlator: String?

    /** Scanlation groups, as the list the source actually reports. */
    var scanlators: List<String>

    /**
     * BCP 47 tag for the chapter's content language. `null` means "same as the manga's".
     *
     * @since tachiyomix 1.7
     */
    var language: String?

    /** Whether the chapter is locked or otherwise inaccessible. */
    var locked: Boolean

    /** Free-form note shown alongside the chapter — availability dates, and so on. */
    var note: String?

    /**
     * Extra metadata associated with the chapter.
     *
     * The JSON object is not visible to users and is intended for internal or source-specific
     * purposes. Apps may define their own namespaced keys (e.g., `"ephyra.*"`) for sources to
     * populate. This mirrors the upstream `tachiyomix` 1.6 `SChapter` API — extensions compiled
     * against it call `getMemo()`/`setMemo()` on instances, so this member must exist or the
     * extension crashes at runtime with an `IncompatibleClassChangeError`.
     *
     * @since tachiyomix 1.6
     */
    var memo: JsonObject

    fun copyFrom(other: SChapter) {
        name = other.name
        url = other.url
        date_upload = other.date_upload
        chapter_number = other.chapter_number
        scanlator = other.scanlator
        memo = other.memo
        number = other.number
        volume = other.volume
        scanlators = other.scanlators
        language = other.language
        locked = other.locked
        note = other.note
    }

    /**
     * The chapter number, preferring the string form.
     *
     * [number] is authoritative upstream and [chapter_number] is deprecated, but the two are
     * independent — upstream does not mirror one into the other — so an extension compiled against
     * either one leaves the other unset. Falling back is what stops a 1.4-era extension's number
     * from reading as `null`.
     */
    fun effectiveNumber(): String? = number ?: chapter_number.takeIf { it > 0f }?.toString()

    companion object {
        fun create(): SChapter {
            return SChapterImpl()
        }
    }
}
