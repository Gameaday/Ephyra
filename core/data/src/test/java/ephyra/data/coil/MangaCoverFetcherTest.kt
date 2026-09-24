package ephyra.data.coil

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import ephyra.data.cache.CoverCache
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class MangaCoverFetcherTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `durable hit returns disk bytes and refreshes lru age`() = runTest {
        val coverCache = CoverCache(context).apply { deleteAll() }
        val coverFile = requireNotNull(
            coverCache.getCoverFile("https://example.test/cover.jpg", lastModified = 4L),
        )
        coverFile.parentFile?.mkdirs()
        coverFile.writeText("cached-image")
        coverFile.setLastModified(1_000L)
        val imageLoader = ImageLoader.Builder(context).build()
        var touched: File? = null
        val fetcher = MangaCoverFetcher(
            url = "https://example.test/cover.jpg",
            options = Options(context),
            coverFileLazy = lazy { coverFile },
            customCoverFileLazy = lazy { coverCache.getCustomCoverFile(1L) },
            diskCacheKeyLazy = lazy { "cover-key" },
            sourceLazy = lazy { null },
            callFactoryLazy = lazy { error("network must not be used for a durable hit") },
            imageLoader = imageLoader,
            touchCoverCache = { file ->
                coverCache.touch(file)
                touched = file
            },
        )

        val result = fetcher.fetch()

        assertTrue(result is SourceFetchResult)
        val sourceResult = result as SourceFetchResult
        assertEquals(DataSource.DISK, sourceResult.dataSource)
        assertEquals(coverFile, touched)
        assertTrue(coverFile.lastModified() > 1_000L)
        sourceResult.source.close()
        imageLoader.shutdown()
        coverCache.deleteAll()
    }
}
