package ephyra.domain.content.source

import ephyra.domain.content.model.ContentType
import eu.kanade.tachiyomi.network.NetworkHelper
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class AdaptiveHeuristicEngineTest {

    private val networkHelper = mockk<NetworkHelper>()
    private val profileCache = mockk<SourceProfileCache>()
    private val engine = AdaptiveHeuristicEngine(kotlinx.coroutines.Dispatchers.Unconfined, networkHelper, profileCache)

    @Test
    fun `discover extracts standard layout components successfully`() = runBlocking {
        val baseUrl = "https://example-manga.com"
        val htmlContent = """
            <html>
            <head><title>Example Manga Portal</title></head>
            <body>
                <form action="/search" method="get">
                    <input name="q" type="text" />
                    <button type="submit">Search</button>
                </form>
                <div class="grid">
                    <div class="card">
                        <a href="/manga/1">
                            <h2>One Piece</h2>
                            <img src="/covers/one_piece.jpg" />
                        </a>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val mockClient = mockk<OkHttpClient>()
        val mockCall = mockk<Call>()

        every { networkHelper.client } returns mockClient
        every { mockClient.newCall(any()) } returns mockCall

        val dummyRequest = Request.Builder().url(baseUrl).build()
        val dummyResponse = Response.Builder()
            .request(dummyRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(htmlContent.toResponseBody())
            .build()

        every { mockCall.execute() } returns dummyResponse

        val profile = engine.discover(baseUrl)

        assertNotNull(profile)
        assertEquals(baseUrl, profile.baseUrl)
        assertEquals(ContentType.MANGA, profile.contentType)

        // Check inferred search path
        val searchPattern = profile.endpoints[Endpoint.SEARCH]?.pathTemplate
        assertEquals("/search?q={query}", searchPattern)

        // Check inferred selectors
        val selectors = profile.selectors
        assertNotNull(selectors)
        val nonNullSelectors = selectors!!
        assertEquals("div .item, div .card, a.manga-card", nonNullSelectors[DataField.ITEM_LIST])
        assertEquals("h3, h2, .title, .name", nonNullSelectors[DataField.ITEM_TITLE])
    }

    @Test
    fun `getChapters parses chapter links successfully`() = runBlocking {
        val itemUrl = "https://example-manga.com/manga/1"
        val htmlContent = """
            <html>
            <body>
                <div class="chapters">
                    <a href="https://example-manga.com/manga/1/chapter-2">Chapter 2</a>
                    <a href="https://example-manga.com/manga/1/chapter-1.5">Chapter 1.5</a>
                    <a href="https://example-manga.com/manga/1/chapter-1">Chapter 1</a>
                </div>
            </body>
            </html>
        """.trimIndent()

        val mockClient = mockk<OkHttpClient>()
        val mockCall = mockk<Call>()

        every { networkHelper.client } returns mockClient
        every { mockClient.newCall(any()) } returns mockCall

        val dummyRequest = Request.Builder().url(itemUrl).build()
        val dummyResponse = Response.Builder()
            .request(dummyRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(htmlContent.toResponseBody())
            .build()

        every { mockCall.execute() } returns dummyResponse

        val profile = SourceProfile(
            baseUrl = "https://example-manga.com",
            contentType = ContentType.MANGA,
            displayName = "Example",
        )
        val chapters = engine.getChapters(profile, itemUrl)

        assertEquals(3, chapters.size)
        assertEquals("Chapter 2", chapters[0].title)
        assertEquals(2.0, chapters[0].number)
        assertEquals("Chapter 1.5", chapters[1].title)
        assertEquals(1.5, chapters[1].number)
        assertEquals("Chapter 1", chapters[2].title)
        assertEquals(1.0, chapters[2].number)
    }

    @Test
    fun `getPages parses reader image urls successfully`() = runBlocking {
        val chapterUrl = "https://example-manga.com/manga/1/chapter-1"
        val htmlContent = """
            <html>
            <body>
                <div id="reader">
                    <img src="https://example-manga.com/img/page1.jpg" />
                    <img data-src="https://example-manga.com/img/page2.jpg" />
                </div>
            </body>
            </html>
        """.trimIndent()

        val mockClient = mockk<OkHttpClient>()
        val mockCall = mockk<Call>()

        every { networkHelper.client } returns mockClient
        every { mockClient.newCall(any()) } returns mockCall

        val dummyRequest = Request.Builder().url(chapterUrl).build()
        val dummyResponse = Response.Builder()
            .request(dummyRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(htmlContent.toResponseBody())
            .build()

        every { mockCall.execute() } returns dummyResponse

        val profile = SourceProfile(
            baseUrl = "https://example-manga.com",
            contentType = ContentType.MANGA,
            displayName = "Example",
        )
        val pages = engine.getPages(profile, chapterUrl)

        assertEquals(2, pages.size)
        assertEquals("https://example-manga.com/img/page1.jpg", pages[0])
        assertEquals("https://example-manga.com/img/page2.jpg", pages[1])
    }
}
