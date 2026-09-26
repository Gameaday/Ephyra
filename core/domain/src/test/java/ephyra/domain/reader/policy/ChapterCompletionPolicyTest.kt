package ephyra.domain.reader.policy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChapterCompletionPolicyTest {

    // --- the intended rule -------------------------------------------------------------------

    @Test
    fun `reaching the last page forward completes the chapter`() {
        assertTrue(
            ChapterCompletionPolicy.isChapterComplete(
                pageIndex = 9,
                pageCount = 10,
                visiblePagesAfter = 0,
                chapterResolved = true,
                movingForward = true,
            ),
        )
    }

    @Test
    fun `a single page chapter is complete on reaching it`() {
        assertTrue(
            ChapterCompletionPolicy.isChapterComplete(
                pageIndex = 0,
                pageCount = 1,
                visiblePagesAfter = 0,
                chapterResolved = true,
                movingForward = true,
            ),
        )
    }

    @Test
    fun `a visible page still following means the chapter is not complete`() {
        assertFalse(
            ChapterCompletionPolicy.isChapterComplete(
                pageIndex = 2,
                pageCount = 10,
                visiblePagesAfter = 7,
                chapterResolved = true,
                movingForward = true,
            ),
        )
    }

    @Test
    fun `a merged chapter completes once the last visible page is passed`() {
        // Five pages, only the first visible: pages 1..4 were absorbed by the merge. The reader
        // has seen everything there is to see, so the chapter is done. This is the case the
        // original rule was written for, and it must keep working.
        assertTrue(
            ChapterCompletionPolicy.isChapterComplete(
                pageIndex = 0,
                pageCount = 5,
                visiblePagesAfter = 0,
                chapterResolved = true,
                movingForward = true,
            ),
        )
    }

    // --- defect 1: vacuous tail ---------------------------------------------------------------

    @Test
    fun `an index past the end of the page list does not complete the chapter`() {
        // `[].all { it.isHidden }` is true in Kotlin, so the original inline rule completed the
        // chapter here on an empty tail — the user had reached no content at all.
        assertFalse(
            ChapterCompletionPolicy.isChapterComplete(
                pageIndex = 10,
                pageCount = 10,
                visiblePagesAfter = 0,
                chapterResolved = true,
                movingForward = true,
            ),
        )
    }

    @Test
    fun `a negative index does not complete the chapter`() {
        assertFalse(
            ChapterCompletionPolicy.isChapterComplete(
                pageIndex = -1,
                pageCount = 10,
                visiblePagesAfter = 0,
                chapterResolved = true,
                movingForward = true,
            ),
        )
    }

    @Test
    fun `an empty chapter never completes`() {
        assertFalse(
            ChapterCompletionPolicy.isChapterComplete(
                pageIndex = 0,
                pageCount = 0,
                visiblePagesAfter = 0,
                chapterResolved = true,
                movingForward = true,
            ),
        )
    }

    // --- defect 2: unresolved chapter --------------------------------------------------------

    @Test
    fun `a chapter that has not finished loading never completes`() {
        // The page list exists before loading finishes, so judging from the list alone can mark a
        // chapter read while its remaining pages are still being fetched.
        assertFalse(
            ChapterCompletionPolicy.isChapterComplete(
                pageIndex = 4,
                pageCount = 5,
                visiblePagesAfter = 0,
                chapterResolved = false,
                movingForward = true,
            ),
            "An unresolved chapter has an unknown tail; 'nothing follows' is not 'finished'",
        )
    }

    // --- defect 3: direction -----------------------------------------------------------------

    @Test
    fun `moving backward never completes a chapter even on the last page`() {
        assertFalse(
            ChapterCompletionPolicy.isChapterComplete(
                pageIndex = 9,
                pageCount = 10,
                visiblePagesAfter = 0,
                chapterResolved = true,
                movingForward = false,
            ),
        )
    }

    // --- tail counting -----------------------------------------------------------------------

    @Test
    fun `visible pages after the current page are counted`() {
        // Five pages, currently on index 0. The pages after it are 1, 2, 3 and 4; of those, 1, 2
        // and 4 are hidden, leaving only page 3 visible.
        val hidden = setOf(1, 2, 4)
        assertEquals(
            1,
            ChapterCompletionPolicy.countVisiblePagesAfter(0, 5) { it in hidden },
            "Only page 3 is visible after index 0, so exactly one page of content remains",
        )
    }

    @Test
    fun `the tail is empty for the last page`() {
        assertEquals(0, ChapterCompletionPolicy.countVisiblePagesAfter(4, 5) { false })
    }

    @Test
    fun `an out of range index counts no tail rather than throwing`() {
        assertEquals(0, ChapterCompletionPolicy.countVisiblePagesAfter(9, 5) { false })
        assertEquals(0, ChapterCompletionPolicy.countVisiblePagesAfter(-1, 5) { false })
    }

    @Test
    fun `a fully absorbed tail counts as nothing visible`() {
        assertEquals(0, ChapterCompletionPolicy.countVisiblePagesAfter(0, 4) { true })
    }
}
