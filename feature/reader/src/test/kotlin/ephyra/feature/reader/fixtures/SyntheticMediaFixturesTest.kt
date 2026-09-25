package ephyra.feature.reader.fixtures

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.size.Size
import ephyra.core.common.util.system.ImageUtil
import ephyra.data.coil.BorderCropTransformation
import ephyra.feature.reader.viewer.webtoon.WebtoonSlicer
import kotlinx.coroutines.runBlocking
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.security.MessageDigest

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
    fun `immutable animated artifacts keep their reviewed binary identities`() {
        assertEquals(
            "9068dd9f3efaef51c405bdc95ad9e6a67cd36479cce7d8f55222f37ba5ae7948",
            SyntheticMediaFixtures.animatedGif().bytes.sha256(),
        )
        assertEquals(
            "70ec1c170901eefe20f9d31d9c194694ba353ea0e3ce9243515605d3c29d5963",
            SyntheticMediaFixtures.animatedWebp().bytes.sha256(),
        )
    }

    @Test
    fun `immutable animated fixtures are recognized by app policy and Android decoder`() {
        listOf(
            SyntheticMediaFixtures.animatedGif(),
            SyntheticMediaFixtures.animatedWebp(),
        ).forEach { fixture ->
            val source = Buffer().write(fixture.bytes)
            assertTrue(ImageUtil.isAnimatedAndSupported(source))
            val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(fixture.bytes)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            assertTrue(drawable is AnimatedImageDrawable)
            assertEquals(32, drawable.intrinsicWidth)
            assertEquals(32, drawable.intrinsicHeight)
        }
    }

    @Test
    fun `animated webp carries animation metadata`() {
        val fixture = SyntheticMediaFixtures.animatedWebp()
        val source = Buffer().write(fixture.bytes)
        assertTrue(ImageUtil.isAnimatedAndSupported(source))
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

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
}
