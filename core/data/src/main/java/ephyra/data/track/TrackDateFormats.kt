package ephyra.data.track

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Epoch-millis <-> `yyyy-MM-dd` conversions shared by the tracking services.
 *
 * Every tracker used to build its own `java.text.SimpleDateFormat` at the call site. That class is
 * neither thread-safe nor safe to parse with a non-Gregorian default locale, and its lenient mode
 * happily turns a malformed remote value such as `2024-02-30` into a plausible-looking date. The
 * immutable `java.time` formatters below are shareable and strict.
 *
 * Calendar dates are deliberately converted in [ZoneId.systemDefault]: the trackers only exchange
 * the *date* portion, and the value it is compared against (a chapter's upload timestamp, an
 * AniList "fuzzy" start year) is stored as wall-clock midnight in the device zone.
 */
internal object TrackDateFormats {

    /**
     * Parses an ISO `yyyy-MM-dd` calendar date.
     *
     * @return the local-midnight epoch millis, or `null` when the value is absent or malformed.
     */
    fun parseDate(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        return runCatching {
            LocalDate.parse(text.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }

    /**
     * Formats epoch millis as an ISO `yyyy-MM-dd` calendar date.
     *
     * @return the date, or `null` when the value cannot be represented.
     */
    fun formatDate(epochMillis: Long): String? = runCatching {
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    }.getOrNull()

    /** Formats an epoch *seconds* value (Kitsu's Algolia index) as an ISO `yyyy-MM-dd` date. */
    fun formatEpochSeconds(epochSeconds: Long): String? = formatDate(epochSeconds * 1000L)
}
