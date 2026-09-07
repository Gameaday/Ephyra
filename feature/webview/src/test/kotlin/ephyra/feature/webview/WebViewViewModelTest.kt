package ephyra.feature.webview

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import ephyra.domain.source.service.SourceManager
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WebViewViewModelTest {

    private val sourceManager: SourceManager = mockk(relaxed = true)
    private val network: NetworkHelper = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()

    private val fakeHttpSource = object : HttpSource() {
        override val id: Long = 42L
        override val name: String = "Test Source"
        override val baseUrl: String = "https://example.com"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        override fun headersBuilder(): Headers.Builder = Headers.Builder().add("User-Agent", "EphyraTest")

        override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(emptyList(), false)
        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(emptyList(), false)
        override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(emptyList(), false)
        override suspend fun getMangaDetails(manga: SManga): SManga = manga
        override suspend fun getChapterList(manga: SManga): List<SChapter> = emptyList()
        override suspend fun getPageList(chapter: SChapter): List<eu.kanade.tachiyomi.source.model.Page> = emptyList()
        override suspend fun getImageUrl(page: eu.kanade.tachiyomi.source.model.Page): String = ""
        override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
        override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
        override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
        override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
        override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
        override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
        override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()
        override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()
        override fun chapterPageParse(response: Response): SChapter = throw UnsupportedOperationException()
        override fun pageListParse(response: Response): List<eu.kanade.tachiyomi.source.model.Page> = throw UnsupportedOperationException()
        override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { sourceManager.get(42L) } returns fakeHttpSource
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state with sourceId in SavedStateHandle loads headers`() = runTest {
        val savedState = SavedStateHandle(mapOf("source_key" to 42L))
        val viewModel = WebViewViewModel(savedState, sourceManager, network)

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(42L, state.sourceId)
            assertEquals("EphyraTest", state.headers["user-agent"] ?: state.headers["User-Agent"])
        }
    }

    @Test
    fun `initialize updates state with source headers`() = runTest {
        val savedState = SavedStateHandle()
        val viewModel = WebViewViewModel(savedState, sourceManager, network)

        viewModel.state.test {
            val initial = awaitItem()
            assertTrue(initial.headers.isEmpty())

            viewModel.initialize(42L)

            val updated = awaitItem()
            assertEquals(42L, updated.sourceId)
            assertEquals("EphyraTest", updated.headers["user-agent"] ?: updated.headers["User-Agent"])
        }
    }

    @Test
    fun `ShareWebpage and OpenInBrowser emit effects`() = runTest {
        val savedState = SavedStateHandle()
        val viewModel = WebViewViewModel(savedState, sourceManager, network)

        viewModel.effects.test {
            viewModel.onEvent(WebViewScreenEvent.ShareWebpage("https://example.com"))
            assertEquals(WebViewEffect.ShareWebpage("https://example.com"), awaitItem())

            viewModel.onEvent(WebViewScreenEvent.OpenInBrowser("https://example.com/item"))
            assertEquals(WebViewEffect.OpenInBrowser("https://example.com/item"), awaitItem())
        }
    }
}
