package ephyra.domain.chapter.interactor

import ephyra.domain.chapter.model.Chapter
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers the decision that decides whether a database chapter is rewritten on every sync.
 *
 * This is a pure predicate with no collaborators, which makes it directly testable -- unlike
 * `SyncChaptersWithSource`, whose ten injected ports would each need a fake. It is also the
 * predicate where a wrong answer costs the most: a false positive rewrites a row on every single
 * sync, forever, for a difference that does not exist.
 */
class ShouldUpdateDbChapterTest {

    private val interactor = ShouldUpdateDbChapter()

    private fun chapter(
        id: Long = 1L,
        name: String = "Chapter 12.3",
        scanlator: String? = null,
        dateUpload: Long = 0L,
        chapterNumber: Double = 12.3,
        sourceOrder: Long = 0L,
    ) = Chapter.create().copy(
        id = id,
        mangaId = 1L,
        name = name,
        scanlator = scanlator,
        dateUpload = dateUpload,
        chapterNumber = chapterNumber,
        sourceOrder = sourceOrder,
    )

    @Test
    fun `an identical chapter is not rewritten`() {
        assertFalse(interactor.await(chapter(), chapter()))
    }

    @Test
    fun `a restored chapter number does not count as a difference`() {
        // DEF-012. A backup round trip used to narrow the number through a Float field; backups now
        // store Double, so a restored row holds exactly what the source reports.
        val fromDatabase = chapter(chapterNumber = 12.3)
        val fromSource = chapter(chapterNumber = 12.3)

        assertFalse(
            interactor.await(fromDatabase, fromSource),
            "an identical number must not trigger a rewrite",
        )
    }

    @Test
    fun `a genuinely different chapter number is a rewrite`() {
        assertTrue(interactor.await(chapter(chapterNumber = 12.3), chapter(chapterNumber = 12.4)))
    }

    @Test
    fun `a renamed chapter is a rewrite`() {
        assertTrue(interactor.await(chapter(name = "Chapter 12.3"), chapter(name = "Ch. 12.3")))
    }

    @Test
    fun `a changed scanlator is a rewrite`() {
        assertTrue(
            interactor.await(chapter(scanlator = "one"), chapter(scanlator = "two")),
        )
    }

    @Test
    fun `a changed upload date is a rewrite`() {
        assertTrue(
            interactor.await(chapter(dateUpload = 1L), chapter(dateUpload = 2L)),
        )
    }

    @Test
    fun `a changed source order is a rewrite`() {
        assertTrue(
            interactor.await(chapter(sourceOrder = 0L), chapter(sourceOrder = 5L)),
        )
    }

    @Test
    fun `unrecognised numbers are not treated as equal`() {
        // A source that reports no chapter numbers must not collapse into "no change", or a real
        // rename would still be picked up by the name check but a real number change would not be.
        assertTrue(
            interactor.await(chapter(chapterNumber = -1.0), chapter(chapterNumber = -1.0)),
            "unrecognised numbers never compare equal, so the row is rewritten rather than trusted",
        )
    }
}
