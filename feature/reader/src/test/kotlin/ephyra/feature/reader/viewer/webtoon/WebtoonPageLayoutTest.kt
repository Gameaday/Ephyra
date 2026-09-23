package ephyra.feature.reader.viewer.webtoon

import ephyra.feature.reader.model.ReaderPage
import eu.kanade.tachiyomi.source.model.Page
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Locks the two layout contracts that caused the shipped webtoon bugs:
 * write-once dimensions (item suddenly huge between sections) and the contentType
 * pooling split (blank/mis-measured items on fast flings).
 */
class WebtoonPageLayoutTest {

    private fun page(status: Page.State = Page.State.Queue): ReaderPage =
        ReaderPage(index = 0).also { it.status = status }

    @Test
    fun `pending pages pool with loading compositions`() {
        assertEquals("webtoon_page_pending", webtoonContentType(page(), cropBorders = false))
    }

    @Test
    fun `ready pages pool with slice column compositions`() {
        assertEquals(
            "webtoon_page_ready",
            webtoonContentType(page(Page.State.Ready), cropBorders = false),
        )
    }

    @Test
    fun `crop mode pools every page as a single image`() {
        assertEquals(
            "webtoon_single",
            webtoonContentType(page(Page.State.Ready), cropBorders = true),
        )
    }

    @Test
    fun `smart-combined merges pool as single images`() {
        val merged = page().also { it.mergedBitmap = mockk() }
        assertEquals("webtoon_single", webtoonContentType(merged, cropBorders = false))
    }

    @Test
    fun `unknown items get their own pool`() {
        assertEquals("webtoon_item", webtoonContentType(null, cropBorders = false))
    }

    @Test
    fun `recordDimensionsOnce keeps the first decoded size`() {
        val p = page()
        p.recordDimensionsOnce(800, 12000)
        // A later decode at a different size must not resize the box LazyColumn laid out.
        p.recordDimensionsOnce(1080, 16000)
        assertEquals(800, p.width)
        assertEquals(12000, p.height)
    }

    @Test
    fun `recordDimensionsOnce ignores invalid dimensions`() {
        val p = page()
        p.recordDimensionsOnce(0, 0)
        assertEquals(0, p.width)
        assertEquals(0, p.height)
        p.recordDimensionsOnce(800, 12000)
        p.recordDimensionsOnce(-1, 100)
        assertEquals(800, p.width)
        assertEquals(12000, p.height)
    }

    @Test
    fun `released pages may record fresh dimensions on revisit`() {
        val p = page()
        p.recordDimensionsOnce(800, 12000)
        p.releasePageResources()
        assertEquals(0, p.width)
        assertEquals(0, p.height)
        p.recordDimensionsOnce(1080, 16200)
        assertEquals(1080, p.width)
        assertEquals(16200, p.height)
    }
}
