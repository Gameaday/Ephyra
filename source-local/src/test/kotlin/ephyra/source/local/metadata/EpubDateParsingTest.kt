package ephyra.source.local.metadata

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * EPub metadata dates are parsed with locale-independent `java.time` parsers; the previous
 * `SimpleDateFormat(..., Locale.getDefault())` shifted the year on non-Gregorian default locales and
 * only understood a single, colon-less offset shape.
 */
class EpubDateParsingTest {

    @Test
    fun `parses an offset date with a colon`() {
        assertEquals(
            Instant.parse("2019-01-01T00:00:00Z").toEpochMilli(),
            parseEpubDate("2019-01-01T00:00:00+00:00"),
        )
    }

    @Test
    fun `parses an instant with fractional seconds`() {
        assertEquals(
            Instant.parse("2019-01-01T00:00:00.123Z").toEpochMilli(),
            parseEpubDate("2019-01-01T00:00:00.123Z"),
        )
    }

    @Test
    fun `offset-less values are read as UTC`() {
        assertEquals(
            Instant.parse("2019-01-01T00:00:00Z").toEpochMilli(),
            parseEpubDate("2019-01-01T00:00:00"),
        )
    }

    @Test
    fun `a bare calendar date is accepted as UTC midnight`() {
        assertEquals(
            LocalDate.of(2019, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            parseEpubDate("2019-01-01"),
        )
    }

    @Test
    fun `absent and malformed values are null`() {
        assertNull(parseEpubDate(null))
        assertNull(parseEpubDate(""))
        assertNull(parseEpubDate("   "))
        assertNull(parseEpubDate("sometime last year"))
    }
}
