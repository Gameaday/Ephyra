package ephyra.core.common.util.network

import eu.kanade.tachiyomi.network.HttpException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Pins the one owner of "a page image failed; what now?".
 *
 * **Why the decision needed an owner.** It lived in two places. The reader dropped a URL the
 * classifier indicted and asked the source again; the downloader retried the identical string.
 * They shared `TransientErrors` — which is `DEF-021`'s fix — but only the *classification*. The
 * *action* stayed duplicated, so for any source with short-lived signed image URLs the same chapter
 * read successfully and failed to download. The backoff had drifted too, 1s/2s/4s against 2s/4s/8s,
 * and neither had jitter, so pages that failed together retried together.
 *
 * **The load-bearing constraint.** The request budget is deliberately *unchanged*: still three
 * retries after the first attempt. Giving "this URL is dead, ask again" a larger allowance is
 * tempting, since only the URL changes, and it would raise the worst-case requests per page. Only
 * the delay differs by kind. The tests below assert that ceiling so a future "just add one more
 * retry" is a visible decision rather than a quiet drift.
 */
class PageLoadRecoveryTest {

    private fun recovery(
        maxRetries: Int = 3,
        random: () -> Double = { 0.5 },
    ) = PageLoadRecovery(maxRetries = maxRetries, random = random)

    /**
     * The case the whole extraction exists for: a source whose first resolution names a CDN that
     * refuses, and whose second names a different one. The decision must be to ask again, and the
     * failed URL must be dropped so the retry cannot re-request it.
     */
    @Test
    fun `a 403 asks the source again and drops the URL that was refused`() {
        val recovery = recovery()

        val decision = recovery.onFailure("https://cdn-a.example/1.jpg", HttpException(403))

        assertEquals(PageLoadRecoveryAction.RE_RESOLVE_URL, decision.action)
        assertTrue(decision.dropUrl, "a refused URL must not survive on the page to be persisted")
        assertTrue(decision.delayMs > 0, "a retry must wait, or the refusal is repeated instantly")
    }

    @Test
    fun `a second resolution naming a different host is a different attempt`() {
        val recovery = recovery()
        recovery.onFailure("https://cdn-a.example/1.jpg", HttpException(403))

        val decision = recovery.onFailure("https://cdn-b.example/1.jpg", HttpException(403))

        assertEquals("https://cdn-b.example/1.jpg", recovery.attempts.last().url)
        assertEquals(2, decision.attempt, "the new host must be recorded, not overwrite the first")
    }

    /**
     * The counterweight, and the reason the reader is not simply re-resolving on everything: a
     * dropped connection says nothing about the URL, so the same one is correct and re-resolving
     * would spend a source round-trip to be told the same thing.
     */
    @Test
    fun `a dropped connection retries the same URL`() {
        val decision = recovery().onFailure("https://cdn-a.example/1.jpg", IOException("connection reset"))

        assertEquals(PageLoadRecoveryAction.RETRY_SAME_URL, decision.action)
        assertFalse(decision.dropUrl)
    }

    /**
     * A timeout is retried but the URL is kept, and the distinction is deliberate and load-bearing:
     * there is no portable type that separates a connect-phase timeout from a slow read, and a slow
     * read is not evidence about the host.
     */
    @Test
    fun `a timeout retries the same URL rather than spending a resolution on it`() {
        val decision = recovery().onFailure("https://cdn-a.example/1.jpg", SocketTimeoutException("timeout"))

        assertEquals(PageLoadRecoveryAction.RETRY_SAME_URL, decision.action)
        assertFalse(decision.dropUrl, "a timeout is not evidence that the host is at fault")
    }

    /**
     * The host-at-fault family that was previously missed. Each of these is an `IOException`, so it
     * was already being *retried* — against the identical URL, four times, for a host that a fresh
     * resolution could have replaced. That is the waste this class exists to remove.
     *
     * Named cases rather than `@ValueSource(classes = …)` so a failure reports *which* exception
     * stopped being recognised. `[3] class javax.net.ssl.SSLHandshakeException` is not a diagnostic.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("hostFaults")
    fun `a failure that indicts the host asks the source for a different URL`(error: Exception) {
        val decision = recovery().onFailure("https://cdn-a.example/1.jpg", error)

        assertEquals(PageLoadRecoveryAction.RE_RESOLVE_URL, decision.action)
        assertTrue(decision.dropUrl)
    }

    companion object {
        @JvmStatic
        fun hostFaults(): List<Exception> = listOf(
            UnknownHostException("cdn-a.example"),
            SSLPeerUnverifiedException("no peer certificate"),
            SSLHandshakeException("handshake failed"),
            ConnectException("connection refused"),
        )
    }

    /** A `404` will still be a `404` on a different host, so spending a resolution on it is waste. */
    @ParameterizedTest
    @ValueSource(ints = [400, 401, 404, 422])
    fun `a permanent status gives up immediately and is not re-resolved`(code: Int) {
        val recovery = recovery()
        val decision = recovery.onFailure("https://cdn-a.example/1.jpg", HttpException(code))

        assertEquals(PageLoadRecoveryAction.GIVE_UP, decision.action)
        assertEquals(0, decision.delayMs, "nothing follows a give-up, so it must not ask for a wait")
        assertEquals(1, recovery.attempts.size, "a permanent failure must not be retried")
    }

    /**
     * The budget is the ceiling this change refused to raise. Four attempts total — the first plus
     * three retries — exactly as the reader had before, so extracting the decision cannot have
     * doubled anyone's request count.
     */
    @Test
    fun `the request budget is unchanged at three retries`() {
        val recovery = recovery()

        repeat(3) { recovery.onFailure("https://cdn-a.example/1.jpg", HttpException(403)) }
        val decision = recovery.onFailure("https://cdn-a.example/1.jpg", HttpException(403))

        assertEquals(PageLoadRecoveryAction.GIVE_UP, decision.action)
        assertEquals(4, recovery.attempts.size, "one attempt plus three retries, and no more")
    }

    /**
     * …and the *shipped* default is three, asserted through the no-argument constructor.
     *
     * This is a separate test from the one above on purpose. That one pins the behaviour of a
     * configured instance; this pins the configuration itself. They were the same test at first, and
     * a falsification probe caught the difference: raising `DEFAULT_MAX_RETRIES` from 3 to 5 left
     * the suite green, because the helper passed its own value and the production default was never
     * exercised. A test that cannot fail when the policy it names is changed is not a test of that
     * policy, and "just add one more retry" is exactly the change most likely to arrive.
     */
    @Test
    fun `the default budget is three retries, asserted on the shipped configuration`() {
        val recovery = PageLoadRecovery(random = { 0.5 })

        repeat(3) { recovery.onFailure("https://cdn-a.example/1.jpg", HttpException(403)) }

        assertEquals(
            PageLoadRecoveryAction.GIVE_UP,
            recovery.onFailure("https://cdn-a.example/1.jpg", HttpException(403)).action,
            "the default request budget changed; that is a decision, not a detail",
        )
        assertEquals(3, PageLoadRecovery.DEFAULT_MAX_RETRIES)
    }

    @Test
    fun `the budget counts every kind of failure, not each kind separately`() {
        // The alternative — a separate allowance for re-resolves — is what would let a page spend
        // more requests than it used to. One counter is the constraint that prevents that.
        val recovery = recovery()

        recovery.onFailure("https://cdn-a.example/1.jpg", HttpException(403))
        recovery.onFailure("https://cdn-b.example/1.jpg", IOException("reset"))
        recovery.onFailure("https://cdn-b.example/1.jpg", IOException("reset"))
        val decision = recovery.onFailure("https://cdn-b.example/1.jpg", IOException("reset"))

        assertEquals(PageLoadRecoveryAction.GIVE_UP, decision.action)
    }

    /**
     * A re-resolve is answered sooner than a same-URL retry, because the next attempt is a different
     * request to a possibly different host rather than the same one again. Without this, recovering
     * from a dead CDN costs the same as retrying a flaky socket, which is the wrong trade.
     */
    @Test
    fun `a re-resolve is answered sooner than a same-URL retry`() {
        val recovery = recovery()

        val reResolve = recovery.onFailure("https://cdn-a.example/1.jpg", HttpException(403))
        val sameUrl = recovery.onFailure("https://cdn-b.example/1.jpg", IOException("reset"))

        assertTrue(
            reResolve.delayMs < sameUrl.delayMs,
            "asking for a different URL should not wait as long as repeating the same one " +
                "(${reResolve.delayMs}ms vs ${sameUrl.delayMs}ms)",
        )
    }

    /**
     * Jitter is what stops a chapter of pages that failed together from retrying together. The
     * bounds are asserted against the extremes of the draw, because "there is jitter somewhere in
     * here" is not the property — "two callers cannot land on the same delay" is.
     */
    @Test
    fun `the backoff is jittered, so equal failures do not retry in lockstep`() {
        val earliest = recovery(random = { 0.0 }).onFailure("https://cdn-a/1.jpg", IOException("reset"))
        val latest = recovery(random = { 1.0 }).onFailure("https://cdn-a/1.jpg", IOException("reset"))

        assertTrue(
            latest.delayMs > earliest.delayMs,
            "the delay must depend on the draw, or parallel pages retry at the same instant " +
                "(${earliest.delayMs}ms vs ${latest.delayMs}ms)",
        )
    }

    @Test
    fun `the backoff never exceeds its ceiling however many attempts pass`() {
        val recovery = PageLoadRecovery(maxRetries = 40, random = { 1.0 })

        val delays = (1..20).map { recovery.backoffMs(PageLoadRecoveryAction.RETRY_SAME_URL, it) }

        assertTrue(
            delays.all { it <= PageLoadRecovery.DEFAULT_MAX_DELAY_MS },
            "an unbounded ladder would park a reader worker for minutes; saw $delays",
        )
    }

    /**
     * The unusable-URL memory. Its only job is to stop the source being asked again for a string
     * already proved incapable of addressing a host — each such call is a round-trip spent learning
     * nothing. It must be scoped to this attempt sequence and cleared by a good resolution, or a
     * source that fixes itself would be refused forever.
     */
    @Test
    fun `a URL already rejected structurally is remembered`() {
        val recovery = recovery()
        val malformed = MalformedImageUrlException("https://cdn-x,https", "the host is not a hostname")
        recovery.onFailure("https://cdn-x,https", malformed)

        assertTrue(recovery.isKnownUnusable("https://cdn-x,https"))
    }

    @Test
    fun `a different URL is not refused because another one was`() {
        val recovery = recovery()
        recovery.onFailure("https://cdn-x,https", MalformedImageUrlException("https://cdn-x,https", "bad host"))

        assertFalse(
            recovery.isKnownUnusable("https://cdn-y.example/1.jpg"),
            "the source handing back a different string is the one thing that can recover, and " +
                "refusing it would make the page unrecoverable by construction",
        )
    }

    @Test
    fun `a good resolution clears the memory of a rejected one`() {
        val recovery = recovery()
        recovery.onFailure("https://cdn-x,https", MalformedImageUrlException("https://cdn-x,https", "bad host"))

        recovery.onResolved("https://cdn-y.example/1.jpg")

        assertFalse(recovery.isKnownUnusable("https://cdn-x,https"))
    }

    /**
     * The diagnosability requirement. A page that gave up having tried three hosts is exactly the
     * case that cannot currently be explained, and the sentence below is the whole difference
     * between a report that can be acted on and one that has to be reasoned about from first
     * principles.
     */
    @Test
    fun `the summary names every host tried and what each said`() {
        val recovery = recovery()
        recovery.onFailure("https://cdn-a.example/1.jpg", HttpException(403))
        recovery.onFailure("https://cdn-b.example/1.jpg", SocketTimeoutException("timeout"))
        recovery.onFailure("https://cdn-c.example/1.jpg", HttpException(403))

        val summary = recovery.summary()

        assertTrue(summary.contains("cdn-a.example"), summary)
        assertTrue(summary.contains("HTTP 403"), summary)
        assertTrue(summary.contains("cdn-b.example"), summary)
        assertTrue(summary.contains("timeout"), summary)
        assertTrue(summary.contains("cdn-c.example"), summary)
        assertTrue(summary.startsWith("tried 3 attempt(s)"), summary)
    }

    @Test
    fun `a failure before any URL existed is still recorded`() {
        // `getImageUrl` can fail on its own, and a report that says only "no url" would hide the
        // one thing worth knowing about it.
        val recovery = recovery()
        recovery.onFailure(null, HttpException(500))

        assertTrue(recovery.summary().contains("<no url>"))
    }

    @Test
    fun `the label for an HTTP failure is the status, not the class name`() {
        val recovery = recovery()
        recovery.onFailure("https://cdn-a.example/1.jpg", HttpException(403))

        assertEquals("HTTP 403", recovery.attempts.single().errorLabel)
    }

    /** Both the reader and the downloader must be able to ask the same delay question. */
    @ParameterizedTest
    @CsvSource(
        "RETRY_SAME_URL, 1, 1000, 2000",
        "RETRY_SAME_URL, 2, 2000, 4000",
        "RE_RESOLVE_URL, 1, 250, 500",
        "GIVE_UP, 1, 0, 0",
    )
    fun `the delay stays within the previous and next ceiling`(
        action: PageLoadRecoveryAction,
        retry: Int,
        floor: Long,
        bound: Long,
    ) {
        val recovery = recovery()

        val delay = recovery.backoffMs(action, retry)

        assertTrue(delay in floor..bound, "expected $delay to be within $floor..$bound")
    }
}
