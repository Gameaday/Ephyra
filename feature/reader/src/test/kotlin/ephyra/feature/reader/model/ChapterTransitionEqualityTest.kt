package ephyra.feature.reader.model

import ephyra.domain.chapter.model.Chapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Pins transition identity.
 *
 * The reader resolves a position with `items.indexOf`, so transition equality is navigation
 * behaviour, not an implementation detail. A `Prev(A, B)` must never equal a `Next(B, A)`: after a
 * forward crossing the two exist in the same pager history, and treating them as one makes an item
 * index from the previous chapter resolve inside the new one — one chapter-boundary item away from
 * the page it describes.
 */
class ChapterTransitionEqualityTest {

    private fun chapter(id: Long): ReaderChapter =
        ReaderChapter(Chapter.create().copy(id = id, mangaId = 1L, name = "Chapter $id"))

    @Test
    fun `the same direction across the same boundary is equal`() {
        val a = chapter(1)
        val b = chapter(2)

        assertEquals(ChapterTransition.Prev(a, b), ChapterTransition.Prev(a, b))
        assertEquals(ChapterTransition.Next(a, b), ChapterTransition.Next(a, b))
        assertEquals(
            ChapterTransition.Prev(a, b).hashCode(),
            ChapterTransition.Prev(a, b).hashCode(),
        )
    }

    @Test
    fun `opposite directions across the same boundary are not equal`() {
        val a = chapter(1)
        val b = chapter(2)

        assertNotEquals(ChapterTransition.Prev(a, b), ChapterTransition.Next(b, a))
        assertNotEquals(ChapterTransition.Next(a, b), ChapterTransition.Prev(b, a))
    }

    @Test
    fun `the same direction across a different boundary is not equal`() {
        val a = chapter(1)
        val b = chapter(2)
        val c = chapter(3)

        assertNotEquals(ChapterTransition.Next(a, b), ChapterTransition.Next(b, c))
        assertNotEquals(ChapterTransition.Prev(a, b), ChapterTransition.Prev(b, c))
    }

    @Test
    fun `equality and hash code agree`() {
        val a = chapter(1)
        val b = chapter(2)
        val forward = ChapterTransition.Next(a, b)
        val sameBoundary = ChapterTransition.Next(a, b)
        val backward = ChapterTransition.Prev(b, a)

        assertEquals(forward, sameBoundary)
        assertEquals(forward.hashCode(), sameBoundary.hashCode())
        assertNotEquals(forward, backward)
    }
}
