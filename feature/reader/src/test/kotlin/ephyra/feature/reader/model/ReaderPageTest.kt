package ephyra.feature.reader.model

import ephyra.domain.chapter.model.Chapter
import eu.kanade.tachiyomi.source.model.Page
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue

class ReaderPageTest {

    @Test
    fun `clear loaded image drops handle payload without changing page identity`() {
        val page = ReaderPage(7, url = "https://example.test/page")
        page.resource.cachedBytes = byteArrayOf(1, 2, 3)
        page.resource.width = 800
        page.resource.height = 1200

        page.resource.clearLoadedImage()

        assertNull(page.resource.cachedBytes)
        assertEquals(0, page.resource.width)
        assertEquals(0, page.resource.height)
        assertEquals(7, page.index)
        assertEquals("https://example.test/page", page.url)
    }

    @Test
    fun `payload lives on the handle and is reachable only through it`() {
        val page = ReaderPage(3, url = "https://example.test/p3")

        assertNotNull(page.resource)
        page.resource.cachedBytes = byteArrayOf(9)
        page.resource.width = 640
        page.resource.height = 480

        assertSame(page.resource.cachedBytes, page.resource.cachedBytes)
        assertEquals(640, page.resource.width)
        assertEquals(480, page.resource.height)
    }

    @Test
    fun `the handle is created with the page and never replaced`() {
        val page = ReaderPage(1, url = "https://example.test/p1")
        val handleAtCreation = page.resource

        // Reading the property repeatedly must yield the same handle for the page's whole life.
        assertSame(handleAtCreation, page.resource)

        val supplied = PageResource()
        val pageWithSuppliedHandle = ReaderPage(2, url = "https://example.test/p2", resource = supplied)
        assertSame(supplied, pageWithSuppliedHandle.resource)
    }

    @Test
    fun `insert page shares the parent handle and reports ready`() {
        val parent = ReaderPage(5, url = "https://example.test/p5").apply {
            // InsertPage records the parent's chapter at construction (unchanged from the
            // pre-handle code), so the parent must be attached to one first — as it always
            // is in production, where the pager splices inserts into a loaded chapter.
            chapter = ReaderChapter(Chapter.create().copy(id = 5L, mangaId = 1L, name = "Ch 5"))
        }
        parent.resource.cachedBytes = byteArrayOf(4, 5)

        val insert = InsertPage(parent)

        // The insert renders exactly what the parent renders: one handle, not a copy.
        assertSame(parent.resource, insert.resource)
        assertSame(parent.resource.cachedBytes, insert.resource.cachedBytes)
        assertEquals(parent.index, insert.index)
        assertEquals(parent.url, insert.url)
        assertEquals(Page.State.Ready, insert.status)
    }

    @Test
    fun `hidden flags are navigation metadata, not payload`() {
        val plain = ReaderPage(0, url = "https://example.test/p0")
        assertFalse(plain.isHidden)

        val absorbed = ReaderPage(0, url = "https://example.test/p0")
        absorbed.isAbsorbed = true
        assertTrue(absorbed.isHidden)

        val blocked = ReaderPage(0, url = "https://example.test/p0")
        blocked.isBlockedByFilter = true
        assertTrue(blocked.isHidden)
    }

    @Test
    fun `aspect ratio is derived from the handle's recorded dimensions`() {
        val page = ReaderPage(2, url = "https://example.test/p2")
        assertNull(page.aspectRatio)

        page.resource.width = 1000
        page.resource.height = 500
        assertEquals(2f, page.aspectRatio!!)
    }
}
