package ephyra.app.extension

import ephyra.app.extension.util.ExtensionLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.round

class ExtensionLoaderVersionTest {

    @Test
    fun `isLibVersionSupported accepts exact double versions`() {
        assertTrue(ExtensionLoader.isLibVersionSupported(1.6))
    }

    /**
     * The supported set is 1.6 alone, and that is a decision rather than an omission.
     *
     * Carrying 1.4 and 1.5 meant carrying their deprecated surface — `fetchImageUrl`,
     * `imageUrlRequest`, `imageUrlParse`, the `Observable` catalogue methods — and that surface is
     * where every reported extension failure came from. Upstream `tachiyomix` removes the same
     * methods "with no replacement" in `[Unreleased]`, so no extension in circulation uses them.
     *
     * **The gate.** Widen this list only alongside the source-api work that implements the new
     * generation, per `doc/EXTENSION_COMPATIBILITY.md`. An extension declaring an unsupported version
     * is refused at load time, which is the better failure: it reports one clear reason instead of
     * failing per page in ways that read as image-pipeline bugs.
     */
    @Test
    fun `only 1_6 is supported`() {
        assertEquals(listOf(1.6), ExtensionLoader.SUPPORTED_LIB_VERSIONS)
        assertEquals(1.6, ExtensionLoader.LIB_VERSION_MIN)
        assertEquals(1.6, ExtensionLoader.LIB_VERSION_MAX)

        // Named explicitly, because "the list is what it is today" is how a list quietly regrows.
        assertFalse(
            ExtensionLoader.isLibVersionSupported(1.4),
            "1.4 is refused deliberately — see doc/EXTENSION_COMPATIBILITY.md",
        )
        assertFalse(
            ExtensionLoader.isLibVersionSupported(1.5),
            "1.5 is refused deliberately — see doc/EXTENSION_COMPATIBILITY.md",
        )
    }

    @Test
    fun `isLibVersionSupported accepts float-to-double converted versions with IEEE-754 mantissa noise`() {
        // In Android manifests, numeric metadata values are read as 32-bit floats.
        // Direct Float.toDouble() results in binary precision artifacts:
        // 1.6f.toDouble() == 1.600000023841858
        val float16AsDouble = 1.6f.toDouble()
        val float14AsDouble = 1.4f.toDouble()
        val float15AsDouble = 1.5f.toDouble()

        // 1.6 is supported despite the artifact; 1.4 and 1.5 are refused *despite* matching a float
        // equally well, which is the point — the tolerance is about representation, not support.
        assertTrue(ExtensionLoader.isLibVersionSupported(float16AsDouble))
        assertFalse(ExtensionLoader.isLibVersionSupported(float14AsDouble))
        assertFalse(ExtensionLoader.isLibVersionSupported(float15AsDouble))
    }

    @Test
    fun `isLibVersionSupported rejects unsupported versions`() {
        assertFalse(ExtensionLoader.isLibVersionSupported(null))
        assertFalse(ExtensionLoader.isLibVersionSupported(1.0))
        assertFalse(ExtensionLoader.isLibVersionSupported(1.2))
        assertFalse(ExtensionLoader.isLibVersionSupported(1.3))
        assertFalse(ExtensionLoader.isLibVersionSupported(1.7))
        assertFalse(ExtensionLoader.isLibVersionSupported(2.0))
        assertFalse(ExtensionLoader.isLibVersionSupported(-1.0))
    }

    @Test
    fun `canonical version normalization maps IEEE-754 artifacts to exact supported double`() {
        val rawVersion16 = 1.6f.toDouble()
        val normalized16 = ExtensionLoader.SUPPORTED_LIB_VERSIONS.firstOrNull { abs(it - rawVersion16) < 0.001 }
            ?: (round(rawVersion16 * 100.0) / 100.0)
        assertEquals(1.6, normalized16)

        // A 1.4-era APK still normalises cleanly — it just lands on a version that is no longer
        // supported, so the loader refuses it with UNSUPPORTED_LIB_VERSION rather than failing later.
        val rawVersion14 = 1.4f.toDouble()
        val normalized14 = ExtensionLoader.SUPPORTED_LIB_VERSIONS.firstOrNull { abs(it - rawVersion14) < 0.001 }
            ?: (round(rawVersion14 * 100.0) / 100.0)
        assertEquals(1.4, normalized14)
        assertFalse(ExtensionLoader.isLibVersionSupported(normalized14))
    }
}
