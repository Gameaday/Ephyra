package ephyra.core.common.util.system

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The tall-image threshold used by [ImageUtil.splitTallImage].
 *
 * The regression this pins: the original `outHeight / outWidth > 3` used integer division, so a
 * 3.24 ratio (1080x3500) truncated to 3 and was treated as *not* tall, while the doc said "3x".
 */
class ImageUtilTallRatioTest {

    @Test
    fun `a page taller than three times its width is tall`() {
        assertTrue(ImageUtil.isTallRatio(height = 3001, width = 1000))
        assertTrue(ImageUtil.isTallRatio(height = 24000, width = 1080))
    }

    @Test
    fun `a page at or below three times its width is not tall`() {
        assertFalse(ImageUtil.isTallRatio(height = 3000, width = 1000)) // exactly 3x
        assertFalse(ImageUtil.isTallRatio(height = 1000, width = 1000)) // square
        assertFalse(ImageUtil.isTallRatio(height = 500, width = 1000)) // wide
    }

    @Test
    fun `the integer-division bug is closed`() {
        // 3500 / 1080 = 3.24: the old integer division gave 3 (not > 3 -> "not tall"); now it is tall.
        assertTrue(ImageUtil.isTallRatio(height = 3500, width = 1080))
    }

    @Test
    fun `non-positive dimensions are never tall`() {
        assertFalse(ImageUtil.isTallRatio(height = 0, width = 1000))
        assertFalse(ImageUtil.isTallRatio(height = 1000, width = 0))
        assertFalse(ImageUtil.isTallRatio(height = -1, width = 1000))
    }
}
