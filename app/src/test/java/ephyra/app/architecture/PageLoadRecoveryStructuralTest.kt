package ephyra.app.architecture

import ephyra.app.security.TrackedFileNames
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Structural gates for `DEF-028`: one owner for the page-load recovery decision, and no second
 * copy of it.
 *
 * **What the defect was.** "A page image failed; what now?" lived in two modules. The reader dropped
 * a URL the classifier indicted and asked the source for a different one; the downloader consulted
 * the same classifier and then retried the identical string anyway. `TransientErrors` was already
 * shared — that is `DEF-021`'s fix — but only the *classification* was. The action, the budget and
 * the backoff were still two copies, and they had drifted: 1s/2s/4s in the reader against
 * 2s/4s/8s in the downloader, and no jitter in either. The consequence was that a chapter with
 * short-lived signed image URLs read successfully and failed to download.
 *
 * **Why structural, for the downloader half.** `Downloader` needs a `Context`, a `UniFile` root, a
 * `WorkManager` and a live `ChapterCache`; a JVM test of the retry path would stand up all four to
 * assert one string, and a test that never reaches its assertion is the blind-gate failure this
 * programme has hit repeatedly. The reader half *is* covered behaviourally, by
 * `HttpPageLoaderCdnSwapTest`, and the shared decision is covered by `PageLoadRecoveryTest` — so
 * what is left to assert here is the one thing neither can see: that the downloader asks the owner
 * rather than deciding for itself.
 *
 * **What it does not claim.** It does not prove a download recovers. It proves the downloader
 * stopped carrying its own answer, which is the half that silently diverged.
 */
class PageLoadRecoveryStructuralTest {

    @Test
    fun `the downloader asks the shared owner instead of deciding for itself`() {
        val text = code("core/download/src/main/kotlin/ephyra/core/download/Downloader.kt")

        assertTrue(
            PAGE_LOAD_RECOVERY.containsMatchIn(text),
            "the downloader must route its retry through PageLoadRecovery, or it is a second copy " +
                "of the rule that already drifted once",
        )
    }

    /**
     * The re-resolve itself, which is the behaviour the drift removed. Without it the downloader
     * holds the decision but still does nothing with it — the same shape as before, with more
     * indirection in front of it.
     */
    @Test
    fun `the downloader replaces a URL its own classifier indicted`() {
        val text = code("core/download/src/main/kotlin/ephyra/core/download/Downloader.kt")

        assertTrue(
            REPLACES_FROM_PAGE_LIST.containsMatchIn(text),
            "a URL the recovery decision drops must be replaced from a fresh page list — the only " +
                "place a source populating Page.imageUrl keeps them — or the retry re-requests the " +
                "address that just failed, or asks getImageUrl for a call this source does not have",
        )
    }

    /**
     * The reader does the same, and additionally flags rather than clears.
     *
     * The flag matters because `retryPage` is not a suspending function: it cannot fetch anything,
     * so clearing there would leave the page unrecoverable before any suspending code runs. It marks
     * the page instead, and `loadPage` replaces the address on the way in — inside a `runCatching`,
     * so a failed replacement fetch (network I/O on the viewer's own coroutine) fails the *page*
     * rather than the reader.
     */
    @Test
    fun `the reader replaces a URL its own classifier indicted`() {
        val text = code("feature/reader/src/main/kotlin/ephyra/feature/reader/loader/HttpPageLoader.kt")

        assertTrue(
            text.contains("freshAddresses.at(page.index)"),
            "the reader must draw a replacement from a fresh page list for the same reason the " +
                "downloader does",
        )
        assertTrue(
            text.contains("needsFreshAddress = true"),
            "the non-suspending reload path must flag the page rather than clear its address",
        )
        // The reader's replacement fetch is network I/O on the viewer's coroutine: unguarded, an
        // exception escaping it killed the reader activity (reported as a dialog and a kick back
        // to the series screen). It must stay guarded.
        assertTrue(
            text.contains("runCatching { freshAddresses.at("),
            "the reader's fresh-address fetch must not be able to escape loadPage and kill the " +
                "reader; a failed replacement falls back to the recovery ladder",
        )
    }

    /**
     * A backoff schedule in either module would be a third schedule. The delay has to come from the
     * owner, or the two drift again the moment either is tuned.
     *
     * Matched on *computing* a delay rather than on the two shapes these modules happened to use.
     * The first version of this gate looked for `shl attempt` and `shl retries` — the exact
     * expressions that drifted — and a probe that wrote a fresh `(2L shl 1) * 1000` sailed straight
     * through it. A gate pinned to past syntax passes the first time someone writes it differently,
     * which is the only time it matters.
     */
    @Test
    fun `no module computes its own retry delay`() {
        val offenders = TrackedFileNames.inMainSources()
            .filter { it.endsWith(".kt") }
            .filter { it.contains("/reader/") || it.contains("/download/") }
            .map { it to stripComments(File(TrackedFileNames.repositoryRoot(), it).readText()) }
            .flatMap { (path, text) ->
                COMPUTED_DELAY.findAll(text).map { match ->
                    val line = text.take(match.range.first).count { it == '\n' } + 1
                    "$path:$line  ${match.value.replace(Regex("\\s+"), " ")}"
                }
            }
            .toList()

        assertTrue(
            offenders.isEmpty(),
            "the retry delay belongs to PageLoadRecovery. A module computing its own has its own " +
                "schedule, and the reader's and the downloader's already disagreed:\n" +
                offenders.joinToString("\n") { "  $it" },
        )
    }

    /**
     * The invariant that has no name and no test: the disk-cache key and the URL actually requested
     * come from two different places. `fetchAndCacheImage(imageUrl) { source.getImage(page) }` keys
     * on the argument and fetches through `page.imageUrl`.
     *
     * They must remain the same string — the value read back off the page, unmodified. The
     * original version of this test required the loader to *resolve* `page.imageUrl` through
     * `PageImageAddress` before fetching, and that requirement is the defect it now exists to
     * prevent: a populated `Page.imageUrl` is opaque to the host. MangaDex stores a **relative
     * path** there and its own overridden `imageRequest` joins it onto an at-home host read from
     * `Page.url`; a loader-side baseUrl-join produced
     * `"<at-home-host>https://mangadex.org/data/..."` — a host that can never resolve — and every
     * page of every MangaDex chapter failed identically. Upstream Mihon never writes into a
     * populated `Page.imageUrl`, and resolution belongs at the request boundary
     * (`HttpSource.imageRequest`), where a source override inherits nothing by design.
     */
    @Test
    fun `the cache key and the requested URL are the same string in the loader`() {
        val text = code("feature/reader/src/main/kotlin/ephyra/feature/reader/loader/HttpPageLoader.kt")

        // Read back off the page, so the cache key and the request cannot disagree.
        assertTrue(
            text.indexOf("requireNotNull(page.imageUrl)") >= 0,
            "the URL used for the cache key and the request must be read back off the page, not " +
                "kept in a local that could disagree with it",
        )

        // And never rewritten once populated: the loader resolves nothing against `baseUrl` on
        // the image path. `PageImageAddress` has no business in this file at all.
        assertTrue(
            !text.contains("PageImageAddress"),
            "the loader must not resolve a page's image URL against baseUrl. `Page.imageUrl` is " +
                "opaque to the host once populated — a source's own imageRequest (MangaDex's " +
                "joins an at-home host onto a relative path) interprets it, and a loader-side " +
                "resolve-and-write-back splices two URLs into a host that can never resolve. " +
                "Resolution belongs at the request boundary in HttpSource.imageRequest.",
        )
    }

    /**
     * The passthrough that makes the loader's rule enforceable: a populated `Page.imageUrl` is
     * handed on untouched by [resolvePageImage] — the one owner of the resolution rules — rather
     * than resolved against `baseUrl`.
     *
     * The reader and the downloader both assign `resolvePageImage(...).value` back into the page,
     * so if the owner resolved a populated field, both consumers would corrupt it for any source
     * that overrides `imageRequest`. This pins the opaque passthrough (`ResolvedImageUrl.opaque`)
     * so the rewrite cannot come back through the shared owner after being removed from the
     * loader.
     */
    @Test
    fun `a populated page imageUrl is passed through opaquely, never baseUrl-resolved`() {
        val text = code("source-api/src/main/kotlin/eu/kanade/tachiyomi/source/online/PageImageAddress.kt")

        assertTrue(
            text.contains("ResolvedImageUrl.opaque(populated)"),
            "resolvePageImage must pass a populated Page.imageUrl through as opaque " +
                "(ResolvedImageUrl.opaque). Resolving it against baseUrl splices a second host " +
                "onto a value the source's own imageRequest interprets — the reported MangaDex " +
                "failure in which every page of every chapter requested a host that could " +
                "never resolve.",
        )
    }

    /**
     * The pacing guard, and the regression it was written after.
     *
     * `ReResolvePacer` was first called on *every* URL resolution rather than only on a
     * re-resolution. That put up to 400ms onto the load of any page — including the one the user is
     * waiting for, which competes with the preload window for the same few workers — to solve a
     * problem that only exists after something has already failed. Measured on a six-page preload
     * window it added 1.2s.
     *
     * The behavioural answer is `PageLoadRecovery.isRetrySequence`, and this gate is the structural
     * one: the pacing call may not appear in either module without that guard around it. A regression
     * here is invisible in review — the line looks harmless, and nothing about it is wrong until it
     * is measured — which is exactly the class of defect a gate is for.
     */
    @Test
    fun `pacing a resolution is only reachable behind the retry-sequence guard`() {
        val offenders = TrackedFileNames.inMainSources()
            .filter { it.endsWith(".kt") }
            .filter { it.contains("/reader/") || it.contains("/download/") }
            .map { it to stripComments(File(TrackedFileNames.repositoryRoot(), it).readText()) }
            .filter { (_, text) -> PACED_RESOLUTION.containsMatchIn(text) }
            .filter { (_, text) -> !RETRY_GUARD.containsMatchIn(text) }
            .map { (path, _) -> path }

        assertTrue(
            offenders.isEmpty(),
            "a resolution may only be paced when the load has already failed. Pacing a page's first " +
                "resolution taxes every chapter open -- and the page the user is waiting on most of " +
                "all -- to solve a failure-path problem. Guard it with " +
                "PageLoadRecovery.isRetrySequence. Files that pace without it:\n" +
                offenders.joinToString("\n") { "  $it" },
        )
    }

    private fun source(path: String): String =
        File(TrackedFileNames.repositoryRoot(), path).readText()

    /**
     * Source with comments removed, because a gate that trips on a comment *explaining what it
     * forbids* is a gate that gets deleted rather than obeyed. Found the hard way: the first
     * version of `no module computes its own retry delay` matched this very file's comment
     * quoting the old `(2L shl attempt) * 1000`, so it was red against correct code.
     *
     * Line comments are removed before block comments, so a `//` inside a block comment cannot
     * unbalance the two. String literals are not handled, and cannot be: doing that correctly needs
     * a Kotlin lexer, and the cost is not worth it when a false positive is visibly a false
     * positive — a real schedule in a string would be a bug someone would have to write on purpose.
     */
    private fun code(path: String): String = stripComments(source(path))

    private fun stripComments(text: String): String {
        val withoutLines = text.lineSequence().joinToString("\n") { line ->
            val comment = line.indexOf("//")
            if (comment >= 0) line.substring(0, comment) else line
        }
        return withoutLines
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("""^\s*\*.*$""", RegexOption.MULTILINE), " ")
    }

    private companion object {
        val PAGE_LOAD_RECOVERY = Regex("""\bPageLoadRecovery\b""")
        val PACED_RESOLUTION = Regex("""\.paceReResolution\(""")
        val RETRY_GUARD = Regex("""\bisRetrySequence\b""")

        /**
         * The re-resolve that replaces an indicted URL.
         *
         * Matched on the *pair* — asking the source for a fresh address, and putting it through a
         * resolution that judges it — rather than on one call, because either alone is gameable: the
         * bare `getImageUrl(page)` also appears in the first-load path, which replaces nothing, and
         * `ResolvedImageUrl.of` appears at two other sites. This shape was rewritten when the
         * pipeline moved to [ResolvedImageUrl]; the first version of this regex named
         * `ImageUrlPolicy.resolve(source.getImageUrl(page)` and went red against correct code the
         * moment the type did its job, which is the failure mode the comment on [COMPUTED_DELAY]
         * describes. The gate is retargeted at the property, and will need retargeting again if the
         * call is restructured — deliberately louder than silently passing.
         */
        val RE_RESOLVE = Regex(
            """(?:ResolvedImageUrl\.of|ImageUrlPolicy\.resolve)\(\s*[\w.]*\.?getImageUrl\(page\)""",
            RegexOption.DOT_MATCHES_ALL,
        )

        /**
         * A replacement address drawn from a page list rather than from a per-page call.
         *
         * **Why this replaced `getImageUrl` as the thing to look for.** Replacing a URL the
         * classifier indicted used to mean "ask the source for another address for this page". That
         * is wrong for a source populating `Page.imageUrl` in `getPageList` — every 1.6 extension —
         * which implements no per-page call at all, so the inherited default throws. The addresses
         * exist only in a page list, so that is where the replacement has to come from.
         *
         * The `getImageUrl` form is still matched below, as the *shape that must not be relied on*.
         */
        val REPLACES_FROM_PAGE_LIST = Regex("""\.imageUrl\s*=\s*\w*fresh\w*\.at\(""", RegexOption.DOT_MATCHES_ALL)

        /**
         * A `delay(...)` whose argument *computes* a delay: a bit shift, or a named schedule
         * constant. `delay(decision.delayMs)` and `delay(250)` are both fine and do not match —
         * the first reads the delay from the owner, the second is a fixed UI tick.
         */
        val COMPUTED_DELAY = Regex(
            """delay\([^)]*(?:\bshl\b|\b[A-Z][A-Z_]*(?:DELAY|BACKOFF)[A-Z_]*\b)""",
            RegexOption.DOT_MATCHES_ALL,
        )

        // **Retired with the loader-side rewrite it pinned.** `ASSIGNED_BEFORE_USE` asserted that the
        // loader resolved `page.imageUrl` and read it back — the pairing that, correctly applied to a
        // *populated* source field, spliced two URLs together and broke MangaDex completely (see
        // `doc/EXTENSION_COMPATIBILITY.md`, "`Page.imageUrl` is opaque to the host once populated").
        // The invariant it gestures at survives in the test above as its shadow: the cache key and
        // the request are the same *raw* string read back off the page, and the loader resolves
        // nothing. A regex cannot express "nothing writes into this field", so that is asserted
        // directly — `!text.contains("PageImageAddress")` — instead of pinning the syntax of an
        // assignment that must no longer exist. Kept as a comment rather than deleted so the next
        // gate author reads why pinning an assignment's *syntax* is the wrong shape here.
    }
}
