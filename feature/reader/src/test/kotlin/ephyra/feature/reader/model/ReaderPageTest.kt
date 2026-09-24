package ephyra.feature.reader.model

import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull

class ReaderPageTest {

    @Test
    fun `clear loaded image drops bytes and dimensions without changing page identity`() {
        val page = ReaderPage(7, url = "https://example.test/page")
        page.cachedBytes = byteArrayOf(1, 2, 3)
        page.width = 800
        page.height = 1200

        page.clearLoadedImage()

        assertNull(page.cachedBytes)
        assertEquals(0, page.width)
        assertEquals(0, page.height)
        assertEquals(7, page.index)
        assertEquals("https://example.test/page", page.url)
    }
}
