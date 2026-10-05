package ephyra.data.cache

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class CoverCacheTest {

    private lateinit var cache: CoverCache

    @Before
    fun setUp() {
        cache = CoverCache(ApplicationProvider.getApplicationContext())
        cache.deleteAll()
    }

    @After
    fun tearDown() {
        cache.deleteAll()
    }

    @Test
    fun `remote cover identity is stable and persists outside custom storage`() {
        val first = cache.getCoverFile("https://example.test/cover.jpg", lastModified = 0L)
        val second = cache.getCoverFile("https://example.test/cover.jpg", lastModified = 0L)

        assertEquals(first, second)
        assertFalse(first!!.parentFile == cache.getCustomCoverFile(1L).parentFile)
    }

    @Test
    fun `remote cover revisions have distinct durable identities`() {
        val first = cache.getCoverFile("https://example.test/cover.jpg", lastModified = 1L)
        val refreshed = cache.getCoverFile("https://example.test/cover.jpg", lastModified = 2L)

        assertNotEquals(first, refreshed)
        assertEquals(
            setOf(first!!.name, refreshed!!.name),
            cache.coverFileNames(
                listOf(
                    "https://example.test/cover.jpg" to 1L,
                    "https://example.test/cover.jpg" to 2L,
                ),
            ),
        )
    }

    @Test
    fun `touch refreshes durable hit age used by lru pruning`() {
        val file = cache.getCoverFile("https://example.test/cover.jpg", lastModified = 1L)!!
        file.parentFile?.mkdirs()
        file.writeText("cover")
        file.setLastModified(1_000L)

        assertTrue(cache.touch(file))
        assertTrue(file.lastModified() > 1_000L)
    }

    @Test
    fun `size pruning evicts oldest unprotected remote covers`() {
        val protected = cache.getCoverFile("https://example.test/protected.jpg", lastModified = 0L)!!
        val removable = cache.getCoverFile("https://example.test/removable.jpg", lastModified = 0L)!!
        protected.parentFile?.mkdirs()
        protected.writeText("12345678")
        removable.writeText("abcdefgh")
        protected.setLastModified(2_000L)
        removable.setLastModified(1_000L)

        val pruned = cache.pruneOldCovers(
            protectedNames = setOf(protected.name),
            maxAgeMs = Long.MAX_VALUE,
            maxBytes = 8L,
        )

        assertEquals(1, pruned)
        assertTrue(protected.exists())
        assertFalse(removable.exists())
    }

    @Test
    fun `size pruning counts the custom cover subtree towards the budget`() {
        // `File.length()` on a directory reports the directory's own inode size (typically 4 KiB)
        // and nothing about its contents, so a flat sum made the whole `custom/` subtree look like
        // one small entry and the byte budget never saw those covers. The cache could then exceed
        // its own cap by every custom cover it held, which is the thing the cap exists to prevent.
        //
        // The numbers are chosen so the two implementations disagree, which is the only kind of
        // assertion worth having here:
        //   flat     sees  4 KiB (dir inode) + 8 KiB (remote) = 12 KiB   < 60 KiB  -> prunes nothing
        //   recursive sees 64 KiB (custom)    + 8 KiB (remote) = 72 KiB   > 60 KiB  -> prunes the remote
        // The custom cover is never itself prunable, so it is the *remote* cover whose fate proves
        // which sum was used.
        val custom = cache.getCustomCoverFile(1L)
        custom.parentFile?.mkdirs()
        cache.setCustomCoverToCache(
            ephyra.domain.manga.model.Manga.create().copy(id = 1L),
            ByteArrayInputStream(ByteArray(64 * 1024) { 'x'.code.toByte() }),
        )
        val remote = cache.getCoverFile("https://example.test/removable.jpg", lastModified = 0L)!!
        remote.parentFile?.mkdirs()
        remote.writeBytes(ByteArray(8 * 1024) { 'y'.code.toByte() })
        remote.setLastModified(1_000L)
        assertTrue(custom.exists() && remote.exists())

        val pruned = cache.pruneOldCovers(
            protectedNames = emptySet(),
            maxAgeMs = Long.MAX_VALUE,
            maxBytes = 60L * 1024,
        )

        assertEquals(
            "the custom cover's bytes must count towards the budget, or the remote cover is " +
                "considered within budget while the cache is over it",
            1L,
            pruned.toLong(),
        )
        assertFalse("the unprotected remote cover should have been pruned", remote.exists())
        assertTrue("custom covers must never be pruned", custom.exists())
    }

    @Test
    fun `ordinary pruning protects library names and custom covers`() {
        val remote = cache.getCoverFile("https://example.test/library.jpg", lastModified = 0L)!!
        remote.parentFile?.mkdirs()
        remote.writeText("remote")
        remote.setLastModified(0L)

        val custom = cache.getCustomCoverFile(1L)
        custom.parentFile?.mkdirs()
        cache.setCustomCoverToCache(
            ephyra.domain.manga.model.Manga.create().copy(id = 1L),
            ByteArrayInputStream("custom".toByteArray()),
        )

        val pruned = cache.pruneOldCovers(protectedNames = setOf(remote.name))

        assertEquals(0, pruned)
        assertTrue(remote.exists())
        assertEquals("custom", custom.readText())
    }
}
