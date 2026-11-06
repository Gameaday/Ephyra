package ephyra.feature.reader.model

import ephyra.domain.chapter.model.Chapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Characterises the contracts the reader depends on from the chapter window (RDR-002), now that
 * the view model selects its prev/next chapters through [ReaderChapterWindowAdapter] instead of
 * the deleted feature-local helpers.
 *
 * The richer rules live in the domain ReaderChapterWindowPolicyTest in core:domain; what this
 * suite pins is the wiring: the identities that come back are the reader's own ReaderChapters,
 * the selection follows the canonical list order the caller passes, and the boundaries stay
 * boundaries. These were the contracts of ChapterNavigationPolicyTest, which tested the deleted
 * helpers; they move here rather than silently disappearing with their subjects.
 */
class ReaderChapterWindowAdapterTest {

    private fun dbChapter(
        id: Long,
        read: Boolean = false,
        number: Double = id.toDouble(),
    ) = Chapter.create().copy(
        id = id,
        mangaId = 1L,
        name = "Chapter " + id,
        chapterNumber = number,
        read = read,
        sourceOrder = id,
    )

    private fun window(chapters: List<Chapter>) = chapters.map(::ReaderChapter)

    /** The boundary contract: the first chapter has nothing before it, the last nothing after. */
    @Test
    fun `the first and last chapters are boundaries`() {
        val chapters = window(listOf(dbChapter(1), dbChapter(2), dbChapter(3)))

        val head = ReaderChapterWindowAdapter.neighbours(chapters, chapters.first(), skipReadForward = true)
        assertNull(head.previous, "the first chapter has no predecessor")

        val tail = ReaderChapterWindowAdapter.neighbours(chapters, chapters.last(), skipReadForward = true)
        assertNull(tail.next, "the last chapter has no successor")
    }

    /**
     * The DEF-007 contract: skip-read applies forward only. The previous chapter stays adjacent
     * even when it is read -- backward navigation is how the user deliberately revisits material.
     */
    @Test
    fun `skip read is forward only`() {
        val chapters = window(listOf(dbChapter(1, read = true), dbChapter(2, read = true), dbChapter(3)))

        val neighbours = ReaderChapterWindowAdapter.neighbours(chapters, chapters[2], skipReadForward = true)

        assertEquals(chapters[1], neighbours.previous, "the read previous chapter must stay reachable")
        assertNull(neighbours.next, "with every forward chapter read, forward navigation ends")
    }

    @Test
    fun `without skip read the next chapter is immediately adjacent`() {
        val chapters = window(listOf(dbChapter(1, read = true), dbChapter(2)))

        val neighbours = ReaderChapterWindowAdapter.neighbours(chapters, chapters[0], skipReadForward = false)

        assertEquals(chapters[1], neighbours.next)
    }

    /** A current chapter outside the list has no window -- there is no head-of-list fallback. */
    @Test
    fun `a current chapter outside the list has no neighbours`() {
        val chapters = window(listOf(dbChapter(1), dbChapter(2)))
        val outsider = ReaderChapter(dbChapter(99))

        val neighbours = ReaderChapterWindowAdapter.neighbours(chapters, outsider, skipReadForward = true)

        assertNull(neighbours.previous)
        assertNull(neighbours.next)
    }

    /**
     * The list order the caller passes is the navigation order: the adapter pins it positionally
     * so the canonical reader order the view model computed survives. A chapter-number re-sort
     * would reorder it and change behaviour.
     */
    @Test
    fun `selection follows the passed order rather than chapter numbers`() {
        val chapters = window(
            listOf(dbChapter(10, number = 3.0), dbChapter(11, number = 1.0), dbChapter(12, number = 2.0)),
        )

        val neighbours = ReaderChapterWindowAdapter.neighbours(chapters, chapters[0], skipReadForward = false)

        assertEquals(chapters[1], neighbours.next, "the successor is the next list entry, not the lowest number")
    }

    /** DB unknown numbers (-1.0) never become policy numbers: they cannot reject the window. */
    @Test
    fun `unknown chapter numbers do not reject the window`() {
        val chapters = window(listOf(dbChapter(1, number = -1.0), dbChapter(2, number = -1.0)))

        val neighbours = ReaderChapterWindowAdapter.neighbours(chapters, chapters[0], skipReadForward = false)

        assertEquals(chapters[1], neighbours.next)
    }
}
