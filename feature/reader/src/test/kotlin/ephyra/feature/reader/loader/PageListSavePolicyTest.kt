package ephyra.feature.reader.loader

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the rule `HttpPageLoader.recycle` uses to decide whether the page list has to be written
 * back to the chapter cache.
 *
 * **The hole this closes (`DEF-023`).** The decision used to be the `cacheHadMissingImageUrls`
 * flag alone, which answers "was the list complete when it was *loaded*?". A page drops its image
 * URL mid-session precisely when that URL is the thing that failed — a revoked signed URL, or an
 * image host that does not resolve — and a flag describing the past cannot see that. So a dead URL
 * stayed in the persisted page list, and every subsequent open of the chapter began by spending a
 * request on a host already known to be dead, before the retry ladder could rescue it.
 *
 * Pure function of two values, so it is asserted by running it rather than by reading the loader.
 */
class PageListSavePolicyTest {

    @Test
    fun `a URL dropped this session is persisted`() {
        // The case the flag alone missed: the list was complete on load, and a page then lost its
        // URL to a failure that indicted it.
        assertTrue(
            HttpPageLoader.needsPageListSave(
                cacheHadMissingImageUrls = false,
                imageUrls = listOf("https://cdn.example/1.jpg", null, "https://cdn.example/3.jpg"),
            ),
            "a page that dropped its URL has changed what the cache holds, so the list must be saved",
        )
    }

    @Test
    fun `an empty URL counts as missing, exactly as at load time`() {
        // The two conditions must mean the same thing, or a session could decide differently from
        // the load that seeded it.
        assertTrue(HttpPageLoader.needsPageListSave(false, listOf("https://cdn.example/1.jpg", "")))
    }

    @Test
    fun `a list that was already incomplete on load is saved`() {
        assertTrue(HttpPageLoader.needsPageListSave(true, listOf("https://cdn.example/1.jpg")))
    }

    @Test
    fun `a fully resolved list that nothing changed is not rewritten`() {
        // The counterweight: this is the redundant write the whole condition exists to avoid, and a
        // rule that always saves would be correct and useless.
        assertFalse(
            HttpPageLoader.needsPageListSave(
                cacheHadMissingImageUrls = false,
                imageUrls = listOf("https://cdn.example/1.jpg", "https://cdn.example/2.jpg"),
            ),
        )
    }

    @Test
    fun `an empty page list still saves when the loaded list was incomplete`() {
        // `getPages` may never have run, and `cacheHadMissingImageUrls` starts true precisely so
        // that case is conservative rather than a silent skip.
        assertTrue(HttpPageLoader.needsPageListSave(true, emptyList()))
        assertFalse(
            HttpPageLoader.needsPageListSave(false, emptyList()),
            "nothing resolved and nothing to write: the flag said the list was already complete",
        )
    }
}
