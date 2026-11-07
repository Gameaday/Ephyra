package ephyra.feature.reader.viewer.webtoon

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import ephyra.domain.reader.gesture.ReaderGestureEffect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the host half of the continuous reader's zoom path (A4, `RDR-005`).
 *
 * The host exists because the reader's inline wiring threaded `committed = null` into
 * `WebtoonZoomState.reduceWebtoonEffect`, which made the reducer's `GestureCancelled` branch a
 * no-op in production: an abandoned swipe left the strip wherever the finger last reached, while
 * `WebtoonGestureAdapter`'s contract claimed a cancelled gesture restores the committed transform.
 * These tests pin the fix — the host holds the commit point — and the routing the inline `when`
 * blocks used to own, so neither can silently regress behind the extraction.
 */
class WebtoonZoomGestureHostTest {

    private fun state(): WebtoonZoomState = WebtoonZoomState(min = 1f, max = 4f).also {
        it.setViewportWidth(1000f)
    }

    private fun handle(
        host: WebtoonZoomGestureHost,
        effect: ReaderGestureEffect,
        onSingleTap: (Offset, Size) -> Unit = { _, _ -> },
        onLongTap: () -> Unit = {},
    ): Unit = host.handleEffect(
        effect = effect,
        itemSize = { IntSize(100, 400) },
        onSingleTap = onSingleTap,
        onLongTap = onLongTap,
    )

    @Test
    fun `a committed transform is held and a cancelled gesture restores it`() {
        val zoomState = state()
        val host = WebtoonZoomGestureHost(zoomState) {}

        // A pinch released: the transform at release becomes the restore point.
        handle(host, ReaderGestureEffect.TransformCommitted("chapter-1"))

        // A later gesture drifts the transform past the commit point, then is abandoned. With the
        // inline `committed = null` wiring this left the strip at the drifted transform — the
        // defect this host exists to close.
        zoomState.restore(scale = 3.5f, offsetX = 60f)
        handle(host, ReaderGestureEffect.GestureCancelled("chapter-1"))

        assertEquals(
            1f,
            zoomState.scale,
            0.0001f,
            "a cancelled gesture must restore the committed scale, not keep the drifted one",
        )
        assertEquals(
            0f,
            zoomState.offsetX,
            0.0001f,
            "a cancelled gesture must restore the committed offset, not keep the drifted one",
        )
    }

    @Test
    fun `a cancellation before any commit touches nothing`() {
        val zoomState = state()
        zoomState.restore(scale = 2f, offsetX = 0f)
        val host = WebtoonZoomGestureHost(zoomState) {}

        handle(host, ReaderGestureEffect.GestureCancelled("chapter-1"))

        assertEquals(
            2f,
            zoomState.scale,
            0.0001f,
            "no commit point exists yet, so nothing may be fabricated to restore",
        )
    }

    @Test
    fun `transform changes hand the scroll correction to the list owner`() {
        val zoomState = state()
        val corrections = mutableListOf<Float>()
        val host = WebtoonZoomGestureHost(zoomState) { corrections += it }

        handle(
            host,
            ReaderGestureEffect.TransformStarted(
                documentRevision = "chapter-1",
                centroidX = 500f,
                centroidY = 300f,
                panX = 0f,
                panY = 0f,
                zoomChange = 2f,
            ),
        )

        assertTrue(
            corrections.isNotEmpty(),
            "a transform effect must reach the scroll owner with its correction; got $corrections",
        )
        assertTrue(
            zoomState.scale > 1.01f,
            "a 2x request from fit must actually zoom, otherwise the correction is cosmetic; " +
                "scale was ${zoomState.scale}",
        )
    }

    @Test
    fun `taps and long presses route to the item with the item size`() {
        val zoomState = state()
        val host = WebtoonZoomGestureHost(zoomState) {}
        var tapAt: Offset? = null
        var tapContainer: Size? = null
        var longPresses = 0

        handle(
            host,
            ReaderGestureEffect.SingleTap(x = 30f, y = 40f),
            onSingleTap = { offset, container ->
                tapAt = offset
                tapContainer = container
            },
        )
        handle(host, ReaderGestureEffect.LongPress, onLongTap = { longPresses++ })

        assertEquals(Offset(30f, 40f), tapAt, "a tap must arrive with the arbiter's coordinates")
        assertEquals(Size(100f, 400f), tapContainer, "a tap must arrive with the item's laid-out size")
        assertEquals(1, longPresses, "a long press must reach the item's callback exactly once")
    }
}
