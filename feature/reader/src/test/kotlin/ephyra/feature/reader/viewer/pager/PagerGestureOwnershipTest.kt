package ephyra.feature.reader.viewer.pager

import androidx.compose.ui.geometry.Offset
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PagerGestureOwnershipTest {

    @Test
    fun `second pointer claims pinch immediately`() {
        assertTrue(shouldClaimPagerTransform(2, Offset.Zero, canPan = false, touchSlop = 20f))
    }

    @Test
    fun `single pointer remains with pager at fit`() {
        assertFalse(shouldClaimPagerTransform(1, Offset(100f, 0f), canPan = false, touchSlop = 20f))
    }

    @Test
    fun `single pointer pans only after slop while zoomed`() {
        assertFalse(shouldClaimPagerTransform(1, Offset(10f, 0f), canPan = true, touchSlop = 20f))
        assertTrue(shouldClaimPagerTransform(1, Offset(25f, 0f), canPan = true, touchSlop = 20f))
    }
}
