package ephyra.feature.reader.viewer.webtoon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WebtoonRegionDecoderTest {

    @Test
    fun `sample size covers target without waste`() {
        assertEquals(1, WebtoonRegionDecoder.sampleSizeForWidth(800, 1080))
        assertEquals(1, WebtoonRegionDecoder.sampleSizeForWidth(1600, 1080))
        assertEquals(2, WebtoonRegionDecoder.sampleSizeForWidth(4000, 1080))
        assertEquals(2, WebtoonRegionDecoder.sampleSizeForWidth(2160, 1080))
        assertEquals(4, WebtoonRegionDecoder.sampleSizeForWidth(5000, 1080))
    }

    @Test
    fun `sample size never drops below one`() {
        assertEquals(1, WebtoonRegionDecoder.sampleSizeForWidth(0, 1080))
        assertEquals(1, WebtoonRegionDecoder.sampleSizeForWidth(800, 0))
    }
}
