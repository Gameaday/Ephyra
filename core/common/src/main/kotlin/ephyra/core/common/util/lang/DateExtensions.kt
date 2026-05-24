package ephyra.core.common.util.lang

import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlin.math.absoluteValue

/**
 * Format a [LocalDateTime] using the given [dateTimeFormatter] for the date portion
 * and a localized short formatter for the time portion.
 */
fun LocalDateTime.toDateTimestampString(dateTimeFormatter: DateTimeFormatter): String {
    val date = dateTimeFormatter.format(this)
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(this)
    return "$date $time"
}

/**
 * Format a [Date] as a short time string (e.g. "3:45 PM").
 */
fun Date.toTimestampString(): String {
    return DateFormat.getTimeInstance(DateFormat.SHORT).format(this)
}

/**
 * Convert an epoch-millis timestamp from one time zone to another.
 */
fun Long.convertEpochMillisZone(
    from: ZoneId,
    to: ZoneId,
): Long {
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(this), from)
        .atZone(to)
        .toInstant()
        .toEpochMilli()
}

/**
 * Convert an epoch-millis timestamp to a [LocalDate] in the system default time zone.
 */
fun Long.toLocalDate(): LocalDate {
    return LocalDate.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())
}

/**
 * Convert an [Instant] to a [LocalDate] in the given (or system default) time zone.
 */
fun Instant.toLocalDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate {
    return LocalDate.ofInstant(this, zoneId)
}

/**
 * Render a [LocalDate] as a relative string (e.g. "2 days ago").
 *
 * This function requires [android.content.Context] and is defined in
 * the `core/android-common` module. For domain-safe usage, use the
 * pure-Kotlin [LocalDate.toRelativeString] overload that takes a pre-resolved
 * string resource map.
 *
 * NOTE: This file originally lived with Android deps but was cleaned.
 * The Context-dependent overload is now at:
 *   core/android-common/src/.../util/lang/DateExtensions.kt
 */
fun LocalDate.toRelativeString(
    context: android.content.Context,
    relative: Boolean = true,
    dateFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT),
): String {
    if (!relative) {
        return dateFormat.format(this)
    }
    val now = LocalDate.now()
    val difference = ChronoUnit.DAYS.between(this, now)
    return when {
        difference < -7 -> dateFormat.format(this)
        difference < 0 -> context.resources.getQuantityString(
            ephyra.app.core.common.R.plurals.upcoming_relative_time,
            difference.toInt().absoluteValue,
            difference.toInt().absoluteValue,
        )
        difference < 1 -> context.getString(ephyra.app.core.common.R.string.relative_time_today)
        difference < 7 -> context.resources.getQuantityString(
            ephyra.app.core.common.R.plurals.relative_time,
            difference.toInt(),
            difference.toInt(),
        )
        else -> dateFormat.format(this)
    }
}
