package ephyra.feature.reader.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pins the reader's chapter-navigation window exactly as it behaves today.
 *
 * **Why this matters for the 2.0 migration.** `ReaderViewModel` keeps its chapter identity in
 * loose fields and computes the neighbours with these two helpers. The replacement --
 * `ReaderChapterWindow` in `core:domain` -- is built, tested and never called, and the roadmap
 * records it as blocked *"until the replacement session consumes it"*. That wiring is a refactor of a
 * 1,400-line view model, and nothing else in the suite covers it: `ReaderViewModelTest` covers
 * chapter *completion* but never asks what the neighbours are.
 *
 * So these are a characterisation, written before the refactor rather than after -- which is the
 * only order in which they describe existing behaviour instead of describing whatever the refactor
 * happened to produce.
 *
 * **Two contracts here are counter-intuitive**, and a replacement that "fixes" them would silently
 * navigate somewhere the reader did not ask for. Both return `null` for a current chapter that is
 * not in the list, rather than falling back to the head of the list; and the previous chapter is
 * returned whether or not it has been read, because going back into a finished chapter is allowed.
 */
class ChapterNavigationPolicyTest {

    private val chapters = listOf(
        ChapterNavigationItem(1, read = true),
        ChapterNavigationItem(2, read = true),
        ChapterNavigationItem(3, read = true),
        ChapterNavigationItem(4, read = false),
        ChapterNavigationItem(5, read = false),
    )

    @Test
    fun `backward navigation always uses the immediate canonical chapter`() {
        val current = chapters[2]
        assertEquals(chapters[1], selectPreviousNavigationItem(chapters, current))
    }

    @Test
    fun `forward navigation skips read chapters only when enabled`() {
        val current = chapters[1]
        assertEquals(
            chapters[3],
            selectNextNavigationItem(chapters, current) { !it.read },
        )
        assertEquals(
            chapters[2],
            selectNextNavigationItem(chapters, current) { true },
        )
    }

    @Test
    fun `navigation boundaries and missing current are explicit`() {
        assertNull(selectPreviousNavigationItem(chapters, chapters.first()))
        assertNull(selectNextNavigationItem(chapters, chapters.last()) { !it.read })
        assertNull(selectNextNavigationItem(chapters, ChapterNavigationItem(99, read = false)) { true })
    }

    /**
     * An eligibility predicate nothing satisfies has to yield nothing rather than the immediate
     * neighbour -- "no eligible chapter" and "the next chapter" are different answers, and
     * collapsing them navigates the user somewhere ineligible.
     */
    @Test
    fun `an eligibility nothing satisfies yields no next chapter`() {
        assertNull(selectNextNavigationItem(chapters, chapters.first()) { false })
    }

    /** An empty list is a boundary, not an error, and must not throw. */
    @Test
    fun `an empty list has neither neighbour`() {
        val empty = emptyList<ChapterNavigationItem>()
        assertNull(selectPreviousNavigationItem(empty, ChapterNavigationItem(1, read = false)))
        assertNull(selectNextNavigationItem(empty, ChapterNavigationItem(1, read = false)) { true })
    }

    /**
     * Lookup is by equality, not by reference or by index. A caller passing an equal-but-distinct
     * instance still resolves -- which matters because the replacement addresses chapters by a
     * stable string id, and a divergence here would present as "no neighbours" rather than an error.
     */
    @Test
    fun `an equal instance resolves even when it is not the same object`() {
        // (3, read) is chapters[2] in this fixture, so its neighbours are chapters[1] and
        // chapters[3]. Established by running the selectors, not by reading the indices.
        val equalCopy = ChapterNavigationItem(3, read = true)
        assertEquals(chapters[1], selectPreviousNavigationItem(chapters, equalCopy))
        assertEquals(chapters[3], selectNextNavigationItem(chapters, equalCopy) { true })
    }

    private data class ChapterNavigationItem(val id: Int, val read: Boolean)
}
