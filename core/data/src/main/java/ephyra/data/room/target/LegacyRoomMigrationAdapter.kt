package ephyra.data.room.target

import ephyra.data.room.entities.ChapterEntity
import ephyra.data.room.entities.HistoryEntity
import ephyra.data.room.entities.MangaEntity
import ephyra.domain.content.model.ContentType
import ephyra.domain.series.LegacyChapterRecord
import ephyra.domain.series.LegacyHistoryRecord
import ephyra.domain.series.LegacySeriesMigrationInput
import ephyra.domain.series.LegacySeriesRecord

/**
 * One-way compatibility adapter from the current Room v3 entities to the pure target migration
 * contract. It intentionally performs no writes and owns no identity policy.
 */
object LegacyRoomMigrationAdapter {
    fun toMigrationInput(
        manga: MangaEntity,
        chapters: List<ChapterEntity>,
        history: List<HistoryEntity>,
    ): LegacySeriesMigrationInput {
        val series = LegacySeriesRecord(
            legacyId = manga.id,
            sourceId = manga.source,
            url = manga.url,
            title = manga.title,
            author = manga.author,
            artist = manga.artist,
            description = manga.description,
            genres = manga.genre.orEmpty(),
            status = manga.status,
            thumbnailUrl = manga.thumbnailUrl,
            sourceRevision = manga.version,
            inLibrary = manga.favorite,
            dateAdded = manga.dateAdded,
            contentType = ContentType.fromValue(manga.contentType),
        )
        val legacyChapters = chapters.map { chapter ->
            LegacyChapterRecord(
                legacyId = chapter.id,
                seriesLegacyId = chapter.mangaId,
                url = chapter.url,
                title = chapter.name,
                scanlator = chapter.scanlator,
                chapterNumber = chapter.chapterNumber,
                sourceOrder = chapter.sourceOrder.toLong(),
                read = chapter.read,
                bookmark = chapter.bookmark,
                lastPageRead = chapter.lastPageRead.toLong(),
                dateFetch = chapter.dateFetch,
                dateUpload = chapter.dateUpload,
                lastModifiedAt = chapter.lastModifiedAt,
                revision = chapter.version,
            )
        }
        val legacyHistory = history.map { record ->
            LegacyHistoryRecord(
                chapterLegacyId = record.chapterId,
                lastReadAtMillis = record.lastRead?.time,
                readDurationMillis = record.timeRead,
            )
        }
        return LegacySeriesMigrationInput(series, legacyChapters, legacyHistory)
    }
}
