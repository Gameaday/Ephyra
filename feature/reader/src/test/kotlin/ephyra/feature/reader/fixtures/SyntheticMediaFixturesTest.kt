package ephyra.feature.reader.fixtures

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.size.Size
import ephyra.data.coil.BorderCropTransformation
import ephyra.feature.reader.viewer.webtoon.WebtoonSlicer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class SyntheticMediaFixturesTest {
    @Test
    fun `generated jpeg and png decode to declared dimensions`() {
        listOf(
            SyntheticMediaFixtures.staticJpeg(),
            SyntheticMediaFixtures.staticPng(),
            SyntheticMediaFixtures.staticWebp(),
        ).forEach { fixture ->
            val bitmap = BitmapFactory.decodeByteArray(fixture.bytes, 0, fixture.bytes.size)
            assertNotNull(bitmap)
            assertEquals(fixture.width, bitmap.width)
            assertEquals(fixture.height, bitmap.height)
        }
    }

    @Test
    fun `generated bordered jpeg exposes uniform crop geometry`() = runBlocking {
        val fixture = SyntheticMediaFixtures.uniformBorderedJpeg()
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(fixture.bytes, 0, fixture.bytes.size))
        val transformed = BorderCropTransformation().transform(bitmap, Size.ORIGINAL)
        assertEquals(84, transformed.width)
        assertEquals(136, transformed.height)
    }

    @Test
    fun `generated noisy artwork declines crop`() = runBlocking {
        val fixture = SyntheticMediaFixtures.noisyArtworkJpeg()
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(fixture.bytes, 0, fixture.bytes.size))
        val transformed = BorderCropTransformation().transform(bitmap, Size.ORIGINAL)
        assertSame(bitmap, transformed)
    }

    @Test
    fun `generated long dimensions produce exact contiguous slices`() {
        val slices = WebtoonSlicer.computeSlices(80, 1200, 1080, 1024)
        assertTrue(slices.isNotEmpty())
        assertEquals(0, slices.first().top)
        assertEquals(1200, slices.last().bottom)
        slices.zipWithNext().forEach { (first, second) -> assertEquals(first.bottom, second.top) }
        assertEquals(1200, slices.sumOf { it.srcHeight })
    }

    @Test
    fun `corrupt and missing fixtures remain explicit failures`() {
        assertEquals(FixtureMediaFormat.UNKNOWN, SyntheticMediaFixtures.corrupt().format)
        assertNull(MissingMediaFixture.bytes)
    }
}
