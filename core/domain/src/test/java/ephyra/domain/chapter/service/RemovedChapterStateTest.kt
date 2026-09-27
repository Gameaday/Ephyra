package ephyra.domain.chapter.service

import ephyra.domain.chapter.model.Chapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Covers the `DEF-014` decision at the point it is made, rather than only at the rule it depends on.
 *
 * The interactor that calls this has ten injected ports and no test source set, so the decision
 * itself is what carries the evidence. The two sides of every comparison here arrive by *different
 * routes*: the removed chapter's number comes from the source, the candidate's from the database,
 * and a restored database number is `Float`-widened. That asymmetry is the whole defect.
 */
class RemovedChapterStateTest {

    private fun chapter(
        url: String,
        number: Double,
        read: Boolean = false,
        bookmark: Boolean = false,
        dateFetch: Long = 0L,
    ) = Chapter.create().copy(
        id = url.hashCode().toLong(),
        mangaId = 1L,
        url = url,
        name = "Chapter $number",
        chapterNumber = number,
        read = read,
        bookmark = bookmark,
        dateFetch = dateFetch,
    )

    @Test
    fun `a replacement inherits the state of the chapter it replaced`() {
        val removed = listOf(chapter("/old", 12.3, read = true, bookmark = true, dateFetch = 500L))
        val state = RemovedChapterState.from(removed)

        val inherited = RemovedChapterState.inherit(chapter("/new", 12.3), state)!!

        assertEquals(true, inherited.read)
        assertEquals(true, inherited.bookmark)
        assertEquals(500L, inherited.dateFetch)
    }

    @Test
    fun `a restored candidate inherits from a source-side removed chapter`() {
        // The exact `DEF-014` failure. The removed chapter's number arrives fresh from the source;
        // the candidate's comes from the database and has been through a `Float` backup field.
        val removed = listOf(chapter("/old", 12.3, read = true, bookmark = true, dateFetch = 500L))
        val state = RemovedChapterState.from(removed)
        val restoredCandidate = chapter("/new", 12.3.toFloat().toDouble())

        val inherited = RemovedChapterState.inherit(restoredCandidate, state)

        // A null here is the `DEF-014` defect: the widened candidate failed to match its twin.
        assertNotNull(inherited, "a Float-widened candidate must still match its source-side twin")
        val matched = inherited!!
        assertEquals(true, matched.read)
        assertEquals(500L, matched.dateFetch)
    }

    @Test
    fun `a candidate with a different number inherits nothing`() {
        val state = RemovedChapterState.from(listOf(chapter("/old", 12.3, read = true)))

        assertNull(RemovedChapterState.inherit(chapter("/new", 12.4), state))
    }

    @Test
    fun `an unread removed chapter resets the replacement to unread`() {
        // Inheritance must not be sticky: a replacement of something the user never read is unread.
        val state = RemovedChapterState.from(listOf(chapter("/old", 12.3, read = false, dateFetch = 7L)))

        val inherited = RemovedChapterState.inherit(chapter("/new", 12.3), state)!!

        assertEquals(false, inherited.read)
    }

    @Test
    fun `an unrecognised candidate inherits nothing`() {
        // Mirrors `sameChapterNumber`: a source reporting no numbers must not have its chapters
        // silently adopted from unrelated ones.
        val state = RemovedChapterState.from(listOf(chapter("/old", -1.0, read = true)))

        assertNull(RemovedChapterState.inherit(chapter("/new", -1.0), state))
    }

    @Test
    fun `the most recently fetched removed chapter wins a shared number`() {
        val state = RemovedChapterState.from(
            listOf(
                chapter("/old-a", 12.3, read = true, dateFetch = 100L),
                chapter("/old-b", 12.3, read = false, dateFetch = 900L),
            ),
        )

        val inherited = RemovedChapterState.inherit(chapter("/new", 12.3), state)!!

        assertEquals(900L, inherited.dateFetch)
        assertEquals(false, inherited.read, "the later chapter is the one that was actually current")
    }

    @Test
    fun `no removed chapters means nothing is inherited`() {
        assertNull(RemovedChapterState.inherit(chapter("/new", 12.3), RemovedChapterState.from(emptyList())))
    }
}
