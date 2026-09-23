package ephyra.source.local.metadata

import ephyra.core.archive.EpubReader
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Fills manga and chapter metadata using this epub file's metadata.
 */
fun EpubReader.fillMetadata(manga: SManga, chapter: SChapter) {
    val ref = getPackageHref()
    val doc = getPackageDocument(ref)

    val title = doc.getElementsByTag("dc:title").first()
    val publisher = doc.getElementsByTag("dc:publisher").first()
    val creator = doc.getElementsByTag("dc:creator").first()
    val description = doc.getElementsByTag("dc:description").first()
    var date = doc.getElementsByTag("dc:date").first()
    if (date == null) {
        date = doc.select("meta[property=dcterms:modified]").first()
    }

    creator?.text()?.let { manga.author = it }
    description?.text()?.let { manga.description = it }

    title?.text()?.let { chapter.name = it }

    if (publisher != null) {
        chapter.scanlator = publisher.text()
    } else if (creator != null) {
        chapter.scanlator = creator.text()
    }

    if (date != null) {
        parseEpubDate(date.text())?.let { chapter.date_upload = it }
    }
}

/**
 * Parsers for the date shapes an EPub package document can carry, tried in order: `dc:date` holds
 * an ISO-8601 value with a UTC offset (`2019-01-01T00:00:00+00:00`), while `dcterms:modified` is an
 * `xsd:dateTime` instant ending in `Z`. Offset-less values are read as UTC.
 *
 * Parsing is deliberately independent of the device locale and calendar. The
 * `SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault())` this replaced inherited the
 * default calendar, so on a locale such as `th-TH` the extracted year was silently shifted by the
 * Buddhist era offset.
 */
private val epubDateParsers: List<(String) -> Long> = listOf(
    { text -> OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli() },
    { text -> Instant.parse(text).toEpochMilli() },
    { text ->
        LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toInstant(ZoneOffset.UTC).toEpochMilli()
    },
    { text ->
        LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    },
)

/**
 * @return the epoch millis of an EPub metadata date, or `null` when it is absent or in a shape none
 *   of [epubDateParsers] understands. A missing date leaves `SChapter.date_upload` at its default.
 */
internal fun parseEpubDate(raw: String?): Long? {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return epubDateParsers.firstNotNullOfOrNull { parse -> runCatching { parse(text) }.getOrNull() }
}
