package ephyra.data.cache

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val first = cache.getCoverFile("https://example.test/cover.jpg")
        val second = cache.getCoverFile("https://example.test/cover.jpg")

        assertEquals(first, second)
        assertFalse(first!!.parentFile == cache.getCustomCoverFile(1L).parentFile)
    }

    @Test
    fun `size pruning evicts oldest unprotected remote covers`() {
        val protected = cache.getCoverFile("https://example.test/protected.jpg")!!
        val removable = cache.getCoverFile("https://example.test/removable.jpg")!!
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
    fun `ordinary pruning protects library names and custom covers`() {
        val remote = cache.getCoverFile("https://example.test/library.jpg")!!
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
