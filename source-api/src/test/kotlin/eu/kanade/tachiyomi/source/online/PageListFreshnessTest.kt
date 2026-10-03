package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.model.Page
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
