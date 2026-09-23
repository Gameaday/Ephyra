package ephyra.feature.reader.viewer.webtoon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WebtoonVisibilityTest {

    @Test
    fun `watchdog grace is positive and sub-second`() {
        assertEquals(800L, WebtoonVisibility.WATCHDOG_GRACE_MS)
    }
}
