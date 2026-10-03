package ephyra.app.architecture

import ephyra.app.security.TrackedFileNames
import ephyra.core.common.util.network.TransientErrors
import eu.kanade.tachiyomi.network.HttpException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.io.IOException
import java.net.UnknownHostException

/**
 * Structural gate for **DEF-023**: a page whose image URL is at fault must be re-loaded with a
 * *different* URL, by the reader's own Retry as well as by its internal ladder.
 *
 * **The defect this prevents.** `HttpPageLoader` decided "is this URL the problem?" with a local
 * attempt counter — re-resolve on the second and later attempts. A counter cannot answer that
 * question, and it answered it wrongly at both ends:
 *
 * - it re-resolved after a `429`, where the same URL is correct and re-resolving costs an extra
 *   source round-trip, and
 * - it did **not** re-resolve on the first attempt of a user-initiated Retry, because a fresh
 *   `internalLoadPage` call starts its counter at zero and `prepareForReload` kept the failed URL.
 *   So the Retry button re-sent the request that had just failed. For a host that does not resolve
 *   that is a permanent failure the user cannot get out of — "even after retry" exactly.
 *
 * The rule now lives in one place, `TransientErrors.shouldReResolveUrl`, which is the lesson
 * `DEF-021` already recorded for the retry classification itself: two answers to "is this
 * retryable", in two modules, with no compiler enforcing agreement, is how the reader and the
 * downloader drifted.
 *
 * **Why a structural gate and not a behavioural test.** The same reason
 * `PageRequeueStructuralTest` gives: `HttpPageLoader` needs a live `Source`, a `ChapterCache`
 * backed by a real Coil `DiskCache` and `Dispatchers.IO`, and a JVM test of the reload path that
 * never reaches its assertion is the blind-gate failure this programme has hit repeatedly. What
 * *is* cheap to assert behaviourally is the policy itself — `TransientErrors` is a pure JVM object
 * — and that is asserted below directly. What cannot be reached is the wiring, so the wiring is
 * checked structurally: the property that broke is "the loader asks the shared classifier, and
 * never a counter".
 *
 * **What it does not claim.** It does not prove a page recovers on a device. It proves the retry
 * asks the source for a new URL whenever the classifier says the URL is at fault, and that the
 * cache-eviction reload — where the URL was never implicated — still keeps it.
 */
class PageRetryUrlPolicyStructuralTest {

    @Test
    fun `the loader asks the shared classifier, never a local attempt counter`() {
        val text = loader().readText()

        assertTrue(
            RECLASSIFIER.containsMatchIn(text),
            "HttpPageLoader must route its re-resolve decision through " +
                "TransientErrors.shouldReResolveUrl; a private rule here is DEF-021 recurring",
        )
        assertTrue(
            !COUNTER_HEURISTIC.containsMatchIn(text),
            "`retries > 0` re-resolves on the wrong attempts: after a 429 the same URL is correct, " +
                "and on the first attempt of a user Retry the failed URL is re-requested as-is. " +
                "That is DEF-023",
        )
    }

    @Test
    fun `both the internal ladder and the user retry re-resolve`() {
        // Two distinct decisions, and they are the two that drifted apart: the loop's own next
        // attempt, and the reload the user triggers by pressing Retry.
        val decisions = RECLASSIFIER.findAll(loader().readText()).count()
        assertTrue(
            decisions >= 2,
            "the re-resolve decision must be taken both inside the retry ladder and when a reload " +
                "is requested; found $decisions in HttpPageLoader.kt",
        )
    }

    @Test
    fun `a hand-written override of the policy is a drift risk`() {
        // `dropImageUrl = true`/`false` at a call site would be a private second answer to the same
        // question. The only legitimate calls are the bare default (eviction: the URL was fine) and
        // the classifier's verdict (the URL was not).
        val handRolled = loader().readText()
            .lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("dropImageUrl = ") }
            .filterNot { it.startsWith("dropImageUrl = TransientErrors.shouldReResolveUrl(") }
            .toList()

        assertTrue(
            handRolled.isEmpty(),
            "a reload must decide about the URL from the shared classifier or leave it alone, " +
                "not by hand:\n" + handRolled.joinToString("\n") { "  $it" },
        )
    }

    @Test
    fun `the eviction reload still keeps the URL it already resolved`() {
        // The counterweight. An eviction is a *local* failure: the bytes are gone but the URL was
        // never at fault, and re-resolving there would silently change which URL a page that was
        // mid-render points at. A fix that dropped the URL everywhere would pass every other test
        // in this class and still be wrong.
        val bareReloads = Regex("""prepareForReload\(page\)""").findAll(loader().readText()).count()

        assertTrue(
            bareReloads >= 2,
            "both eviction reloads (the cache-miss check and the stream lambda) must keep the " +
                "resolved URL; found $bareReloads bare prepareForReload(page) call(s)",
        )
    }

    @Test
    fun `the policy itself sends a resolution failure back to the source`() {
        // Behavioural, and the part that actually decides the outcome. The structural gates above
        // only prove the loader asks; this proves what it is asking.
        val resolutionFailure = UnknownHostException(
            "Unable to resolve host \"cmxd98sb0x3yprd.mangadex.network\": No address associated with hostname",
        )

        assertTrue(TransientErrors.shouldReResolveUrl(resolutionFailure), "DEF-023: a host that does not resolve")
        assertTrue(TransientErrors.isTransient(resolutionFailure), "and it is still worth retrying")
        assertFalse(
            TransientErrors.shouldReResolveUrl(IOException("connection reset")),
            "a dropped connection says nothing about the URL, so the same URL must be re-requested",
        )
        assertFalse(
            TransientErrors.shouldReResolveUrl(HttpException(429)),
            "and neither does a rate limit",
        )
    }

    @Test
    fun `the matchers distinguish the forms the loader uses`() {
        // A gate that cannot fail is worse than no gate. These are the exact forms in the file.
        assertTrue(RECLASSIFIER.containsMatchIn("dropImageUrl = TransientErrors.shouldReResolveUrl(failedWith)"))
        assertTrue(!RECLASSIFIER.containsMatchIn("dropImageUrl = true"))
        assertTrue(COUNTER_HEURISTIC.containsMatchIn("} else if (retries > 0) {"))
        assertTrue(
            !COUNTER_HEURISTIC.containsMatchIn("if (retries < MAX_PAGE_LOAD_RETRIES) {"),
            "the ladder's own bound mentions `retries` and must not read as the re-resolve heuristic",
        )
    }

    @Test
    fun `a dropped address is replaced, never cleared`() {
        // The defect this prevents, twice over. `dropUrl` means "this URL is at fault", and both the
        // reader and the downloader acted on it by setting `Page.imageUrl = null`. For a source that
        // populates `Page.imageUrl` in `getPageList` — every 1.6 extension, since upstream removed
        // the per-page chain — a page with no address is asked for one via `getImageUrl`, which that
        // source does not implement. The inherited default runs and throws.
        //
        // Dropping the URL was correct; a dead MangaDex@Home token must not be reused. There was
        // simply no way back from having dropped it.
        val offenders = filesThatClearAnAddress()
        assertTrue(
            offenders.isEmpty(),
            "clearing Page.imageUrl leaves the page unrecoverable for any source that populates it. " +
                "Supply a replacement from a page list instead:\n" +
                offenders.joinToString("\n") { "  $it" },
        )
    }

    @Test
    fun `every module that drops an address supplies one`() {
        // The counterweight to the rule above: a file that *never* drops is not evidence the rule is
        // satisfied, it is just a file that has not hit the case yet. Both current sites must appear,
        // so deleting one cannot make this pass.
        val sites = listOf(
            "feature/reader/src/main/kotlin/ephyra/feature/reader/loader/HttpPageLoader.kt",
            "core/download/src/main/kotlin/ephyra/core/download/Downloader.kt",
        )
        val dropping = sites.filter { path ->
            val file = File(TrackedFileNames.repositoryRoot(), path)
            file.exists() && DROPS_ADDRESS.containsMatchIn(file.readText())
        }
        assertEquals(2, dropping.size, "expected both the reader and the downloader to drop addresses")
    }

    /** Files that clear `Page.imageUrl` without putting something in its place. */
    private fun filesThatClearAnAddress(): List<String> = SEVERAL_DROPS_SITES.mapNotNull { path ->
        val file = File(TrackedFileNames.repositoryRoot(), path)
        if (!file.exists()) return@mapNotNull null
        val offending = file.readText().lineSequence()
            .map { it.trim() }
            .filter { CLEARS_ADDRESS.containsMatchIn(it) }
            .filterNot { SUPPLIES_REPLACEMENT.containsMatchIn(it) }
            .toList()
        offending.takeIf { it.isNotEmpty() }?.let { "$path:\n" + it.joinToString("\n") { "    $it" } }
    }

    @Test
    fun `the matchers distinguish a clear from a replacement`() {
        // A gate that cannot fail is worse than none. These are the exact forms in the tree.
        assertTrue(CLEARS_ADDRESS.containsMatchIn("page.imageUrl = null"))
        assertTrue(SUPPLIES_REPLACEMENT.containsMatchIn("page.imageUrl = freshAddresses.at(page.index)"))
        assertTrue(SUPPLIES_REPLACEMENT.containsMatchIn("page.imageUrl = download.source.freshPage(page)"))
        assertFalse(CLEARS_ADDRESS.containsMatchIn("page.imageUrl = freshAddresses.at(page.index)"))
        assertFalse(CLEARS_ADDRESS.containsMatchIn("page.imageUrl = ResolvedImageUrl.of(value, baseUrl)"))
    }

    private fun loader(): File = File(
        TrackedFileNames.repositoryRoot(),
        "feature/reader/src/main/kotlin/ephyra/feature/reader/loader/HttpPageLoader.kt",
    ).also {
        assertTrue(it.exists(), "HttpPageLoader.kt not found at the expected path; the gate would be inert")
    }

    private companion object {
        /** Every module that acts on `dropUrl` today. A new one must be added here deliberately. */
        val SEVERAL_DROPS_SITES = listOf(
            "feature/reader/src/main/kotlin/ephyra/feature/reader/loader/HttpPageLoader.kt",
            "core/download/src/main/kotlin/ephyra/core/download/Downloader.kt",
        )

        /** An address being thrown away: the form that makes a 1.6 page unrecoverable. */
        val CLEARS_ADDRESS = Regex("""\b\w+\.imageUrl\s*=\s*null\b""")

        /** A replacement drawn from a page list — the only place a 1.6 source keeps addresses. */
        val SUPPLIES_REPLACEMENT = Regex("""\b\w+\.imageUrl\s*=\s*(?!null)\S+""")

        /** The decision being acted on, so a file that never drops cannot pass by omission. */
        val DROPS_ADDRESS = Regex("""if\s*\(\s*decision\.dropUrl\s*\)""")

        /** A call to the shared classifier, by name. */
        val RECLASSIFIER = Regex("""TransientErrors\.shouldReResolveUrl\(""")

        /**
         * The attempt counter standing in for the policy: `retries > 0` exactly. The ladder's own
         * bound is `retries < MAX_PAGE_LOAD_RETRIES`, which does not match, so this cannot fire on a
         * correct implementation.
         */
        val COUNTER_HEURISTIC = Regex("""retries\s*>\s*0""")
    }
}
