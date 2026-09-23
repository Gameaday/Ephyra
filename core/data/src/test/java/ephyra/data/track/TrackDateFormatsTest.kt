package ephyra.data.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TrackDateFormatsTest {

    private fun localMidnight(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `formats epoch millis as an ISO calendar date`() {
        assertEquals("2024-02-29", TrackDateFormats.formatDate(localMidnight(2024, 2, 29)))
        assertEquals("2024-01-01", TrackDateFormats.formatDate(localMidnight(2024, 1, 1)))
    }

    @Test
    fun `parses a calendar date back to the value it was formatted from`() {
        val parsed = TrackDateFormats.parseDate("2024-02-29")

        assertEquals(localMidnight(2024, 2, 29), parsed)
        assertEquals("2024-02-29", TrackDateFormats.formatDate(parsed!!))
    }

    @Test
    fun `absent values are null rather than zero`() {
        assertNull(TrackDateFormats.parseDate(null))
        assertNull(TrackDateFormats.parseDate(""))
        assertNull(TrackDateFormats.parseDate("   "))
    }

    @Test
    fun `malformed values are rejected instead of guessed`() {
        // SimpleDateFormat's lenient mode used to roll 2024-02-30 over into March 1st.
        assertNull(TrackDateFormats.parseDate("2024-02-30"))
        assertNull(TrackDateFormats.parseDate("not-a-date"))
        assertNull(TrackDateFormats.parseDate("01/02/2024"))
    }

    @Test
    fun `epoch seconds go through the same formatting`() {
        assertEquals(
            TrackDateFormats.formatDate(localMidnight(2024, 1, 1)),
            TrackDateFormats.formatEpochSeconds(localMidnight(2024, 1, 1) / 1000L),
        )
    }
}
