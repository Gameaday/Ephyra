package ephyra.feature.reader.viewer.webtoon

import eu.kanade.tachiyomi.network.HttpException
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class WebtoonRateLimitTest {

    @Test
    fun `429 and 503 are rate limited`() {
        assertTrue(isRateLimitError(HttpException(429)))
        assertTrue(isRateLimitError(HttpException(503)))
    }

    @Test
    fun `other errors are not rate limited`() {
        assertFalse(isRateLimitError(HttpException(404)))
        assertFalse(isRateLimitError(IOException("timeout")))
        assertFalse(isRateLimitError(null))
        assertFalse(isRateLimitError(IllegalStateException("boom")))
    }
}
