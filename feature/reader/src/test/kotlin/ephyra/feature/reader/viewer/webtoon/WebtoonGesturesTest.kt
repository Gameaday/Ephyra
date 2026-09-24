package ephyra.feature.reader.viewer.webtoon

import androidx.compose.ui.geometry.Offset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebtoonGesturesTest {

    @Test
    fun `zoom clamp honors min and max`() {
        assertEquals(1f, coerceWebtoonZoom(1f, 0.5f, 4f))
        assertEquals(0.5f, coerceWebtoonZoom(0.1f, 0.5f, 4f))
        assertEquals(4f, coerceWebtoonZoom(9f, 0.5f, 4f))
        // Zoom-out disabled: floor is 1x.
        assertEquals(1f, coerceWebtoonZoom(0.5f, 1f, 4f))
    }

    @Test
    fun `webtoon claims only dominant horizontal pan while zoomed`() {
        val slop = 20f
        assertTrue(
            shouldClaimWebtoonHorizontalPan(Offset(25f, 10f), locksInteraction = true, touchSlop = slop),
        )
        assertFalse(
            shouldClaimWebtoonHorizontalPan(Offset(10f, 25f), locksInteraction = true, touchSlop = slop),
        )
        assertFalse(
            shouldClaimWebtoonHorizontalPan(Offset(25f, 10f), locksInteraction = false, touchSlop = slop),
        )
    }

    @Test
    fun `double tap needs fast second tap near the first`() {
        val slop = 20f
        assertTrue(
            isWebtoonDoubleTap(
                now = 1000L,
                lastTapTime = 800L,
                pos = Offset(100f, 100f),
                lastPos = Offset(105f, 105f),
                touchSlop = slop,
            ),
        )
        // Too slow.
        assertFalse(
            isWebtoonDoubleTap(
                now = 2000L,
                lastTapTime = 800L,
                pos = Offset(100f, 100f),
                lastPos = Offset(100f, 100f),
                touchSlop = slop,
            ),
        )
        // Too far.
        assertFalse(
            isWebtoonDoubleTap(
                now = 1000L,
                lastTapTime = 800L,
                pos = Offset(500f, 500f),
                lastPos = Offset(100f, 100f),
                touchSlop = slop,
            ),
        )
    }
}
