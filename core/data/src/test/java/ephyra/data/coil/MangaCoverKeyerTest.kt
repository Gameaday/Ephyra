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
    fun `remote cover keys separate revisions for manga and lightweight models`() {
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
            MangaKeyer(coverCache).key(manga, options),
            MangaKeyer(coverCache).key(refreshedManga, options),
        )
        assertNotEquals(
            MangaCoverKeyer(coverCache).key(cover, options),
            MangaCoverKeyer(coverCache).key(refreshedCover, options),
        )
    }

    @Test
    fun `custom cover key uses manga identity and revision`() {
        val manga = Manga.create().copy(id = 7L, coverLastModified = 3L)
        coverCache.setCustomCoverToCache(manga, ByteArrayInputStream("custom".toByteArray()))
        val cover = MangaCover(7L, 1L, true, "https://example.test/remote.jpg", 3L)

        assertEquals("7;3", MangaKeyer(coverCache).key(manga, options))
        assertEquals("7;3", MangaCoverKeyer(coverCache).key(cover, options))
    }
}
