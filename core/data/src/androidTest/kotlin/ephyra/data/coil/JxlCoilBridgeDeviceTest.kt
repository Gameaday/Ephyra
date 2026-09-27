package ephyra.data.coil

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.awxkee.jxlcoder.JxlChannelsConfiguration
import com.awxkee.jxlcoder.JxlCoder
import com.awxkee.jxlcoder.JxlCompressionOption
import com.awxkee.jxlcoder.JxlResizeFilter
import com.awxkee.jxlcoder.PreferredColorConfig
import com.awxkee.jxlcoder.ScaleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `TST-001C3`: a real JXL artifact, decoded on a real device, through the real bridge.
 *
 * The fixture does not need to be sourced from anywhere. The app already bundles a full JXL
 * codec (`jxl-coder`) for the decode path, and the same codec can **encode**, so the artifact
 * under test is produced by the same library that will read it back. That removes the long-standing
 * "no .jxl file exists anywhere in the tree" blocker without inventing bytes: `encode` emits a
 * genuine file, and `isJxl()` then has to recognise genuine output rather than a hand-written
 * header that merely looks plausible.
 *
 * The decode call is byte-for-byte the one `JxlCoilBridgeDecoder` makes, so this covers the part of
 * the bridge that unit tests cannot reach.
 */
@RunWith(AndroidJUnit4::class)
class JxlCoilBridgeDeviceTest {

    /**
     * Encodes losslessly.
     *
     * The default is lossy, and a first run of this test proved it: red (255) came back as 238,
     * which is correct behaviour for a lossy codec and a wrong assertion for a test. Encoding
     * losslessly is what makes exact pixel comparison meaningful here — the test is then
     * asserting that the *bridge* preserved the image, not that a compression ratio was met. A
     * separate lossy case is kept below, because "lossy output is still recognisably the right
     * colours" is worth pinning too.
     */
    private fun encodeLosslessly(bitmap: Bitmap): ByteArray = JxlCoder.encode(
        bitmap = bitmap,
        channelsConfiguration = JxlChannelsConfiguration.RGBA,
        compressionOption = JxlCompressionOption.LOSSLESS,
    )

    private fun sampleBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // Asymmetric corners, so a transpose or a flip cannot pass unnoticed.
        bitmap.eraseColor(Color.MAGENTA)
        bitmap.setPixel(0, 0, Color.RED)
        bitmap.setPixel(width - 1, 0, Color.GREEN)
        bitmap.setPixel(0, height - 1, Color.BLUE)
        bitmap.setPixel(width - 1, height - 1, Color.YELLOW)
        return bitmap
    }

    @Test
    fun anEncodedArtifactIsRecognisedAsJxl() {
        val encoded = JxlCoder.encode(sampleBitmap(64, 96))

        assertTrue(
            "encode() produced ${encoded.size} bytes that isJxl() does not recognise",
            encoded.isJxl(),
        )
    }

    @Test
    fun theEncodedArtifactDecodesBackToTheOriginalGeometry() {
        val encoded = JxlCoder.encode(sampleBitmap(64, 96))
        assertTrue(encoded.isJxl())

        val decoded = JxlCoder.decodeSampled(
            byteArray = encoded,
            width = -1,
            height = -1,
            preferredColorConfig = PreferredColorConfig.RGBA_8888,
            scaleMode = ScaleMode.FIT,
            jxlResizeFilter = JxlResizeFilter.BILINEAR,
        )

        assertEquals(64, decoded.width)
        assertEquals(96, decoded.height)
    }

    @Test
    fun theFourCornersSurviveTheRoundTrip() {
        val encoded = encodeLosslessly(sampleBitmap(64, 96))
        val decoded = JxlCoder.decodeSampled(
            byteArray = encoded,
            width = -1,
            height = -1,
            preferredColorConfig = PreferredColorConfig.RGBA_8888,
            scaleMode = ScaleMode.FIT,
            jxlResizeFilter = JxlResizeFilter.BILINEAR,
        )

        assertEquals(Color.RED, decoded.getPixel(0, 0))
        assertEquals(Color.GREEN, decoded.getPixel(63, 0))
        assertEquals(Color.BLUE, decoded.getPixel(0, 95))
        assertEquals(Color.YELLOW, decoded.getPixel(63, 95))
    }

    @Test
    fun sampledDecodeHonoursARequestedSize() {
        // The branch `JxlCoilBridgeDecoder` takes when Coil asks for a specific size, which is the
        // path a reader page actually uses.
        val encoded = JxlCoder.encode(sampleBitmap(64, 96))

        val decoded = JxlCoder.decodeSampled(
            byteArray = encoded,
            width = 32,
            height = 48,
            preferredColorConfig = PreferredColorConfig.RGBA_8888,
            scaleMode = ScaleMode.FIT,
            jxlResizeFilter = JxlResizeFilter.BILINEAR,
        )

        assertEquals(32, decoded.width)
        assertEquals(48, decoded.height)
    }

    @Test
    fun lossyOutputIsStillRecognisedAndDecodesToTheRightColours() {
        // The default encode path. Colours are approximate by design, so the assertion is that the
        // decode is *close* and in the right place -- not that it is bit-exact, which is what the
        // lossless case above is for.
        val encoded = JxlCoder.encode(sampleBitmap(64, 96))
        assertTrue(encoded.isJxl())

        val decoded = JxlCoder.decodeSampled(
            byteArray = encoded,
            width = -1,
            height = -1,
            preferredColorConfig = PreferredColorConfig.RGBA_8888,
            scaleMode = ScaleMode.FIT,
            jxlResizeFilter = JxlResizeFilter.BILINEAR,
        )

        assertEquals(64, decoded.width)
        assertEquals(96, decoded.height)

        val corner = decoded.getPixel(0, 0)
        val red = android.graphics.Color.red(corner)
        val green = android.graphics.Color.green(corner)
        val blue = android.graphics.Color.blue(corner)
        assertTrue("expected a red-dominant pixel, got rgb($red,$green,$blue)", red > 200 && green < 40 && blue < 40)
    }

    @Test
    fun aContainerFormArtifactIsRecognised() {
        // The encoder emits a *bare codestream* (`FF 0A`), proven by falsification: reverting the
        // `jXL ` box type to `JXL ` left every other test in this class green, because the bare
        // codestream branch of `isJxl()` short-circuits before the container check is reached. So
        // the container branch — the one the original defect broke — had no device coverage at
        // all until this test.
        //
        // The header is the one the specification defines: a 12-byte box whose type is `jXL `
        // (lowercase `j`, 0x6A) followed by the JXL brand. Recognition is asserted rather than a
        // decode, because a signature box followed by a raw codestream is not a container libjxl
        // will parse; asserting a decode here would be asserting something untrue.
        val containerHeader = byteArrayOf(
            0x00, 0x00, 0x00, 0x0C, // box size
            0x6A, 0x58, 0x4C, 0x20, // 'j', 'X', 'L', ' '
            0x0D, 0x0A, 0x87.toByte(), 0x0A, // JXL brand
        )

        assertTrue("a well-formed jXL container header was not recognised", containerHeader.isJxl())
    }

    @Test
    fun aContainerWithTheWrongBoxTypeIsNotRecognised() {
        // The exact defect, as a regression guard on the device rather than only at E2: `JXL ` with
        // an uppercase J is not the specified box type, and must not be accepted.
        val wrongBoxType = byteArrayOf(
            0x00, 0x00, 0x00, 0x0C,
            0x4A, 0x58, 0x4C, 0x20, // 'J' instead of 'j'
            0x0D, 0x0A, 0x87.toByte(), 0x0A,
        )

        assertTrue("a wrong box type must not be accepted", !wrongBoxType.isJxl())
    }

    @Test
    fun aRealJpegIsNotMistakenForJxl() {
        // The negative control that makes the positive assertions mean something: without it, a
        // `isJxl()` that returned true for everything would pass every test above.
        val out = java.io.ByteArrayOutputStream()
        sampleBitmap(64, 96).compress(Bitmap.CompressFormat.JPEG, 90, out)
        val bytes = out.toByteArray()

        assertTrue("premise: the JPEG was written", bytes.isNotEmpty())
        assertTrue("a JPEG must not be recognised as JXL", !bytes.isJxl())
    }
}
