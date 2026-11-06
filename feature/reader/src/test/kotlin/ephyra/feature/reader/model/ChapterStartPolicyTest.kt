package ephyra.feature.reader.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pins which source of truth decides where a chapter opens.
 *
 * The regression this guards is the first chapter transition of a fresh session: live progress and
 * the process-restore index share one field, so the previous chapter's page index was written into
 * the next chapter's start position. On screen that was "it keeps starting on page 2" and, when
 * the index was near the end, the reader continuing into the following chapter.
 */
class ChapterStartPolicyTest {

    @Test
    fun `the saved page index applies only to the chapter that was saved`() {
        val alreadyReadToPageTwo = resolveChapterStartRequest(
            chapterId = 2L,
            chapterRead = false,
            lastPageRead = 0,
            startingAtBeginning = false,
            startFromEnd = false,
            savedChapterId = 2L,
            savedPageIndex = 1,
            savedRestoreApplied = false,
        )

        assertEquals(1, alreadyReadToPageTwo.requestedPage)
        assertEquals(true, alreadyReadToPageTwo.savedRestoreResolved)
    }

    @Test
    fun `a later chapter is never treated as the restore target`() {
        // A fresh session has read page 2 of chapter 1; the live index is 1 and the saved chapter
        // is still chapter 1. Moving to chapter 2 must not reuse that index.
        val nextChapter = resolveChapterStartRequest(
            chapterId = 2L,
            chapterRead = false,
            lastPageRead = 0,
            startingAtBeginning = false,
            startFromEnd = false,
            savedChapterId = 1L,
            savedPageIndex = 1,
            savedRestoreApplied = false,
        )

        assertNull(nextChapter.requestedPage)
        assertEquals(true, nextChapter.savedRestoreResolved)
    }

    @Test
    fun `an explicit forward direction is never overridden`() {
        val forward = resolveChapterStartRequest(
            chapterId = 2L,
            chapterRead = false,
            lastPageRead = 7,
            startingAtBeginning = true,
            startFromEnd = false,
            savedChapterId = 2L,
            savedPageIndex = 7,
            savedRestoreApplied = false,
        )

        assertNull(forward.requestedPage, "forward navigation already set requestedPage to the start")
    }

    @Test
    fun `an explicit backward direction is never overridden`() {
        val backward = resolveChapterStartRequest(
            chapterId = 2L,
            chapterRead = false,
            lastPageRead = 3,
            startingAtBeginning = false,
            startFromEnd = true,
            savedChapterId = 2L,
            savedPageIndex = 3,
            savedRestoreApplied = false,
        )

        assertNull(backward.requestedPage)
    }

    @Test
    fun `an unread chapter without an explicit direction resumes on saved progress`() {
        val resumed = resolveChapterStartRequest(
            chapterId = 4L,
            chapterRead = false,
            lastPageRead = 5,
            startingAtBeginning = false,
            startFromEnd = false,
            savedChapterId = 9L,
            savedPageIndex = 5,
            savedRestoreApplied = true,
        )

        assertEquals(5, resumed.requestedPage)
    }

    @Test
    fun `a finished chapter is not resumed mid-way`() {
        val finished = resolveChapterStartRequest(
            chapterId = 4L,
            chapterRead = true,
            lastPageRead = 12,
            startingAtBeginning = false,
            startFromEnd = false,
            savedChapterId = 9L,
            savedPageIndex = 12,
            savedRestoreApplied = true,
        )

        assertNull(finished.requestedPage)
    }

    @Test
    fun `a fresh session with nothing saved opens at the beginning`() {
        val first = resolveChapterStartRequest(
            chapterId = 1L,
            chapterRead = false,
            lastPageRead = 0,
            startingAtBeginning = false,
            startFromEnd = false,
            savedChapterId = 1L,
            savedPageIndex = -1,
            savedRestoreApplied = false,
        )

        assertNull(first.requestedPage)
        assertEquals(true, first.savedRestoreResolved)
    }
}
