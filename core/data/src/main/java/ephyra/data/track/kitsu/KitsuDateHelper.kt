package ephyra.data.track.kitsu

import ephyra.core.common.util.system.logcat
import logcat.LogPriority
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Kitsu exchanges instants as ISO-8601 UTC strings with millisecond precision, e.g.
 * `2018-04-27T00:00:00.000Z`.
 *
 * Both directions are pinned to [ZoneOffset.UTC]. The previous implementation shared a single
 * `java.text.SimpleDateFormat` with a *quoted* `'Z'`, which was wrong twice over: that class is not
 * thread-safe, while a tracked manga list is parsed entry by entry, and the quoted literal made the
 * UTC value read as local time, shifting every date by the device's UTC offset. `java.time`
 * formatters are immutable, so one shared instance is safe.
 */
object KitsuDateHelper {

    /** Canonical Kitsu wire format: instant with milliseconds and a literal `Z` suffix. */
    private val canonicalDate: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC)

    /**
     * Parsers tried in order. Kitsu occasionally omits the milliseconds or sends an explicit
     * `+00:00` offset instead of `Z`; a single strict parser would drop those entries.
     */
    private val parsers: List<DateTimeFormatter> = listOf(
        canonicalDate,
        DateTimeFormatter.ISO_INSTANT,
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
    )

    /** @return the date as a Kitsu UTC timestamp, or `null` when the date is unset. */
    fun convert(dateValue: Long): String? {
        if (dateValue == 0L) return null

        return canonicalDate.format(Instant.ofEpochMilli(dateValue))
    }

    /**
     * @return the epoch millis of the given Kitsu timestamp, or `0` when it is absent or
     *   unparseable. `0` is the trackers' "no date" sentinel, so a malformed value must never abort
     *   the surrounding sync.
     */
    fun parse(dateString: String?): Long {
        if (dateString.isNullOrBlank()) return 0L

        val text = dateString.trim()
        var lastFailure: DateTimeParseException? = null
        for (parser in parsers) {
            try {
                return parser.parse(text, Instant::from).toEpochMilli()
            } catch (e: DateTimeParseException) {
                lastFailure = e
            }
        }

        logcat(LogPriority.WARN, lastFailure) { "Unparseable Kitsu date '$text'; treating as unset" }
        return 0L
    }
}
