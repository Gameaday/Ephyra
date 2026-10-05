@file:Suppress("PropertyName")

package ephyra.data.database.models

import kotlinx.serialization.json.JsonObject

class ChapterImpl : Chapter {

    override var id: Long? = null

    override var manga_id: Long? = null

    override lateinit var url: String

    override lateinit var name: String

    override var scanlator: String? = null

    override var read: Boolean = false

    override var bookmark: Boolean = false

    override var last_page_read: Int = 0

    override var date_fetch: Long = 0

    override var date_upload: Long = 0

    override var chapter_number: Float = 0f

    override var source_order: Int = 0

    override var last_modified: Long = 0

    override var version: Long = 0

    // Inherited from `SChapter` (tachiyomix 1.6). Memo metadata is source-specific and is
    // attached/copy-merged by extensions via `copyFrom`; host storage keeps it empty.
    override var memo: JsonObject = JsonObject(emptyMap())

    // Inherited from `SChapter` (tachiyomix 1.7). These describe the *source's* view of a chapter —
    // its own string numbering, volume, scanlation groups, content language, lock state and note.
    // Host storage does not persist them, so they stay at their defaults rather than being invented;
    // `chapter_number` above remains the stored value and `effectiveNumber()` prefers `number` when
    // an extension sets it.
    override var number: String? = null

    override var volume: String? = null

    override var scanlators: List<String> = emptyList()

    override var language: String? = null

    override var locked: Boolean = false

    override var note: String? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val chapter = other as Chapter
        if (url != chapter.url) return false
        return id == chapter.id
    }

    override fun hashCode(): Int {
        return url.hashCode() + id.hashCode()
    }
}
