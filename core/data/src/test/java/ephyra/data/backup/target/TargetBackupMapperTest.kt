package ephyra.data.backup.target

import ephyra.data.backup.target.TargetBackupDocument
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetBackupMapperTest {

    private val protoBuf = ProtoBuf
    private val mapper = TargetBackupMapper()

    @Test
    fun `target backup round trips without legacy source ids`() {
        val snapshot = TargetBackupSnapshot(
            series = TargetBackupMapperTest.series(),
            sourceReferences = listOf(TargetBackupMapperTest.sourceReference()),
            libraryEntry = TargetBackupMapperTest.libraryEntry(),
            chapters = listOf(TargetBackupMapperTest.chapter()),
            chapterStates = listOf(TargetBackupMapperTest.chapterState()),
            history = listOf(TargetBackupMapperTest.history()),
            tracking = listOf(TargetBackupMapperTest.tracking()),
            categories = listOf(TargetBackupMapperTest.category()),
            seriesCategories = listOf(
                ephyra.data.room.target.TargetSeriesCategoryEntity(
                    seriesId = "native:opds",
                    categoryId = "category:reading",
                ),
            ),
        )
        val document = mapper.toBackupDocument(listOf(snapshot))
        val bytes = protoBuf.encodeToByteArray(TargetBackupDocument.serializer(), document)
        val decoded = protoBuf.decodeFromByteArray(TargetBackupDocument.serializer(), bytes)
        val restored = mapper.fromBackupDocument(decoded).single()

        assertEquals("native:opds", restored.series.localId)
        assertEquals(listOf("Action"), Json.decodeFromString<List<String>>(restored.series.genresJson))
        assertEquals("opds", restored.sourceReferences.single().sourceId)
        assertEquals(true, restored.libraryEntry!!.updateEnabled)
        assertEquals(true, restored.chapterStates.single().isRead)
        assertEquals(1234L, restored.history.single().lastReadAt)
        assertEquals("Reading", restored.categories.single().name)
        assertEquals("category:reading", restored.seriesCategories.single().categoryId)
        assertEquals("legacy-tracker:3", restored.tracking.single().trackerId)
        assertEquals("99", restored.tracking.single().remoteId)
        assertEquals("Remote title", restored.tracking.single().title)
    }

    @Test
    fun `target backup rejects state for an unknown chapter`() {
        val malformed = mapper.toBackupDocument(
            listOf(
                TargetBackupSnapshot(
                    series = series(),
                    sourceReferences = emptyList(),
                    libraryEntry = null,
                    chapters = emptyList(),
                    chapterStates = listOf(chapterState()),
                    history = emptyList(),
                ),
            ),
        )

        val failure = runCatching { mapper.fromBackupDocument(malformed) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun `target backup rejects conflicting category definitions`() {
        val first = TargetBackupSnapshot(
            series = series(),
            sourceReferences = emptyList(),
            libraryEntry = null,
            chapters = emptyList(),
            chapterStates = emptyList(),
            history = emptyList(),
            categories = listOf(category()),
        )
        val conflicting = first.copy(
            categories = listOf(category().copy(name = "Different")),
        )

        val failure = runCatching { mapper.toBackupDocument(listOf(first, conflicting)) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun `target backup rejects missing category definition for membership`() {
        val snapshot = TargetBackupSnapshot(
            series = series(),
            sourceReferences = emptyList(),
            libraryEntry = null,
            chapters = emptyList(),
            chapterStates = emptyList(),
            history = emptyList(),
            seriesCategories = listOf(
                ephyra.data.room.target.TargetSeriesCategoryEntity("native:opds", "missing"),
            ),
        )

        val failure = runCatching { mapper.toBackupDocument(listOf(snapshot)) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun `target backup rejects unknown category membership`() {
        val malformed = mapper.toBackupDocument(emptyList()).copy(
            series = listOf(
                TargetBackupSeries(
                    localId = "native:opds",
                    contentType = "MANGA",
                    title = "Target Series",
                    categoryIds = listOf("missing"),
                ),
            ),
        )

        val failure = runCatching { mapper.fromBackupDocument(malformed) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun `target backup rejects blank local identity`() {
        val malformed = mapper.toBackupDocument(emptyList()).copy(
            series = listOf(series().toBackup().copy(localId = " ")),
        )

        val failure = runCatching { mapper.fromBackupDocument(malformed) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun `target backup rejects unsupported format version`() {
        val malformed = mapper.toBackupDocument(emptyList()).copy(formatVersion = 99)

        val failure = runCatching { mapper.fromBackupDocument(malformed) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    private fun ephyra.data.room.target.TargetSeriesEntity.toBackup() = TargetBackupSeries(
        localId = localId,
        contentType = contentType,
        title = title,
        author = author,
        artist = artist,
        description = description,
        status = status,
        genres = emptyList(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private companion object {
        fun series() = ephyra.data.room.target.TargetSeriesEntity(
            localId = "native:opds",
            contentType = "MANGA",
            title = "Target Series",
            author = null,
            artist = null,
            description = null,
            status = null,
            genresJson = "[\"Action\"]",
            createdAt = 1L,
            updatedAt = 2L,
        )

        fun sourceReference() = ephyra.data.room.target.TargetSeriesSourceEntity(
            seriesId = "native:opds",
            sourceId = "opds",
            externalId = "stable-1",
            url = "https://example.test/series/1",
            revision = 4L,
            displayTitle = "Target Series",
            thumbnailUrl = null,
            sourceMetadataJson = "{}",
            lastSeenAt = 2L,
        )

        fun libraryEntry() = ephyra.data.room.target.TargetLibraryEntryEntity(
            seriesId = "native:opds",
            addedAt = 10L,
            librarySortPosition = null,
            updatePolicy = "DEFAULT",
            updateEnabled = true,
            lastCheckedAt = null,
            lastChangedAt = null,
        )

        fun chapter() = ephyra.data.room.target.TargetChapterEntity(
            localId = "native:chapter:1",
            seriesId = "native:opds",
            sourceId = "opds",
            externalId = "chapter-1",
            url = "https://example.test/series/1/chapter/1",
            title = "Chapter 1",
            unitNumber = 1.0,
            scanlator = null,
            sortKey = "0001",
            revision = 1L,
            publishedAt = 2L,
            fetchedAt = 3L,
        )

        fun chapterState() = ephyra.data.room.target.TargetChapterStateEntity(
            chapterId = "native:chapter:1",
            isRead = true,
            bookmarked = true,
            lastPageRead = 3L,
        )

        fun history() = ephyra.data.room.target.TargetHistoryEntity(
            targetChapterLocalId = "native:chapter:1",
            lastReadAt = 1234L,
            readDurationMs = 60L,
        )

        fun tracking() = ephyra.data.room.target.TargetTrackingEntity(
            seriesId = "native:opds",
            trackerId = "legacy-tracker:3",
            remoteId = "99",
            libraryId = null,
            title = "Remote title",
            lastChapterRead = 12.0,
            totalChapters = 24L,
            status = "2",
            score = 8.5,
            remoteUrl = "https://tracker.test/99",
            startedAt = 1000L,
            finishedAt = 0L,
            isPrivate = true,
            updatedAt = 2L,
        )

        fun category() = ephyra.data.room.target.TargetCategoryEntity(
            categoryId = "category:reading",
            name = "Reading",
            sortOrder = 2L,
            flags = 1L,
            isSystem = false,
        )
    }
}
