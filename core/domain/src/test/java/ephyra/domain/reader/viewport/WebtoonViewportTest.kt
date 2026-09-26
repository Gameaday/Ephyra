package ephyra.domain.reader.viewport

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The `RDR-005` viewport-state contract.
 *
 * [DocumentViewportTest] already covers the arithmetic, so these deliberately do not re-check it.
 * What was untested is the *lifecycle*: when a transform applies, what invalidates it, and what
 * happens across a chapter boundary. That is the part a caller has to re-derive if it is missing,
 * and re-derivation is how the two readers drifted apart on zoom-lock behaviour.
 */
class WebtoonViewportTest {

    private fun document(id: String = "doc-1", height: Float = 20000f) = ReaderDocument(
        documentId = id,
        size = DocumentSize(1080f, height),
        pages = listOf(
            DocumentPage(0, "p0", DocumentRect(0f, 0f, 1080f, height / 2f)),
            DocumentPage(1, "p1", DocumentRect(0f, height / 2f, 1080f, height)),
        ),
    )

    private val size = ViewportSize(1080f, 1920f)

    private fun measured(chapter: String = "chapter-1") =
        WebtoonViewport.forChapter(chapter, document(), size)

    @Test
    fun `a new chapter starts at fit rather than inheriting a transform`() {
        val fresh = measured()
        assertTrue(fresh.isAtFit, "Precondition: a fresh chapter is at fit")
        assertEquals(1f, fresh.scale, 1e-3f)
        assertTrue(fresh.isMeasured)
    }

    @Test
    fun `a zoom changes the scale the viewport holds`() {
        val zoomed = WebtoonViewport.onZoom(measured(), 2f, focalX = 540f, focalY = 960f)
        assertEquals(
            2f,
            zoomed.scale,
            1e-3f,
            "A pinch must change the scale; this is the invariant DEF-002 depends on at cutover",
        )
    }

    @Test
    fun `zoom is anchored on the focal point, not the viewport centre`() {
        val offCentre = WebtoonViewport.onZoom(measured(), 2f, focalX = 100f, focalY = 300f)
        val atCentre = WebtoonViewport.onZoom(measured(), 2f, focalX = 540f, focalY = 960f)
        assertNotEquals(
            offCentre.viewport?.offset,
            atCentre.viewport?.offset,
            "A pinch off-centre must land differently from one at the centre. Identical offsets mean " +
                "the focal point is discarded, which is the DEF-002 defect pattern",
        )
    }

    @Test
    fun `a transform computed for another chapter is discarded`() {
        val zoomed = WebtoonViewport.onZoom(measured(), 3f, 540f, 960f)
        assertEquals(3f, zoomed.scale, 1e-3f, "Precondition: the chapter is zoomed")

        val applied = WebtoonViewport.onTransformEffect(
            state = zoomed,
            documentRevision = "chapter-2",
            factor = 0.1f,
            panX = 0f,
            panY = 0f,
            focalX = 540f,
            focalY = 960f,
        )
        assertEquals(
            3f,
            applied.scale,
            1e-3f,
            "A transform for a different chapter must be dropped, not applied. Applying it would " +
                "render the new chapter at the previous chapter's zoom and position",
        )
    }

    @Test
    fun `a transform for the same chapter is applied`() {
        val applied = WebtoonViewport.onTransformEffect(
            state = measured(),
            documentRevision = "chapter-1",
            factor = 2f,
            panX = 0f,
            panY = 0f,
            focalX = 540f,
            focalY = 960f,
        )
        assertEquals(2f, applied.scale, 1e-3f)
    }

    @Test
    fun `a chapter with no document yet refuses to transform`() {
        val empty = WebtoonViewportState("chapter-1")
        assertTrue(!empty.isMeasured)
        assertEquals(
            empty,
            WebtoonViewport.onZoom(empty, 4f, 100f, 100f),
            "Without a document there is nothing to zoom; a placeholder size would produce a focal " +
                "anchor wrong by an amount only visible on real hardware",
        )
    }

    @Test
    fun `toggling fit zooms in and back`() {
        val zoomed = WebtoonViewport.onToggleFit(measured())
        assertEquals(2f, zoomed.scale, 1e-3f)
        assertTrue(!zoomed.isAtFit, "Precondition: toggled in")
        assertTrue(WebtoonViewport.onToggleFit(zoomed).isAtFit, "Toggling again must return to fit")
    }

    @Test
    fun `a resize preserves the visible centre so rotation does not lose the reader's place`() {
        val zoomed = WebtoonViewport.onZoom(measured(), 4f, 540f, 960f)
        val rotated = WebtoonViewport.onViewportResized(zoomed, ViewportSize(1920f, 1080f))
        assertEquals(zoomed.scale, rotated.scale, 1e-3f, "A resize must not silently reset the zoom")

        val before = zoomed.viewport?.visibleRect?.centerX ?: 0f
        val after = rotated.viewport?.visibleRect?.centerX ?: 0f
        assertTrue(
            kotlin.math.abs(before - after) < 1f,
            "The visible centre should survive a resize; before $before after $after",
        )
    }

    @Test
    fun `zoom is bounded by the policy maximum`() {
        var state = measured()
        repeat(12) { state = WebtoonViewport.onZoom(state, 2f, 540f, 960f) }
        assertTrue(
            state.scale <= 8.001f,
            "Repeated pinches must stop at the declared ceiling of 8x; scale is ${state.scale}. " +
                "An unbounded transform is an unbounded decode and layout cost",
        )
    }

    @Test
    fun `two chapters do not share a transform`() {
        val a = WebtoonViewport.onZoom(measured("chapter-1"), 3f, 540f, 960f)
        val b = WebtoonViewport.forChapter("chapter-2", document("doc-2"), size)
        assertEquals(
            1f,
            b.scale,
            1e-3f,
            "Each chapter starts at fit; sharing a transform across a chapter boundary is the " +
                "cross-document corruption this type exists to prevent",
        )
        assertNotEquals(a.viewport?.offset, b.viewport?.offset)
    }
}
