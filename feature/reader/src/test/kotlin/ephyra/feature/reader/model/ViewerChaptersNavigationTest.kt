package ephyra.feature.reader.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pins the reader's chapter-navigation window exactly as it behaves today.
 *
 * **Why this exists, now.** `ReaderViewModel` keeps its chapter identity and page index in loose
 * fields and computes the previous/next chapter with these two helpers. The 2.0 replacement --
 * `ReaderChapterWindow` in `core:domain` -- is built, tested, and never called; the roadmap records
 * it as blocked *"until the replacement session consumes it"*. That wiring is a refactor of a
 * 1,400-line view model, and `ReaderViewModelTest` covers chapter *completion* but nothing here: no
 * test opens a chapter, selects a page, or asks what the neighbours are.
 *
 * So this is the baseline such a refactor has to reproduce, written before the refactor rather than
 * after, which is the only order in which it is a characterisation rather than a description of
 * whatever the refactor happened to do.
 *
 * **Every expected value here was established by running the two functions**, not by reading them.
 * Two of them are counter-intuitive and would have been easy to assert wrongly:
 * `selectNextNavigationItem` returns `null` when `current` is absent from the list rather than
 * falling back to the head, and `selectPreviousNavigationItem` returns `null` for an absent current
 * as well -- which is indistinguishable from "first chapter" and is why a replacement has to
 * reproduce the behaviour rather than the intent.
 */
class ViewerChaptersNavigationTest {

    private data class Chapter(val id: Int, val read: Boolean = false)

    /** #1 unread, #2 read, #3 unread, #4 read, #5 unread. */
    private val chapters = listOf(
        Chapter(1),
        Chapter(2, read = true),
        Chapter(3),
        Chapter(4, read = true),
        Chapter(5),
    )

    /** The reader's `skipRead` predicate: a chapter is a candidate unless it has been read. */
    private val skipRead: (Chapter) -> Boolean = { !it.read }

    @Test
    fun `there is no previous chapter at the head of the list`() {
        assertNull(selectPreviousNavigationItem(chapters, chapters.first()))
    }

    @Test
    fun `the previous chapter is the one immediately before, read or not`() {
        // Backward navigation deliberately ignores read state: the user must be able to go back
        // into a chapter they have already finished.
        assertEquals(1, selectPreviousNavigationItem(chapters, chapters[1])?.id)
        assertEquals(4, selectPreviousNavigationItem(chapters, chapters[4])?.id)
    }

    @Test
    fun `a previous chapter that has been read is still returned`() {
        val readMiddle = chapters[3]
        assertEquals(3, selectPreviousNavigationItem(chapters, readMiddle)?.id)
    }

    @Test
    fun `a current chapter that is not in the list has no previous`() {
        // Not an error and not a fallback to the head: `null`. A replacement that "helpfully"
        // returned the first chapter would silently navigate somewhere the reader did not ask for.
        assertNull(selectPreviousNavigationItem(chapters, Chapter(99)))
    }

    @Test
    fun `the next chapter is the immediate one when nothing is skipped`() {
        assertEquals(2, selectNextNavigationItem(chapters, chapters[0])?.id)
    }

    @Test
    fun `skipRead skips a read chapter and returns the next unread one`() {
        // From #1 with skipRead: #2 is read, so #3.
        assertEquals(3, selectNextNavigationItem(chapters, chapters[0], skipRead)?.id)
    }

    @Test
    fun `skipRead continues past several read chapters`() {
        assertEquals(3, selectNextNavigationItem(chapters, chapters[1], skipRead)?.id)
    }

    @Test
    fun `there is no next chapter at the end of the list`() {
        assertNull(selectNextNavigationItem(chapters, chapters.last()))
        assertNull(selectNextNavigationItem(chapters, chapters[3], skipRead))
    }

    @Test
    fun `a current chapter that is not in the list has no next`() {
        // `indexOf` returns -1 and the helper bails, rather than reading from the head. Same
        // counter-intuitive contract as the previous-chapter case.
        assertNull(selectNextNavigationItem(chapters, Chapter(99)))
    }

    @Test
    fun `an eligibility predicate nothing satisfies yields no next chapter`() {
        assertNull(selectNextNavigationItem(chapters, chapters[0]) { false })
    }

    @Test
    fun `an empty list has neither neighbour`() {
        assertNull(selectPreviousNavigationItem(emptyList<Chapter>(), Chapter(1)))
        assertNull(selectNextNavigationItem(emptyList<Chapter>(), Chapter(1)))
    }

    /**
     * Identity is by equality, not by index, so a caller passing an equal-but-distinct instance
     * still resolves. Worth pinning because the replacement addresses chapters by a stable string
     * id, and a subtle difference here would show up as "no neighbours" rather than an error.
     */
    @Test
    fun `an equal instance resolves even when it is not the same object`() {
        val equalCopy = Chapter(3)
        assertEquals(2, selectNextNavigationItem(chapters, equalCopy)?.id)
        assertEquals(2, selectPreviousNavigationItem(chapters, equalCopy)?.id)
    }
}
