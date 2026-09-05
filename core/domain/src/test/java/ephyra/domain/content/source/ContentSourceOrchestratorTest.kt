package ephyra.domain.content.source

import ephyra.core.common.util.Result
import ephyra.core.common.util.getOrThrow
import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.model.ContentStatus
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.model.ContentUnit
import ephyra.testutil.FakePreferenceStore
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ContentSourceOrchestrator], the single entry point the app
 * uses to search, obtain details, fetch chapters/pages, and manage sources.
 *
 * This is where "none of the ways to get content work" surfaces: it routes
 * each request to the correct engine (heuristic vs. script), enforces enabled
 * state, and tracks source health. If routing or health tracking regresses,
 * remote content silently stops resolving.
 */
class ContentSourceOrchestratorTest {

    private val heuristic = FakeContentSourceEngine()
    private val script = FakeContentSourceEngine()

    private val orchestrator = ContentSourceOrchestrator(
        profileCache = SourceProfileCache(FakePreferenceStore(), Json { ignoreUnknownKeys = true }),
        heuristicEngine = heuristic,
        scriptEngine = script,
        preferenceStore = FakePreferenceStore(),
    )

    private fun profile(baseUrl: String, sourceType: SourceType = SourceType.HEURISTIC) =
        SourceProfile(baseUrl = baseUrl, contentType = ContentType.MANGA, sourceType = sourceType, enabled = true)

    private fun item(title: String) = ContentItem(
        id = -1L,
        sourceId = 1L,
        url = "/$title",
        title = title,
        author = null,
        artist = null,
        description = null,
        genres = emptyList(),
        status = ContentStatus.Unknown,
        thumbnailUrl = null,
        contentType = ContentType.MANGA,
    )

    // ── discover ─────────────────────────────────────────────────────────

    @Test
    fun `discover returns the discovered profile and caches it`() = runTest {
        heuristic.discoverHandler = { baseUrl -> profile(baseUrl) }

        val result = orchestrator.discover("https://mangadex.org")

        assertTrue(result is Result.Success)
        assertEquals("https://mangadex.org", result.getOrThrow().baseUrl)
        assertTrue(orchestrator.getAllProfiles().isNotEmpty())
    }

    // ── search ───────────────────────────────────────────────────────────

    @Test
    fun `search routes to heuristic engine and returns items`() = runTest {
        orchestrator.discover("https://mangadex.org")
        heuristic.searchHandler = { query -> listOf(item(query)) }

        val result = orchestrator.search("https://mangadex.org", "One Piece", 1)

        assertTrue(result is Result.Success)
        assertEquals(listOf("One Piece"), result.getOrThrow().map { it.title })
        assertEquals(1, heuristic.searchCalls)
    }

    @Test
    fun `search routes to script engine for JS_SCRAPER profile`() = runTest {
        orchestrator.discover("https://mangadex.org")
        orchestrator.setSourceType("https://mangadex.org", SourceType.JS_SCRAPER, "mangadex_scraper.js")
        script.searchHandler = { query -> listOf(item("Script:$query")) }

        val result = orchestrator.search("https://mangadex.org", "Naruto", 1)

        assertTrue(result is Result.Success)
        assertEquals(listOf("Script:Naruto"), result.getOrThrow().map { it.title })
        assertEquals(1, script.searchCalls)
        assertEquals(0, heuristic.searchCalls)
    }

    @Test
    fun `search returns error for a disabled source`() = runTest {
        orchestrator.discover("https://disabled.example")
        orchestrator.setSourceEnabled("https://disabled.example", false)

        val result = orchestrator.search("https://disabled.example", "q", 1)

        assertTrue(result is Result.Error)
    }

    @Test
    fun `search propagates engine search failure as Result Error`() = runTest {
        orchestrator.discover("https://mangadex.org")
        heuristic.searchHandler = { throw IllegalStateException("boom") }

        val result = orchestrator.search("https://mangadex.org", "q", 1)

        assertTrue(result is Result.Error)
    }

    // ── getItem / getChapters / getPages ─────────────────────────────────

    @Test
    fun `getItem returns details from the resolved engine`() = runTest {
        orchestrator.discover("https://mangadex.org")
        heuristic.getItemHandler = { item("Detail") }

        val result = orchestrator.getItem("https://mangadex.org", "/manga/1")

        assertTrue(result is Result.Success)
        assertEquals("Detail", result.getOrThrow().title)
    }

    @Test
    fun `getChapters returns units from the resolved engine`() = runTest {
        orchestrator.discover("https://mangadex.org")
        val chapter = ContentUnit(
            id = -1L,
            contentItemId = 1L,
            url = "/ch/1",
            title = "Ch 1",
            number = 1.0,
            dateUpload = 0L,
            progress = 0L,
            totalLength = 0L,
            lastRead = 0L,
            read = false,
        )
        heuristic.chaptersHandler = { listOf(chapter) }

        val result = orchestrator.getChapters("https://mangadex.org", "/manga/1")

        assertTrue(result is Result.Success)
        assertEquals(listOf("Ch 1"), result.getOrThrow().map { it.title })
    }

    @Test
    fun `getPages returns page urls from the resolved engine`() = runTest {
        orchestrator.discover("https://mangadex.org")
        heuristic.pagesHandler = { listOf("https://img/p1.jpg", "https://img/p2.jpg") }

        val result = orchestrator.getPages("https://mangadex.org", "/ch/1")

        assertTrue(result is Result.Success)
        assertEquals(listOf("https://img/p1.jpg", "https://img/p2.jpg"), result.getOrThrow())
    }

    // ── health tracking ──────────────────────────────────────────────────

    @Test
    fun `successful search marks the source healthy`() = runTest {
        orchestrator.discover("https://mangadex.org")
        heuristic.searchHandler = { emptyList() }

        orchestrator.search("https://mangadex.org", "q", 1)

        val cached = orchestrator.getAllProfiles().first { it.baseUrl == "https://mangadex.org" }
        cached.verified shouldBe true
        cached.failureCount shouldBe 0
    }

    @Test
    fun `failed search increments failure count`() = runTest {
        orchestrator.discover("https://mangadex.org")
        heuristic.searchHandler = { throw IllegalStateException("boom") }

        orchestrator.search("https://mangadex.org", "q", 1)

        val cached = orchestrator.getAllProfiles().first { it.baseUrl == "https://mangadex.org" }
        cached.failureCount shouldBe 1
        cached.verified shouldBe false
    }

    // ── profile management ───────────────────────────────────────────────

    @Test
    fun `getAllProfiles returns cached profiles`() = runTest {
        orchestrator.discover("https://mangadex.org")
        orchestrator.discover("https://manganato.com")

        val all = orchestrator.getAllProfiles()
        assertEquals(2, all.size)
    }

    @Test
    fun `invalidateProfile removes the cached profile`() = runTest {
        orchestrator.discover("https://mangadex.org")
        orchestrator.invalidateProfile("https://mangadex.org")

        orchestrator.getAllProfiles().firstOrNull { it.baseUrl == "https://mangadex.org" }.shouldBeNull()
    }

    @Test
    fun `setSourceEnabled toggles the source enabled flag`() = runTest {
        orchestrator.discover("https://mangadex.org")

        val disabled = orchestrator.setSourceEnabled("https://mangadex.org", false).getOrThrow()
        disabled.enabled shouldBe false
    }
}
