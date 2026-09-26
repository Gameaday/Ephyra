package ephyra.data.coil

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Signature detection for the JXL Coil bridge.
 *
 * This is the only part of the bridge that belongs to this project; the decode itself is
 * jxl-coder's. It is asserted at `E2` because it is pure byte inspection, which is exactly the
 * part that can be proven without a device — `TST-001C3`'s device leg covers the decode and is
 * still outstanding.
 *
 * The negative cases carry the weight. A bridge that returns null for everything is
 * indistinguishable from a working one at the UI level, so a test asserting only "valid JXL is
 * recognised" would pass against a decoder that never decodes anything.
 */
class JxlSignatureTest {

    /**
     * Builds bytes from Int literals. Kotlin does not infer `Byte` from a hex literal that exceeds
     * `Byte.MAX_VALUE` — `0x89` is an `Int` — so every signature here goes through this rather than
     * mixing `.toByte()` calls and bare literals.
     */
    private fun hex(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    /** `FF 0A` — a bare JXL codestream. */
    private val codestream = hex(0xFF, 0x0A, 0x00, 0x00, 0x00, 0x01, 0x02, 0x03)

    /** `00 00 00 0C 6A 58 4C 20 0D 0A 87 0A` — the ISOBMFF container form. */
    private val container = hex(
        0x00, 0x00, 0x00, 0x0C,
        0x6A, 0x58, 0x4C, 0x20,
        0x0D, 0x0A,
        0x87, 0x0A,
    )

    @Test
    fun `a bare codestream is recognised`() {
        assertTrue(codestream.isJxl())
    }

    @Test
    fun `an ISOBMFF container is recognised`() {
        assertTrue(container.isJxl())
    }

    @Test
    fun `a codestream longer than the signature is still recognised`() {
        // Guards an implementation that compared the whole array rather than the prefix.
        val padded = codestream + ByteArray(64) { 0x7F }
        assertTrue(padded.isJxl())
    }

    @Test
    fun `empty and single-byte input are rejected without indexing past the end`() {
        assertFalse(ByteArray(0).isJxl())
        assertFalse(hex(0xFF).isJxl())
    }

    @Test
    fun `a truncated container is rejected rather than read out of bounds`() {
        // Nine bytes: long enough to reach the box type, too short for the brand. The original
        // guard is `size < 12`, so this must not throw and must not match.
        val truncated = container.copyOfRange(0, 9)
        assertFalse(truncated.isJxl())
    }

    @Test
    fun `other image formats are rejected`() {
        // PNG, JPEG, WebP and GIF signatures. Without these, a decoder that accepted everything
        // would pass every positive assertion above. Built via `hex` so each literal is an Int
        // converted explicitly, rather than relying on Kotlin inferring a Byte from a hex literal.
        assertFalse(hex(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A).isJxl())
        assertFalse(hex(0xFF, 0xD8, 0xFF, 0xE0).isJxl())
        assertFalse(hex(0x52, 0x49, 0x46, 0x46, 0x1A, 0x45, 0x56, 0x46, 0x57, 0x45, 0x42, 0x50).isJxl())
        assertFalse(hex(0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00, 0x80, 0x00).isJxl())
    }

    @Test
    fun `a container with the wrong brand is rejected`() {
        // Identical to a real container except for the trailing brand bytes. This is the case that
        // distinguishes a real signature check from a prefix match on the box type alone.
        val wrongBrand = container.copyOf().also {
            it[10] = 0x00
            it[11] = 0x00
        }
        assertFalse(wrongBrand.isJxl())
    }

    @Test
    fun `a container with the wrong box size is rejected`() {
        val wrongSize = container.copyOf().also { it[3] = 0x0D }
        assertFalse(wrongSize.isJxl())
    }
}
