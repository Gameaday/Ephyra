package ephyra.data.coil

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class BorderCropTransformationTest {

    @Test
    fun `uniform border is trimmed conservatively`() {
        val bitmap = Bitmap.createBitmap(100, 80, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        for (y in 4 until 76) {
            for (x in 4 until 96) bitmap.setPixel(x, y, Color.rgb(64, 64, 64))
        }

        assertArrayEquals(
            intArrayOf(12, 9, 88, 71),
            BorderCropTransformation.findUniformBorderBounds(bitmap),
        )
    }

    @Test
    fun `artwork with mismatched corners is not cropped`() {
        val bitmap = Bitmap.createBitmap(100, 80, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        bitmap.setPixel(99, 79, Color.BLACK)

        assertNull(BorderCropTransformation.findUniformBorderBounds(bitmap))
    }
}
