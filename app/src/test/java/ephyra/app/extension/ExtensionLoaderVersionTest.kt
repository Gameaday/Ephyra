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
        assertTrue(ExtensionLoader.isLibVersionSupported(1.4))
        assertTrue(ExtensionLoader.isLibVersionSupported(1.5))
        assertTrue(ExtensionLoader.isLibVersionSupported(1.6))
    }

    @Test
    fun `isLibVersionSupported accepts float-to-double converted versions with IEEE-754 mantissa noise`() {
        // In Android manifests, numeric metadata values are read as 32-bit floats.
        // Direct Float.toDouble() results in binary precision artifacts:
        // 1.6f.toDouble() == 1.600000023841858
        // 1.4f.toDouble() == 1.399999976158142
        val float16AsDouble = 1.6f.toDouble()
        val float14AsDouble = 1.4f.toDouble()
        val float15AsDouble = 1.5f.toDouble()

        assertTrue(ExtensionLoader.isLibVersionSupported(float16AsDouble))
        assertTrue(ExtensionLoader.isLibVersionSupported(float14AsDouble))
        assertTrue(ExtensionLoader.isLibVersionSupported(float15AsDouble))
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

        val rawVersion14 = 1.4f.toDouble()
        val normalized14 = ExtensionLoader.SUPPORTED_LIB_VERSIONS.firstOrNull { abs(it - rawVersion14) < 0.001 }
            ?: (round(rawVersion14 * 100.0) / 100.0)
        assertEquals(1.4, normalized14)
    }
}
