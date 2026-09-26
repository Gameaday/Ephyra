package ephyra.domain.reader.renderer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers the dispatch seam the four-content-type plan depends on.
 *
 * The rules that matter are the refusals. A dispatch that quietly falls back to a renderer that
 * "mostly works" is how a light novel ends up drawn to a canvas with no selection and no screen
 * reader, and how nobody notices for a release.
 */
class RendererSelectionTest {

    /** What exists today: the pager and the webtoon viewer, named after their directories. */
    private val imageRenderers = listOf(
        RendererOffer("pager", setOf(RendererCapability.PAGED_IMAGE)),
        RendererOffer("webtoon", setOf(RendererCapability.CONTINUOUS_IMAGE)),
    )

    @Test
    fun `each image kind maps to the renderer that serves it`() {
        assertEquals(
            RendererSelection.Selected(ContentKind.PAGED_IMAGE, "pager"),
            RendererDispatch.select(ContentKind.PAGED_IMAGE, imageRenderers),
        )
        assertEquals(
            RendererSelection.Selected(ContentKind.CONTINUOUS_IMAGE, "webtoon"),
            RendererDispatch.select(ContentKind.CONTINUOUS_IMAGE, imageRenderers),
        )
    }

    @Test
    fun `a text document is refused rather than drawn by an image renderer`() {
        // The central rule. Today the only renderers are image renderers, so this is the real
        // answer for a light novel: not supported yet, and it says so.
        val result = RendererDispatch.select(ContentKind.TEXT_DOCUMENT, imageRenderers)
        assertTrue(
            result is RendererSelection.Unsupported,
            "A text document must not be handed to an image renderer, but got $result",
        )
        assertEquals(ContentKind.TEXT_DOCUMENT, result.kind)
        assertEquals(setOf("pager", "webtoon"), (result as RendererSelection.Unsupported).considered)
    }

    @Test
    fun `a video entry is refused rather than opened as a document`() {
        val result = RendererDispatch.select(ContentKind.VIDEO, imageRenderers)
        assertTrue(result is RendererSelection.Unsupported, "got $result")
    }

    @Test
    fun `an image renderer cannot satisfy text even if it claims the capability loosely`() {
        // A renderer that reports PAGED_IMAGE only must not be treated as text-capable. This is
        // the near-miss the enum is meant to prevent.
        val onlyPaged = listOf(RendererOffer("pager", setOf(RendererCapability.PAGED_IMAGE)))
        assertTrue(
            RendererDispatch.select(ContentKind.TEXT_DOCUMENT, onlyPaged)
                is RendererSelection.Unsupported,
        )
    }

    @Test
    fun `a capable renderer is selected once one exists`() {
        val withText = imageRenderers +
            RendererOffer("text-reader", setOf(RendererCapability.REFLOWABLE_TEXT))
        assertEquals(
            RendererSelection.Selected(ContentKind.TEXT_DOCUMENT, "text-reader"),
            RendererDispatch.select(ContentKind.TEXT_DOCUMENT, withText),
        )
    }

    @Test
    fun `offer order decides between two capable renderers`() {
        val duplicates = listOf(
            RendererOffer("first", setOf(RendererCapability.PAGED_IMAGE)),
            RendererOffer("second", setOf(RendererCapability.PAGED_IMAGE)),
        )
        assertEquals(
            RendererSelection.Selected(ContentKind.PAGED_IMAGE, "first"),
            RendererDispatch.select(ContentKind.PAGED_IMAGE, duplicates),
            "preference order is the caller's; the policy must not reorder",
        )
    }

    @Test
    fun `an empty registry is unsupported, not a crash`() {
        val result = RendererDispatch.select(ContentKind.PAGED_IMAGE, emptyList())
        assertTrue(result is RendererSelection.Unsupported, "got $result")
        assertTrue(
            (result as RendererSelection.Unsupported).reason.contains("no renderers"),
            "the reason should say the registry was empty, was: ${result.reason}",
        )
    }

    @Test
    fun `the requirement for each kind is the one that kind actually needs`() {
        // Pinned deliberately. A future edit that quietly remaps a kind to a capability some
        // renderer happens to offer would defeat the whole point of the seam.
        assertEquals(RendererCapability.PAGED_IMAGE, RendererDispatch.requirementFor(ContentKind.PAGED_IMAGE))
        assertEquals(
            RendererCapability.CONTINUOUS_IMAGE,
            RendererDispatch.requirementFor(ContentKind.CONTINUOUS_IMAGE),
        )
        assertEquals(
            RendererCapability.REFLOWABLE_TEXT,
            RendererDispatch.requirementFor(ContentKind.TEXT_DOCUMENT),
        )
        assertEquals(RendererCapability.VIDEO_PLAYBACK, RendererDispatch.requirementFor(ContentKind.VIDEO))
    }

    @Test
    fun `a renderer with no id is rejected at construction`() {
        // An id is what the selection result names, so a blank one would make the result
        // untraceable back to a renderer.
        var threw = false
        try {
            RendererOffer("  ", setOf(RendererCapability.PAGED_IMAGE))
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw, "a blank renderer id must be rejected")
    }
}
