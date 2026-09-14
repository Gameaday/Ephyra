package ephyra.data.backup

import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.data.backup.models.Backup
import ephyra.data.backup.models.BackupCategory
import ephyra.data.backup.models.BackupChapter
import ephyra.data.backup.models.BackupManga
import ephyra.data.backup.models.BackupSavedSearch
import ephyra.data.backup.models.BackupSource
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

    @Test
    fun `zip tachibk archive containing backup proto gz decodes properly`() {
        val original = sampleBackup()
        val protoBytes = protoBuf.encodeToByteArray(Backup.serializer(), original)
        val gzipBytes = ByteArrayOutputStream().use { raw ->
            GZIPOutputStream(raw).use { gzip -> gzip.write(protoBytes) }
            raw.toByteArray()
        }

        // Build a ZIP container matching Mihon / modern Tachiyomi .tachibk
        val zipBytes = ByteArrayOutputStream().use { byteOut ->
            java.util.zip.ZipOutputStream(byteOut).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("backup.proto.gz"))
                zip.write(gzipBytes)
                zip.closeEntry()

                // Optional dummy cover entry to simulate full archive
                zip.putNextEntry(java.util.zip.ZipEntry("covers/berserk.jpg"))
                zip.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
                zip.closeEntry()
            }
            byteOut.toByteArray()
        }

        assertTrue("ZIP magic bytes expected", zipBytes[0] == 0x50.toByte() && zipBytes[1] == 0x4B.toByte())

        val decoder = BackupDecoder(
            context = ApplicationProvider.getApplicationContext(),
            protoBuf = protoBuf,
        )
        val restored = decoder.decode(ByteArrayInputStream(zipBytes))

        assertEquals(1, restored.backupManga.size)
        assertEquals("Berserk", restored.backupManga.single().title)
        assertEquals(2, restored.backupManga.single().chapters.size)
        assertEquals("Reading", restored.backupCategories.single().name)
    }

    @Test
    fun `zip tachibk archive containing uncompressed backup proto decodes properly`() {
        val original = sampleBackup()
        val protoBytes = protoBuf.encodeToByteArray(Backup.serializer(), original)

        val zipBytes = ByteArrayOutputStream().use { byteOut ->
            java.util.zip.ZipOutputStream(byteOut).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("backup.proto"))
                zip.write(protoBytes)
                zip.closeEntry()
            }
            byteOut.toByteArray()
        }

        val decoder = BackupDecoder(
            context = ApplicationProvider.getApplicationContext(),
            protoBuf = protoBuf,
        )
        val restored = decoder.decode(ByteArrayInputStream(zipBytes))

        assertEquals(1, restored.backupManga.size)
        assertEquals("Berserk", restored.backupManga.single().title)
    }

    @Test
    fun `tachiyomiSY simulated backup with unknown proto fields decodes properly`() {
        val original = sampleBackup()
        val protoBytes = protoBuf.encodeToByteArray(Backup.serializer(), original)

        // Append a raw unknown proto field (tag 999 with wire type 2: (999 shl 3) or 2 = 7994)
        // Varint for 7994 is 0xEA, 0x3E
        val payload = "TachiyomiSY Custom Metadata".toByteArray()
        val unknownFieldBytes = byteArrayOf(0xEA.toByte(), 0x3E.toByte(), payload.size.toByte()) + payload

        val combinedBytes = protoBytes + unknownFieldBytes

        val zipBytes = ByteArrayOutputStream().use { byteOut ->
            java.util.zip.ZipOutputStream(byteOut).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("backup.proto.gz"))
                val gzipOut = ByteArrayOutputStream()
                GZIPOutputStream(gzipOut).use { it.write(combinedBytes) }
                zip.write(gzipOut.toByteArray())
                zip.closeEntry()
            }
            byteOut.toByteArray()
        }

        val decoder = BackupDecoder(
            context = ApplicationProvider.getApplicationContext(),
            protoBuf = protoBuf,
        )
        val restored = decoder.decode(ByteArrayInputStream(zipBytes))

        assertEquals(1, restored.backupManga.size)
        assertEquals("Berserk", restored.backupManga.single().title)
        assertEquals("Reading", restored.backupCategories.single().name)
    }

    @Test
    fun `staged local backup file decodes via file Uri directly`() {
        val original = sampleBackup()
        val protoBytes = protoBuf.encodeToByteArray(Backup.serializer(), original)

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val tempFile = java.io.File(context.cacheDir, "test_staged_backup.tachibk")
        java.io.FileOutputStream(tempFile).use { fileOut ->
            GZIPOutputStream(fileOut).use { gzip ->
                gzip.write(protoBytes)
            }
        }

        try {
            val decoder = BackupDecoder(
                context = context,
                protoBuf = protoBuf,
            )
            val restored = decoder.decode(tempFile.toUri())

            assertEquals(1, restored.backupManga.size)
            assertEquals("Berserk", restored.backupManga.single().title)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `mihon and tachiyomiSY backups with memo and saved searches decode cleanly`() {
        val memoBytes = """{"key":"value"}""".toByteArray()
        val mangaWithMemo = BackupManga(
            source = 1000L,
            url = "/manga/sy_test",
            title = "TachiyomiSY Title",
            memo = memoBytes,
            metadataSource = 12345L,
            metadataUrl = "https://example.com/meta",
            chapters = listOf(
                BackupChapter(
                    url = "/manga/sy_test/ch1",
                    name = "Chapter 1",
                    memo = memoBytes,
                ),
            ),
        )

        val backup = Backup(
            backupManga = listOf(mangaWithMemo),
            backupSavedSearches = listOf(
                BackupSavedSearch(
                    name = "Manga Updates",
                    query = "genre:action",
                    filterList = "[]",
                    source = 1000L,
                ),
            ),
        )

        val bytes = protoBuf.encodeToByteArray(Backup.serializer(), backup)
        val decoder = BackupDecoder(
            context = ApplicationProvider.getApplicationContext(),
            protoBuf = protoBuf,
        )
        val restored = decoder.decode(ByteArrayInputStream(bytes))

        assertEquals(1, restored.backupManga.size)
        val decodedManga = restored.backupManga.single()
        assertEquals("TachiyomiSY Title", decodedManga.title)
        assertTrue(decodedManga.memo.contentEquals(memoBytes))
        assertEquals(12345L, decodedManga.metadataSource)
        assertEquals("https://example.com/meta", decodedManga.metadataUrl)
        assertEquals(1, decodedManga.chapters.size)
        assertTrue(decodedManga.chapters.single().memo.contentEquals(memoBytes))
        assertEquals(1, restored.backupSavedSearches.size)
        assertEquals("Manga Updates", restored.backupSavedSearches.single().name)
    }
}
