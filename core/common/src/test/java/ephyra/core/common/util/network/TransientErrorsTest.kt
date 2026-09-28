package ephyra.core.common.util.network

import eu.kanade.tachiyomi.network.HttpException
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException

/**
 * Pins the shared retry classification.
 *
 * The two behaviours that matter most are the ones the reader got wrong:
 *
 * - `403` used to be **permanent** in the reader, which for a signed or time-limited image URL is
 *   exactly backwards — the URL is stale, the source will issue a different one, and the page failed
 *   after a full backoff ladder for a request that could never succeed. That is the missed-image
 *   report.
 * - `404` must stay **permanent**. Re-requesting a genuinely absent image three times costs the user
 *   seven seconds and changes nothing, so the fix is not "retry everything".
 */
class TransientErrorsTest {

    @Test
    fun `an IO error is transient`() {
        assertTrue(TransientErrors.isTransient(IOException("connection reset")))
        assertTrue(TransientErrors.isTransient(SocketTimeoutException("timeout")))
    }

    @Test
    fun `rate limiting and server errors are transient`() {
        assertTrue(TransientErrors.isTransient(HttpException(429)), "429 asks us to come back")
        assertTrue(TransientErrors.isTransient(HttpException(500)))
        assertTrue(TransientErrors.isTransient(HttpException(502)))
        assertTrue(TransientErrors.isTransient(HttpException(503)))
    }

    @Test
    fun `a stale or forbidden URL is transient because the URL can be re-resolved`() {
        // This is the reader's missed-image defect. A 403 on a signed image URL means "ask again",
        // not "give up", and the loader now re-resolves on every retry so this is genuinely
        // recoverable rather than merely retried.
        assertTrue(TransientErrors.isTransient(HttpException(403)), "403 on a signed URL is recoverable")
        assertTrue(TransientErrors.isTransient(HttpException(410)), "410 means the resource is gone")
    }

    @Test
    fun `a client error that cannot resolve itself stays permanent`() {
        // The counterweight: retrying these costs the user seven seconds and changes nothing.
        assertFalse(TransientErrors.isTransient(HttpException(400)))
        assertFalse(
            TransientErrors.isTransient(HttpException(HttpURLConnection.HTTP_UNAUTHORIZED)),
            "401 must not be transient: retrying bad credentials only wastes the user's time",
        )
        assertFalse(TransientErrors.isTransient(HttpException(404)), "a missing image stays missing")
        assertFalse(TransientErrors.isTransient(HttpException(451)))
    }

    @Test
    fun `a stale URL is distinguished from one that only needs a backoff`() {
        // The caller must be able to tell these apart: a 429 should be retried against the same URL
        // after waiting, a 403 needs a fresh URL first.
        assertTrue(TransientErrors.shouldReResolveUrl(HttpException(403)))
        assertTrue(TransientErrors.shouldReResolveUrl(HttpException(410)))

        assertFalse(TransientErrors.shouldReResolveUrl(HttpException(429)), "429 is a wait, not a stale URL")
        assertFalse(TransientErrors.shouldReResolveUrl(HttpException(500)))
        assertFalse(TransientErrors.shouldReResolveUrl(IOException("io")))
    }

    @Test
    fun `a non network error is never transient`() {
        assertFalse(TransientErrors.isTransient(IllegalStateException("bug")))
        assertFalse(TransientErrors.isTransient(NullPointerException()))
        assertFalse(TransientErrors.isTransient(RuntimeException("wrapped", IOException("io"))))
    }

    @Test
    fun `every status the reader previously mishandled is pinned explicitly`() {
        // The reader's old rule was `code == 429 || code >= 500`. This asserts the delta, so a
        // future "simplification" back to that form fails here rather than in the field.
        val newlyRecoverable = listOf(403, 410)
        newlyRecoverable.forEach { code ->
            assertTrue(
                TransientErrors.isTransient(HttpException(code)),
                "$code was permanent in the reader and is the missed-image defect",
            )
        }
        val stillPermanent = listOf(400, 401, 404, 405, 451)
        stillPermanent.forEach { code ->
            assertFalse(
                TransientErrors.isTransient(HttpException(code)),
                "$code cannot resolve itself and must stay permanent",
            )
        }
    }
}
