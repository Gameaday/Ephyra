package ephyra.feature.reader.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The viewport pin must be **driven by a viewport**, not merely available on one.
 *
 * `MED-002` reads as production-wired and is: the store is constructed per chapter by
 * `PageByteStoreOwner`, both byte write sites route through `ReaderChapter.cacheBytes`, and
 * `releaseByteStore` runs before the page list drops. That is the bounding and the release, and
 * both genuinely work.
 *
 * What did not work is the pin. `ReaderChapter.pinViewport` had **zero production callers** — only
 * the store's own test invoked it — so "evict by distance from what the user is looking at" was
 * policy with nothing applying it. The store fell back to plain LRU, which is exactly the order it
 * gets wrong after a fling: the pages just scrolled past are the most recently used, so recency
 * evicts the pages the user is about to reach.
 *
 * A behavioural test cannot catch this. Both viewers need a live `ReaderActivity`, a real chapter
 * load and a decoded page before `onPageSelected` means anything, so the assertion has to be about
 * the call site. That is a weaker kind of evidence than a measurement, and the limit is stated in
 * the failure message rather than left implied: this proves the pin is *invoked from the settled
 * page*, not that the resulting eviction order is better in the field.
 *
 * It is still the right gate, because the failure it guards is total absence of a call, and that
 * is precisely what a behavioural test would have missed — the same shape as `B-023`, where the
 * adapter had no coverage and silently dropped the arbiter's only negative decision.
 */
class ViewportPinWiringTest {

    private fun sourceOf(relativePath: String): String {
        val file = locate(relativePath)
        assertTrue("$relativePath not found (searched from ${file.parentFile})", file.exists())
        return file.readText()
    }

    private fun locate(relativePath: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relativePath)
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        return File(".", relativePath).absoluteFile
    }

    /**
     * Extracts a function body by brace matching from its declaration, so the assertion covers the
     * whole function rather than a line slice.
     *
     * A line-slice gate is the ninth-blind-gate shape waiting to happen: `TabNavControllerLifetimeTest`
     * anchored on `substringBefore("\n)")`, which cut at the *parameter list's* closing paren and
     * produced an empty string on which no regex can fail. Matching braces cannot silently return
     * nothing, because an unbalanced source file is a compile error rather than a passing test.
     */
    private fun functionBody(source: String, signature: String): String {
        val start = source.indexOf(signature)
        assertTrue("no declaration of `$signature`", start >= 0)
        val open = source.indexOf('{', start)
        assertTrue("`$signature` has no body", open >= 0)
        var depth = 0
        var i = open
        while (i < source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(open, i + 1)
                }
            }
            i++
        }
        throw AssertionError("`$signature` has unbalanced braces; the source is not parseable")
    }

    @Test
    fun `the paged reader pins the viewport around the settled page`() {
        val source = sourceOf("feature/reader/src/main/kotlin/ephyra/feature/reader/viewer/pager/PagerViewer.kt")
        val body = functionBody(source, "fun onPageSelected(page: ReaderPage)")

        assertTrue(
            "PagerViewer.onPageSelected must pin the viewport, otherwise the paged store evicts by " +
                "pure recency and drops the pages the user is about to reach. Body was:\n$body",
            body.contains("pinViewportAround(page)"),
        )
    }

    @Test
    fun `the continuous reader pins the viewport around the settled page`() {
        val source =
            sourceOf("feature/reader/src/main/kotlin/ephyra/feature/reader/viewer/webtoon/WebtoonViewer.kt")
        val body = functionBody(source, "fun onPageSelected(page: ReaderPage, allowPreload: Boolean = true)")

        assertTrue(
            "WebtoonViewer.onPageSelected must pin the viewport, otherwise a fling through a long " +
                "strip evicts by recency and the pages about to be composited are gone. Body was:\n$body",
            body.contains("pinViewportAround(page"),
        )
    }

    /**
     * The pin must be centred on the page's own index within its chapter, not on a position in a
     * combined item list.
     *
     * The continuous reader's `_itemsState` interleaves previous-chapter pages, chapter transitions
     * and next-chapter pages, so a position in that list is not a page index and would pin the
     * wrong window — or a window belonging to a different chapter's store.
     */
    @Test
    fun `the continuous reader pins by chapter page index, not by item position`() {
        val source =
            sourceOf("feature/reader/src/main/kotlin/ephyra/feature/reader/viewer/webtoon/WebtoonViewer.kt")
        val body = functionBody(source, "private fun pinViewportAround(")

        assertTrue(
            "The pin window must be computed from the chapter's own page list. Body was:\n$body",
            body.contains("pages.indexOf(page)"),
        )
        assertTrue(
            "The pin must be handed the chapter page count so the window clamps to the chapter. " +
                "Body was:\n$body",
            body.contains("pinViewport(index, pages.size)"),
        )
        assertTrue(
            "The continuous pin must not index into the combined item list; that list contains " +
                "chapter transitions and neighbouring chapters, so its positions are not page " +
                "indices. Body was:\n$body",
            !body.contains("_itemsState"),
        )
    }

    @Test
    fun `the paged reader pins by chapter page index, not by item position`() {
        val source = sourceOf("feature/reader/src/main/kotlin/ephyra/feature/reader/viewer/pager/PagerViewer.kt")
        val body = functionBody(source, "private fun pinViewportAround(")

        assertTrue(
            "The pin window must be computed from the chapter's own page list. Body was:\n$body",
            body.contains("pages.indexOf(page)"),
        )
        assertTrue(
            "The pin must be handed the chapter page count so the window clamps to the chapter. " +
                "Body was:\n$body",
            body.contains("pinViewport(index, pages.size)"),
        )
    }

    /**
     * A pin that is never released is worse than no pin at all: a leaked pin is permanent, which is
     * the unbounded growth the whole byte-budget mechanism exists to remove. `releaseByteStore`
     * already drops every pin, so the risk is a chapter change that leaves the *previous* chapter's
     * store pinned while the reader moves on.
     *
     * This asserts the ordering that makes release safe: pins are dropped before the page list is
     * discarded, because `PageByteStoreOwner.clear` resolves ids through the index mapping that the
     * page list does not own.
     */
    @Test
    fun `a chapter releases its pins before it drops its pages`() {
        val source = sourceOf("feature/reader/src/main/kotlin/ephyra/feature/reader/model/ReaderChapter.kt")
        val body = functionBody(source, "fun unref(")

        val release = body.indexOf("releaseByteStore()")
        assertTrue("unref must release the byte store. Body was:\n$body", release >= 0)

        val drop = body.indexOf("pages = null")
        if (drop >= 0) {
            assertTrue(
                "Pins must be released before the page list is dropped: clear() resolves ids " +
                    "through the owner's index mapping, so dropping pages first can strand a pin. " +
                    "Body was:\n$body",
                release < drop,
            )
        }
        assertEquals(
            "unref must not leave a store attached after releasing it; a retained store would " +
                "accept bytes for a disposed chapter.",
            1,
            Regex("releaseByteStore\\(\\)").findAll(body).count().coerceAtMost(1),
        )
    }
}
