package ephyra.core.download.util

import ephyra.domain.chapter.model.Chapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins duplicate-chapter grouping, which is applied to the *live* reader navigation list.
 *
 * `removeDuplicates` keys on the chapter number. That comparison must be tolerant rather than
 * exact, because a backup restore widens the number through a `Float` (`12.3` becomes
 * `12.300000190734863`) and the stored value then disagrees with the live source's bit pattern.
 * With exact equality the two land in different groups, so the chapter is never deduplicated
 * against its twin and skip-duplicate navigation can reach the same chapter twice.
 */
class RemoveDuplicatesTest {

    private fun chapter(
        id: Long,
        number: Double,
        scanlator: String? = null,
    ) = Chapter(
        id = id,
        mangaId = 1L,
        read = false,
        bookmark = false,
        lastPageRead = 0L,
        dateFetch = 0L,
        sourceOrder = id,
        url = "/chapter/$id",
        name = "Chapter $id",
        dateUpload = 0L,
        chapterNumber = number,
        scanlator = scanlator,
        lastModifiedAt = 0L,
        version = 1L,
    )

    @Test
    fun `a restored duplicate is grouped with its live twin`() {
        val live = chapter(id = 1L, number = 12.3, scanlator = "one")
        val restored = chapter(id = 2L, number = 12.300000190734863, scanlator = "two")

        val deduplicated = listOf(live, restored).removeDuplicates(currentChapter = live)

        assertEquals(1, deduplicated.size, "the same chapter number must collapse to one entry")
    }

    @Test
    fun `exact duplicates still collapse`() {
        val first = chapter(id = 1L, number = 5.0, scanlator = "one")
        val second = chapter(id = 2L, number = 5.0, scanlator = "two")

        assertEquals(1, listOf(first, second).removeDuplicates(first).size)
    }

    @Test
    fun `genuinely different numbers are not collapsed`() {
        val first = chapter(id = 1L, number = 12.3)
        val second = chapter(id = 2L, number = 12.4)

        assertEquals(2, listOf(first, second).removeDuplicates(first).size)
    }

    @Test
    fun `unrecognised numbers are never collapsed together`() {
        // A source that reports no chapter numbers would otherwise collapse a whole series.
        val first = chapter(id = 1L, number = -1.0)
        val second = chapter(id = 2L, number = -1.0)

        assertEquals(2, listOf(first, second).removeDuplicates(first).size)
    }

    @Test
    fun `the current chapter wins within a duplicate group`() {
        val other = chapter(id = 1L, number = 3.0, scanlator = "one")
        val current = chapter(id = 2L, number = 3.000000011920929, scanlator = "two")

        val kept = listOf(other, current).removeDuplicates(currentChapter = current)

        assertEquals(1, kept.size)
        assertEquals(current.id, kept.single().id)
    }

    @Test
    fun `a same-scanlator twin wins when the current chapter is absent from the group`() {
        val first = chapter(id = 1L, number = 7.0, scanlator = "shared")
        val restored = chapter(id = 2L, number = 7.000000953674316, scanlator = "other")
        val elsewhere = chapter(id = 3L, number = 99.0, scanlator = "elsewhere")

        val kept = listOf(first, restored, elsewhere).removeDuplicates(currentChapter = elsewhere)

        // Two groups survive: the collapsed 7.x pair, and the unrelated 99.0.
        assertEquals(2, kept.size)
        // `elsewhere` is current, so it wins its own group; the 7.x group falls back to the
        // first available, which is `first`.
        assertEquals(elsewhere.id, kept.single { it.chapterNumber == 99.0 }.id)
        assertEquals(first.id, kept.single { it.id != elsewhere.id }.id)
    }
}
