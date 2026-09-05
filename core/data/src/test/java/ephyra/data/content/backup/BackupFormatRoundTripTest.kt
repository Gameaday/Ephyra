package ephyra.data.backup

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.domain.backup.model.Backup
import ephyra.domain.backup.model.BackupCategory
import ephyra.domain.backup.model.BackupChapter
import ephyra.domain.backup.model.BackupManga
import ephyra.domain.backup.model.BackupSource
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * Hermetic round-trip test for the backup persistence pipeline (the "store" leg of the
 * app): library content is serialized with the same protobuf schema that Ephyra and
 * Mihon exchange, gzip-compressed like [BackupCreator] writes it, then decoded back.
 *
 * Guarantees that a user's library survives export → import without data loss and that
 * Mihon's gzip-wrapped protobuf format stays decodable (regression guard for the
 * Mihon backup-bridge compatibility promise).
 */
@OptIn(ExperimentalSerializationApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BackupFormatRoundTripTest {

    private val protoBuf = ProtoBuf

    private fun sampleBackup(): Backup {
        val manga = BackupManga(
            source = 2499283573021220255,
            url = "/manga/berserk",
            title = "Berserk",
            artist = "Kentaro Miura",
            author = "Kentaro Miura",
            description = "A dark fantasy epic.",
            genre = listOf("Action", "Dark Fantasy"),
            status = 2,
            thumbnailUrl = "https://example.com/berserk.jpg",
            dateAdded = 1_700_000_000_000,
            favorite = true,
            chapters = listOf(
                BackupChapter(
                    url = "/manga/berserk/chapter-1",
                    name = "Chapter 1",
                    scanlator = "Group A",
                    read = true,
                    bookmark = false,
                ),
                BackupChapter(
                    url = "/manga/berserk/chapter-2",
                    name = "Chapter 2",
                    scanlator = "Group A",
                    read = false,
                    bookmark = true,
                ),
            ),
            categories = listOf(0),
        )
        return Backup(
            backupManga = listOf(manga),
            backupCategories = listOf(
                BackupCategory(name = "Reading", order = 0L),
            ),
            backupSources = listOf(
                BackupSource(name = "Fake Source", sourceId = 2499283573021220255),
            ),
        )
    }

    @Test
    fun `gzip backup round-trips without data loss`() {
        val original = sampleBackup()
        val bytes = ByteArrayOutputStream().use { raw ->
            GZIPOutputStream(raw).use { gzip ->
                gzip.write(protoBuf.encodeToByteArray(Backup.serializer(), original))
            }
            raw.toByteArray()
        }
        assertTrue("gzip magic number expected", bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte())

        val decoder = BackupDecoder(
            context = ApplicationProvider.getApplicationContext(),
            protoBuf = protoBuf,
        )
        val restored = decoder.decode(ByteArrayInputStream(bytes))

        assertEquals(1, restored.backupManga.size)
        val manga = restored.backupManga.single()
        assertEquals("Berserk", manga.title)
        assertEquals("/manga/berserk", manga.url)
        assertEquals(2499283573021220255L, manga.source)
        assertEquals("Kentaro Miura", manga.author)
        assertEquals("A dark fantasy epic.", manga.description)
        assertTrue(manga.favorite)
        assertEquals(2, manga.chapters.size)
        assertEquals("Group A", manga.chapters[0].scanlator)
        assertEquals(true, manga.chapters[0].read)
        assertEquals(true, manga.chapters[1].bookmark)
        assertEquals(listOf("Action", "Dark Fantasy"), manga.genre)
        assertEquals("Reading", restored.backupCategories.single().name)
        assertEquals(1, restored.backupSources.size)
    }

    @Test
    fun `plain uncompressed backup also decodes`() {
        val original = sampleBackup()
        val bytes = protoBuf.encodeToByteArray(Backup.serializer(), original)

        val decoder = BackupDecoder(
            context = ApplicationProvider.getApplicationContext(),
            protoBuf = protoBuf,
        )
        val restored = decoder.decode(ByteArrayInputStream(bytes))
        assertEquals(original.backupManga.single().title, restored.backupManga.single().title)
    }

    @Test
    fun `decoded chapters preserve read-state for history restoration`() {
        val bytes = ByteArrayOutputStream().use { raw ->
            GZIPOutputStream(raw).use { gzip ->
                gzip.write(protoBuf.encodeToByteArray(Backup.serializer(), sampleBackup()))
            }
            raw.toByteArray()
        }
        val decoder = BackupDecoder(
            context = ApplicationProvider.getApplicationContext(),
            protoBuf = protoBuf,
        )
        val restored = decoder.decode(ByteArrayInputStream(bytes))
        val chapters = restored.backupManga.single().chapters
        assertEquals(listOf(true, false), chapters.map { it.read })
        assertEquals(listOf(false, true), chapters.map { it.bookmark })
    }
}
