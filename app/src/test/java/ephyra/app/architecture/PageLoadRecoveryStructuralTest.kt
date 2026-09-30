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
            RE_RESOLVE.containsMatchIn(text),
            "a URL the recovery decision drops must be replaced with a freshly resolved one, or " +
                "the retry re-requests the address that just failed",
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
                    val line = text.take(match.range.first).count('\n') + 1
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
     * They are the same string today — the loader assigns one from the other two lines earlier — but
     * nothing enforces it. If resolution ever moves so the two can differ, bytes are written under
     * one key having been fetched from another, which presents as random cache misses and a page
     * that re-downloads what it already has, with no error anywhere.
     */
    @Test
    fun `the cache key and the requested URL are the same string in the loader`() {
        val text = code("feature/reader/src/main/kotlin/ephyra/feature/reader/loader/HttpPageLoader.kt")

        assertTrue(
            ASSIGNED_BEFORE_USE.containsMatchIn(text),
            "page.imageUrl must be assigned from the resolved value and the local read back from " +
                "the page, so the disk-cache key and HttpSource.imageRequest cannot disagree",
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
     * version of [no_module_carries_its_own_backoff] matched this very file's comment quoting the
     * old `(2L shl attempt) * 1000`, so it was red against correct code.
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
        val RE_RESOLVE = Regex("""ImageUrlPolicy\.resolve\(\s*source\.getImageUrl\(page\)""")
        /**
         * A `delay(...)` whose argument *computes* a delay: a bit shift, or a named schedule
         * constant. `delay(decision.delayMs)` and `delay(250)` are both fine and do not match —
         * the first reads the delay from the owner, the second is a fixed UI tick.
         */
        val COMPUTED_DELAY = Regex(
            """delay\([^)]*(?:\bshl\b|\b[A-Z][A-Z_]*(?:DELAY|BACKOFF)[A-Z_]*\b)""",
            RegexOption.DOT_MATCHES_ALL,
        )
        val ASSIGNED_BEFORE_USE = Regex(
            """page\.imageUrl\s*=\s*ImageUrlPolicy\.resolve\(page\.imageUrl.*\)""" +
                """\s*\R\s*val imageUrl\s*=""",
            RegexOption.DOT_MATCHES_ALL,
        )
    }
}
