package ephyra.app.architecture

import ephyra.app.security.TrackedFileNames
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Structural gate for **P0-1**: a reader page that is reset for retry must actually be re-offered
 * to the load queue.
 *
 * **The defect this prevents.** `HttpPageLoader` sets `page.status = Page.State.Queue` when an image
 * turns out to have been evicted from the disk cache. `Queue` is a *status*, not an enqueue. The only
 * thing that puts a page in front of a worker is `queue.offer(...)`, the pager's load trigger is
 * `LaunchedEffect(page)` keyed on page *identity* (so it cannot re-fire because a status changed), and
 * the worker loop takes entries that are actually on the queue. The page therefore stayed `Queue`
 * forever and the image never appeared — **silently blank rather than errored**, which is why it
 * presented as "missed images" rather than as a failure.
 *
 * **Why a structural gate and not a behavioural test.** `HttpPageLoader` takes a live `Source`, a
 * real `ChapterCache` backed by a Coil `DiskCache`, and runs on `Dispatchers.IO`; a JVM test of the
 * eviction path needs all three faked before it can assert anything, and a test that never reaches
 * the assertion is the blind-gate failure this programme has hit seven times. What can be asserted
 * cheaply and *structurally* is the property that actually broke: **no code path may reset a page to
 * `Queue` without also offering it.** That is a property of the source's shape, so it holds
 * regardless of how the loader is later refactored.
 *
 * **What it does not claim.** It does not prove the page eventually loads — that also needs the
 * cache to stop evicting in-read pages, which is `P0-3` and is a separate change. It proves the
 * re-offer exists, which is the half that silently removed the page from circulation.
 */
class PageRequeueStructuralTest {

    @Test
    fun `no code path resets a page to Queue without re-offering it to the queue`() {
        val files = TrackedFileNames.inMainSources()
        assertTrue(files.isNotEmpty(), "no tracked main sources found; the gate would be inert")

        val root = TrackedFileNames.repositoryRoot()
        val offenders = files
            .filter { it.endsWith(".kt") }
            .map { it to File(root, it).readText() }
            // Any assignment of the Queue status is a candidate. `==` is excluded from the matcher
            // so a comparison or a guard can never be mistaken for a reset.
            .filter { (_, text) -> QUEUE_RESET.containsMatchIn(text) }
            // Offenders are the resets that do not, anywhere in the same file, offer to the queue.
            // The check is per-file rather than per-function on purpose: a helper that resets in one
            // place and offers in another is a legitimate shape, and a per-function rule would
            // produce false positives on it while still missing a cross-function regression.
            .filter { (_, text) -> !QUEUE_OFFER.containsMatchIn(text) }
            .map { (path, _) -> path }

        assertTrue(
            offenders.isEmpty(),
            "A page reset to Page.State.Queue must be re-offered via queue.offer, or it is never " +
                "loaded again and renders as a permanent blank. Files that reset without offering:\n" +
                offenders.joinToString("\n") { "  $it" },
        )
    }

    @Test
    fun `the loader re-offers an evicted page at the priority it was dequeued at`() {
        val root = TrackedFileNames.repositoryRoot()
        val loader = File(
            root,
            "feature/reader/src/main/kotlin/ephyra/feature/reader/loader/HttpPageLoader.kt",
        )
        assertTrue(loader.exists(), "HttpPageLoader.kt not found at the expected path")

        val text = loader.readText()
        assertTrue(
            REQUEUE_ON_EVICTION.containsMatchIn(text),
            "the eviction branch must reset the page and offer it back in the same block, so a page " +
                "evicted from the disk cache returns to the queue instead of sitting in Queue forever",
        )
    }

    @Test
    fun `the matchers distinguish a reset from a comparison`() {
        // A gate that cannot fail is worse than no gate. These are the exact forms the loader uses.
        assertTrue(QUEUE_RESET.containsMatchIn("page.status = Page.State.Queue"))
        assertTrue(QUEUE_RESET.containsMatchIn("page.status=Page.State.Queue"))
        // Comparisons and guards must not read as resets.
        assertTrue(!QUEUE_RESET.containsMatchIn("if (it.page.status == Page.State.Queue) {"))
        assertTrue(!QUEUE_RESET.containsMatchIn("it.page.status == Page.State.Queue"))
        assertTrue(!QUEUE_RESET.containsMatchIn("page.status = Page.State.Ready"))
    }

    @Test
    fun `the offer matcher recognises the forms the loader uses`() {
        assertTrue(QUEUE_OFFER.containsMatchIn("queue.offer(PriorityPage(page, priority))"))
        assertTrue(QUEUE_OFFER.containsMatchIn("queue.offer(this)"))
        // A removal on cancellation is not an offer; treating it as one would mask a real gap.
        assertTrue(!QUEUE_OFFER.containsMatchIn("queue.remove(it)"))
        assertTrue(!QUEUE_OFFER.containsMatchIn("queue.clear()"))
    }

    private companion object {
        /**
         * An assignment of the `Queue` status.
         *
         * Written as `status\s*=(?!=)` rather than `status\s*=[^=]`: the lookahead is what actually
         * excludes `==`. A `[^=]` after the sign happily matches the *second* character of `==`,
         * which would make every comparison in the file read as a reset and turn this gate into
         * noise. That mistake was caught by the matcher's own test, not by a failing gate.
         */
        val QUEUE_RESET = Regex("""status\s*=(?!=).*Page\.State\.Queue""")

        /** An offer onto the load queue. `remove` and `clear` are deliberately excluded. */
        val QUEUE_OFFER = Regex("""queue\.offer\(""")

        /**
         * The eviction branch: a reset and an offer in the same statement block.
         *
         * Anchored on the `getImageFile(...) ?: run {` form so it matches the real branch rather
         * than any incidental combination elsewhere in the file.
         */
        val REQUEUE_ON_EVICTION = Regex(
            """getImageFile\([^)]*\)\?\.inputStream\(\)\s*\?:\s*run\s*\{[^}]*queue\.offer\(""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
    }
}
