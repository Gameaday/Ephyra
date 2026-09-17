package ephyra.presentation.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Covers the transition-arrow mapping.
 *
 * The bug this guards against was an arrow that always pointed down/forward, including when
 * the reader was moving *backwards* into a previous chapter. Horizontal cases resolve to
 * AutoMirrored glyphs (mirrored by the platform for the reader's own flow), vertical cases to
 * up/down, which are never mirrored.
 */
class ChapterTransitionGlyphTest {

    @Test
    fun `forward navigation in a horizontal reader uses the forward glyph`() {
        assertEquals(
            TransitionArrowGlyph.HORIZONTAL_NEXT,
            transitionArrowGlyph(isNext = true, direction = TransitionDirection.LTR),
        )
    }

    @Test
    fun `backward navigation in a horizontal reader uses the back glyph`() {
        assertEquals(
            TransitionArrowGlyph.HORIZONTAL_PREVIOUS,
            transitionArrowGlyph(isNext = false, direction = TransitionDirection.LTR),
        )
    }

    @Test
    fun `right-to-left readers still use the AutoMirrored horizontal glyphs`() {
        // R2L is handled by pointing the AutoMirrored glyphs the other way via layout
        // direction, not by swapping the glyph itself.
        assertEquals(
            TransitionArrowGlyph.HORIZONTAL_NEXT,
            transitionArrowGlyph(isNext = true, direction = TransitionDirection.RTL),
        )
        assertEquals(
            TransitionArrowGlyph.HORIZONTAL_PREVIOUS,
            transitionArrowGlyph(isNext = false, direction = TransitionDirection.RTL),
        )
    }

    @Test
    fun `vertical readers point down when going forward and up when going back`() {
        assertEquals(
            TransitionArrowGlyph.VERTICAL_NEXT,
            transitionArrowGlyph(isNext = true, direction = TransitionDirection.VERTICAL),
        )
        assertEquals(
            TransitionArrowGlyph.VERTICAL_PREVIOUS,
            transitionArrowGlyph(isNext = false, direction = TransitionDirection.VERTICAL),
        )
    }

    @Test
    fun `vertical readers never point downward for a backward transition`() {
        val backward = transitionArrowGlyph(isNext = false, direction = TransitionDirection.VERTICAL)

        assertNotEquals(TransitionArrowGlyph.VERTICAL_NEXT, backward)
        assertEquals(TransitionArrowGlyph.VERTICAL_PREVIOUS, backward)
    }

    @Test
    fun `the return affordance is always the opposite of the navigation affordance`() {
        // "Return to current chapter" inverts isNext, so a forward transition's return arrow
        // points back (up in a vertical reader), never further forward.
        assertEquals(
            TransitionArrowGlyph.VERTICAL_PREVIOUS,
            transitionArrowGlyph(isNext = false, direction = TransitionDirection.VERTICAL),
        )
        assertEquals(
            TransitionArrowGlyph.HORIZONTAL_PREVIOUS,
            transitionArrowGlyph(isNext = false, direction = TransitionDirection.LTR),
        )
    }
}
