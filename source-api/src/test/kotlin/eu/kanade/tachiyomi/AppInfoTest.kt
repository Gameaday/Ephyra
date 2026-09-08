package eu.kanade.tachiyomi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppInfoTest {

    @Test
    fun `AppInfo conforms to Tachiyomi extension-lib ABI`() {
        AppInfo.init(versionCode = 42, versionName = "2.5.0")

        assertEquals(42, AppInfo.getVersionCode())
        assertEquals("2.5.0", AppInfo.getVersionName())

        val mimeTypes = AppInfo.getSupportedImageMimeTypes()
        assertTrue(mimeTypes.contains("image/jpeg"))
        assertTrue(mimeTypes.contains("image/png"))
        assertTrue(mimeTypes.contains("image/webp"))
    }
}
