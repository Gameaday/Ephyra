package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Whether a page list can still yield a usable image address.
 *
 * **These strings are copied verbatim from device reports**, not written to describe a case. Every
 * earlier failure here came from a fixture shaped like what I assumed the source did rather than what
 * it actually emitted.
 */
class PageListFreshnessTest {

    private val base = "https://mangadex.org"

    /**
     * The caller's identity must reach the failure message.
     *
     * **Why this exists.** `resolvePageImage` took a `listOrigin` parameter and never passed it to
     * the reporter, so every report read `not reported by this caller` — including the ones the
     * downloader had explicitly labelled `download/first attempt` and `download/retry`. Cost several
     * rounds of working out which component had failed, from a report that looked like it did not know.
     *
     * Asserted against the composed text rather than the reporter's signature, so a parameter that is
     * accepted and ignored cannot pass again.
     */
    @Test
    fun `the caller's identity survives into the message`() {
        val reported = failureFrom("download/retry")
        assertTrue(reported.contains("download/retry"), "got: $reported")
    }

    /** And it is absent rather than blank when no caller identified itself. */
    @Test
    fun `an unlabelled caller says so instead of claiming something`() {
        val reported = failureFrom(null)
        assertTrue(reported.contains("not reported by this caller"), "got: $reported")
    }

    /** Drives the real path, so a parameter that is accepted and ignored cannot pass this. */
    private fun failureFrom(origin: String?): String = runBlocking {
        val thrown = runCatching { ThrowingChainSource().resolvePageImage(Page(0, "/p/1.jpg", null), origin) }
        thrown.exceptionOrNull()?.message.orEmpty()
    }

    /**
     * A source that implements none of the image-URL chain, which is what a 1.6 extension does —
     * upstream removed that chain from the extension API, so it cannot implement it.
     */
    private class ThrowingChainSource : HttpSource() {
        override val name: String = "Throwing"
        override val lang: String = "en"
        override val baseUrl: String = "https://mangadex.org"
        override val supportsLatest: Boolean = false

        override suspend fun getMangaDetails(manga: SManga): SManga = manga

        override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()

        override suspend fun getImageUrl(page: Page): String =
            throw IllegalStateException("the inherited chain is not implemented by this source")
    }

    private val reportedComposite =
        "https://cmdxd98sb0x3yprd.mangadex.network," +
            "https://api.mangadex.org/at-home/server/733233d4-19fa-4cd9-9e8d-8dbcdfaa5bf4,1791046242118"

    /**
     * The device case, exactly as reported.
     *
     * If this fails, the predicate is wrong — which is a bug of mine, not of the source, and it is why
     * this test exists rather than a description of the rule.
     */
    @Test
    fun `the reported composite beside a blank address is stale`() {
        val pages = listOf(Page(0, reportedComposite, null))
        assertTrue(
            pages.needsFreshPageList(base),
            "the reported list must be refetched, or the downloader keeps resolving a cache key",
        )
    }

    /** Same string, populated: judged on its own merits, and still unusable. */
    @Test
    fun `the reported composite as an address is stale`() {
        val pages = listOf(Page(0, "/page/1.jpg", reportedComposite))
        assertTrue(pages.needsFreshPageList(base))
    }

    /** The counterweight: a lazy source's relative url must NOT trigger a refetch. */
    @Test
    fun `a blank address beside a relative url is not stale`() {
        assertFalse(listOf(Page(0, "/page/1.jpg", null)).needsFreshPageList(base))
    }

    /** And the fully-populated healthy case. */
    @Test
    fun `a healthy list is not stale`() {
        val pages = listOf(Page(0, "/page/1.jpg", "https://cdn.example.com/1.jpg"))
        assertFalse(pages.needsFreshPageList(base))
    }
}
