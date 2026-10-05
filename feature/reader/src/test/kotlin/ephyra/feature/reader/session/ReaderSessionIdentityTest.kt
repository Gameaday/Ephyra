package ephyra.feature.reader.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pins the mapping from reader entities to session identities.
 *
 * **Why this is worth its own test.** Every other failure in the session path announces itself. A
 * wrong identity here does not: the machine simply concludes it is looking at a different chapter,
 * and the symptom is a chapter that will not restore, or a page selection that lands on the wrong
 * index. Nothing throws, so nothing points here.
 */
class ReaderSessionIdentityTest {

    private val chapterId = ReaderSessionIdentity.chapterId("/chapter/1", sourceId = 7L)

    @Test
    fun `a chapter identity is its source and url`() {
        assertEquals(ReaderChapterId("7:/chapter/1"), chapterId)
    }

    /**
     * The property that makes the identity stable across a restore: two databases holding the same
     * chapter agree, because neither the numeric id nor anything device-local is in it.
     */
    @Test
    fun `the same chapter in another database has the same identity`() {
        val otherDatabaseId = ReaderSessionIdentity.chapterId("/chapter/1", sourceId = 7L)
        assertEquals(chapterId, otherDatabaseId)
    }

    /** Chapter URLs collide across sources, so the source has to be part of the identity. */
    @Test
    fun `the same url in two sources is two chapters`() {
        assertNotEquals(
            ReaderSessionIdentity.chapterId("/chapter/1", sourceId = 1L),
            ReaderSessionIdentity.chapterId("/chapter/1", sourceId = 2L),
        )
    }

    @Test
    fun `a page identity is scoped to its chapter`() {
        assertEquals(
            ReaderPageId("7:/chapter/1#/page/1.jpg"),
            ReaderSessionIdentity.pageId("/page/1.jpg", pageIndex = 0, chapterId = chapterId),
        )
    }

    /**
     * The same page URL in two chapters is two pages, which is why the chapter is part of a page
     * identity rather than assumed from context.
     */
    @Test
    fun `the same page url in two chapters is two pages`() {
        val other = ReaderSessionIdentity.chapterId("/chapter/2", sourceId = 7L)
        assertNotEquals(
            ReaderSessionIdentity.pageId("/page/1.jpg", 0, chapterId),
            ReaderSessionIdentity.pageId("/page/1.jpg", 0, other),
        )
    }

    /**
     * A page with no URL falls back to its position, which is what the session's `pageIds` list is
     * indexed by. The fallback therefore has to agree with that indexing, and the test below is
     * what pins the two together.
     */
    @Test
    fun `a page without a url is identified by its position`() {
        assertEquals(
            ReaderPageId("7:/chapter/1#3"),
            ReaderSessionIdentity.pageId(pageUrl = "", pageIndex = 3, chapterId = chapterId),
        )
    }

    @Test
    fun `a blank url is treated as absent rather than as an identity`() {
        assertEquals(
            ReaderSessionIdentity.pageId("", 3, chapterId),
            ReaderSessionIdentity.pageId("   ", 3, chapterId),
            "a blank url must not produce a distinct page from an absent one",
        )
    }

    @Test
    fun `page identities are listed in order`() {
        val ids = ReaderSessionIdentity.pageIds(listOf("/a.jpg", "/b.jpg", "/c.jpg"), chapterId)

        assertEquals(
            listOf(
                ReaderPageId("7:/chapter/1#/a.jpg"),
                ReaderPageId("7:/chapter/1#/b.jpg"),
                ReaderPageId("7:/chapter/1#/c.jpg"),
            ),
            ids,
        )
    }

    @Test
    fun `an index lookup resolves a page the chapter actually holds`() {
        val ids = ReaderSessionIdentity.pageIds(listOf("/a.jpg", "/b.jpg"), chapterId)

        assertEquals(0, ReaderSessionIdentity.indexOf(ids, ids[0]))
        assertEquals(1, ReaderSessionIdentity.indexOf(ids, ids[1]))
    }

    @Test
    fun `an index lookup for an unknown page is null rather than zero`() {
        val ids = ReaderSessionIdentity.pageIds(listOf("/a.jpg"), chapterId)

        assertNull(
            ReaderSessionIdentity.indexOf(ids, ReaderPageId("7:/chapter/1#/nope.jpg")),
            "index zero would be a real page, so a miss must not alias to the first one",
        )
    }

    @Test
    fun `an empty chapter has no index for anything`() {
        assertNull(ReaderSessionIdentity.indexOf(emptyList(), ReaderPageId("anything")))
    }
}
