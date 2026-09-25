package ephyra.domain.reader.viewport

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Locks the coordinate contract that the previous per-item webtoon layout violated: one document
 * space, one transform, focal zoom that does not drift, clamped bounds, and gap-free tiling.
 */
class DocumentViewportTest {
    private val viewport = ViewportSize(width = 1080f, height = 2400f)

    private fun document(
        pageCount: Int = 10,
        pageWidth: Float = 1080f,
        pageHeight: Float = 4000f,
    ): ReaderDocument {
        val pages = (0 until pageCount).map { index ->
            DocumentPage(
                index = index,
                pageId = "page-$index",
                rect = DocumentRect(
                    left = 0f,
                    top = index * pageHeight,
                    right = pageWidth,
                    bottom = (index + 1) * pageHeight,
                ),
            )
        }
        return ReaderDocument(
            documentId = "chapter-1",
            size = DocumentSize(width = pageWidth, height = pageHeight * pageCount),
            pages = pages,
        )
    }

    @Test
    fun `viewport starts at the document origin and fits`() {
        val viewportState = DocumentViewport(document(), viewport)
        assertEquals(0f, viewportState.offset.x, 1e-4f)
        assertEquals(0f, viewportState.offset.y, 1e-4f)
        assertEquals(1f, viewportState.scale, 1e-4f)
        assertEquals(viewport.width / document().size.width, viewportState.fitScale, 1e-4f)
    }

    @Test
    fun `pan and zoom share one transform`() {
        val state = DocumentViewport(document(), viewport)
            .zoomBy(2f, DocumentPoint(540f, 1200f))
            .panByViewDelta(100f, -200f)

        val viewPoint = DocumentPoint(300f, 900f)
        val documentPoint = state.viewToDocument(viewPoint)
        val roundTrip = state.documentToView(documentPoint)
        assertEquals(viewPoint.x, roundTrip.x, 1e-3f)
        assertEquals(viewPoint.y, roundTrip.y, 1e-3f)
    }

    @Test
    fun `focal zoom keeps the document point under the centroid stable`() {
        val state = DocumentViewport(document(), viewport)
            .zoomBy(2f, DocumentPoint(540f, 1200f))
            .zoomBy(1.7f, DocumentPoint(200f, 800f))
            .zoomBy(0.6f, DocumentPoint(900f, 300f))

        val focal = DocumentPoint(640f, 1500f)
        val before = state.viewToDocument(focal)
        val after = state.zoomBy(2.5f, focal).viewToDocument(focal)
        assertEquals(before.x, after.x, 1e-2f)
        assertEquals(before.y, after.y, 1e-2f)
    }

    @Test
    fun `zoom never changes document geometry`() {
        val doc = document()
        val zoomed = DocumentViewport(doc, viewport).zoomBy(4f, DocumentPoint(500f, 500f))
        assertEquals(doc.size.width, zoomed.document.size.width, 0f)
        assertEquals(doc.size.height, zoomed.document.size.height, 0f)
        assertEquals(doc.pages.size, zoomed.document.pages.size)
    }

    @Test
    fun `offset is clamped inside the document at every scale`() {
        val state = DocumentViewport(document(pageCount = 3), viewport)
            .zoomBy(4f, DocumentPoint(540f, 1200f))
            .panByViewDelta(100000f, 100000f)
            .panByViewDelta(-100000f, -100000f)

        assertTrue(state.offset.x >= 0f)
        assertTrue(state.offset.y >= 0f)
        assertTrue(state.offset.x + viewport.width / state.scale <= document(pageCount = 3).size.width + 1e-3f)
        assertTrue(state.offset.y + viewport.height / state.scale <= document(pageCount = 3).size.height + 1e-3f)
    }

    @Test
    fun `scale is clamped to declared bounds`() {
        val state = DocumentViewport(document(), viewport)
        assertEquals(state.maxScale, state.zoomBy(1000f, DocumentPoint(0f, 0f)).scale, 1e-4f)
        assertEquals(state.minScale, state.zoomBy(0.0001f, DocumentPoint(0f, 0f)).scale, 1e-4f)
    }

    @Test
    fun `scrolling after zoom stays continuous`() {
        val state = DocumentViewport(document(), viewport)
            .zoomBy(3f, DocumentPoint(540f, 1200f))
        val before = state.visibleRect.top
        val after = state.panByViewDelta(0f, viewport.height / 2f).visibleRect.top
        assertTrue(after > before)
        assertEquals(viewport.height / state.scale / 2f, after - before, 1e-2f)
    }

    @Test
    fun `resize preserves the visible centre`() {
        val state = DocumentViewport(document(), viewport)
            .zoomBy(2f, DocumentPoint(540f, 1200f))
            .panByViewDelta(0f, 400f)
        val resized = state.resizedTo(ViewportSize(width = 1440f, height = 3200f))
        assertEquals(state.visibleRect.centerX, resized.visibleRect.centerX, 1e-1f)
        assertEquals(state.visibleRect.centerY, resized.visibleRect.centerY, 1e-1f)
    }

    @Test
    fun `prefetch stays inside the document and expands the visible rect`() {
        val state = DocumentViewport(document(), viewport).zoomBy(2f, DocumentPoint(540f, 1200f))
        val visible = state.visibleRect
        val prefetch = state.prefetchRect(2f)
        assertTrue(prefetch.width >= visible.width)
        assertTrue(prefetch.height >= visible.height)
        assertTrue(prefetch.left >= 0f)
        assertTrue(prefetch.top >= 0f)
        assertTrue(prefetch.right <= state.document.size.width + 1e-3f)
        assertTrue(prefetch.bottom <= state.document.size.height + 1e-3f)
    }

    @Test
    fun `adjacent page boundaries are continuous with no gap or overlap`() {
        val doc = document(pageCount = 4)
        for (i in 0 until doc.pages.size - 1) {
            val current = doc.pages[i]
            val next = doc.pages[i + 1]
            assertEquals(current.rect.bottom, next.rect.top, 1e-4f)
            assertFalse(current.rect.intersects(next.rect))
        }
    }

    @Test
    fun `visible pages follow the viewport and stay in document order`() {
        val state = DocumentViewport(document(), viewport).panByViewDelta(0f, 9000f)
        val visible = state.visiblePages()
        assertTrue(visible.isNotEmpty())
        assertEquals(visible.sortedBy { it.index }, visible)
    }
}
