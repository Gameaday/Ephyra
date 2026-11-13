package ephyra.data.coil

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.request.Options
import ephyra.data.cache.CoverCache
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaCover
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class MangaCoverKeyerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val coverCache = CoverCache(context).also(CoverCache::deleteAll)
    private val options = Options(context)

    @After
    fun tearDown() {
        coverCache.deleteAll()
    }

    @Test
    fun `browse cover keys separate revisions for manga and lightweight models`() {
        val manga = Manga.create().copy(
            id = 1L,
            thumbnailUrl = "https://example.test/cover.jpg",
            coverLastModified = 1L,
        )
        val refreshedManga = manga.copy(coverLastModified = 2L)
        val cover = MangaCover(
            mangaId = manga.id,
            sourceId = manga.source,
            isMangaFavorite = false,
            url = manga.thumbnailUrl,
            lastModified = 1L,
        )
        val refreshedCover = cover.copy(lastModified = 2L)

        assertNotEquals(
            MangaKeyer().key(manga, options),
            MangaKeyer().key(refreshedManga, options),
        )
        assertNotEquals(
            MangaCoverKeyer().key(cover, options),
            MangaCoverKeyer().key(refreshedCover, options),
        )
    }

    @Test
    fun `browse covers key by url so duplicate entries share one cached cover`() {
        val first = Manga.create().copy(
            id = 1L,
            thumbnailUrl = "https://example.test/same.jpg",
            coverLastModified = 1L,
        )
        val second = first.copy(id = 2L)

        assertEquals(MangaKeyer().key(first, options), MangaKeyer().key(second, options))
        assertEquals(
            "https://example.test/same.jpg;1",
            MangaCoverKeyer().key(
                MangaCover(1L, 1L, false, "https://example.test/same.jpg", 1L),
                options,
            ),
        )
    }

    @Test
    fun `favorite covers key by manga identity whether or not a custom cover file exists`() {
        val manga = Manga.create().copy(id = 7L, favorite = true, coverLastModified = 3L)
        val cover = MangaCover(7L, 1L, true, "https://example.test/remote.jpg", 3L)

        assertEquals("7;3", MangaKeyer().key(manga, options))
        assertEquals("7;3", MangaCoverKeyer().key(cover, options))

        // The key never probes the filesystem (audit P2): setting a custom cover changes the
        // bytes, not the key shape — the revision carries the change, and every mutation path
        // (`editCover`, `deleteCustomCover`, `removeCovers`, migration) bumps it.
        coverCache.setCustomCoverToCache(manga, ByteArrayInputStream("custom".toByteArray()))
        assertEquals("7;3", MangaKeyer().key(manga, options))
        assertEquals("7;3", MangaCoverKeyer().key(cover, options))
    }

    @Test
    fun `a revision bump rekeys favorite covers`() {
        val manga = Manga.create().copy(id = 7L, favorite = true, coverLastModified = 3L)

        assertNotEquals(
            MangaKeyer().key(manga, options),
            MangaKeyer().key(manga.copy(coverLastModified = 4L), options),
        )
        assertNotEquals(
            MangaCoverKeyer().key(MangaCover(7L, 1L, true, null, 3L), options),
            MangaCoverKeyer().key(MangaCover(7L, 1L, true, null, 4L), options),
        )
    }

    @Test
    fun `favorite and browse covers with the same revision do not collide`() {
        val cover = MangaCover(7L, 1L, true, "https://example.test/remote.jpg", 3L)
        val browseCover = cover.copy(isMangaFavorite = false)

        assertNotEquals(
            MangaCoverKeyer().key(cover, options),
            MangaCoverKeyer().key(browseCover, options),
        )
    }
}
