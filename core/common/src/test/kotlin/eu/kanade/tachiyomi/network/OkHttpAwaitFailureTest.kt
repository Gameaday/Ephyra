package eu.kanade.tachiyomi.network

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.UnknownHostException

/**
 * Pins that `await()` hands the caller back the failure OkHttp actually produced.
 *
 * **The defect this prevents (`DEF-023`).** `await()` used to re-wrap every failure as
 * `IOException(e.message, e)` — a fresh plain `IOException` carrying the original's message and the
 * original as its `cause`. That reads as harmless and is not: it destroys the failure's *type*, and
 * a resolver failure is the one type that says "the URL is wrong" rather than "the connection
 * dropped". With the type erased, `TransientErrors.shouldReResolveUrl` had no way to recognise it,
 * so the reader re-requested the same unresolvable image URL on every attempt — including the first
 * attempt of the user's own Retry — and the page could not recover. The exception type is the whole
 * message here, so it is asserted directly rather than inferred.
 *
 * **Why this asserts the type and not object identity.** It originally asserted `assertSame`, and
 * failed on both tests. That assertion was wrong, not the code: `await()` does hand back the very
 * instance OkHttp produced — `withCallSite` mutates `stackTrace` in place and returns `this` — but
 * `runBlocking` resumes through `suspendCancellableCoroutine`, and kotlinx-coroutines' stack-trace
 * recovery substitutes a *copy* of the same type with the original chained as its `cause`. Probed
 * rather than assumed: resuming the same `Continuation` directly, with no coroutine boundary, gives
 * `same=true`; going through `runBlocking` gives `same=false` with the class unchanged and
 * `cause=java.io.IOException`. So identity is unachievable across a suspension point by
 * construction, and an assertion that can never hold is not evidence of anything.
 *
 * Asserting the exact class is the stronger claim anyway: the `DEF-023` defect produced an
 * `IOException` wrapping an `UnknownHostException`, so a plain type check would pass while the
 * defect was live. `assertEquals(UnknownHostException::class.java, thrown.javaClass)` fails for that
 * shape and passes for a real resolver failure, which is precisely the distinction the fix turned on.
 */
class OkHttpAwaitFailureTest {

    @Test
    fun `a resolver failure arrives as a resolver failure`() {
        val original = UnknownHostException(
            "Unable to resolve host \"cmxd98sb0x3yprd.mangadex.network\": No address associated with hostname",
        )

        val thrown = assertThrows(UnknownHostException::class.java) {
            runBlocking { failingCall(original).await() }
        }

        assertEquals(
            UnknownHostException::class.java,
            thrown.javaClass,
            "await() must not downgrade the failure's type; a resolver failure is the one type " +
                "that says the URL is wrong, and erasing it is what made DEF-023 unfixable " +
                "downstream",
        )
        assertEquals(original.message, thrown.message, "the message must survive unchanged")
    }

    @Test
    fun `a plain IO failure arrives as itself too`() {
        // The general rule, stated without a subclass: nothing is downgraded on the way out.
        val original = IOException("Image failed to write")

        val thrown = assertThrows(IOException::class.java) {
            runBlocking { failingCall(original).await() }
        }

        assertEquals(
            IOException::class.java,
            thrown.javaClass,
            "a plain failure must arrive as that exact type and not as a narrower or broader one",
        )
        assertEquals(original.message, thrown.message, "the message must survive unchanged")
    }

    /**
     * A [Call] whose only outcome is [failure], delivered through the real `enqueue` callback path.
     *
     * Hand-rolled rather than served, because the behaviour under test is what `await()` does with a
     * failure, not what a server returns. The failure is delivered synchronously from `enqueue`,
     * which is the same order a real `Call` uses when it fails before the request leaves, and it
     * exercises the resume-inside-the-block path of `suspendCancellableCoroutine`.
     */
    private fun failingCall(failure: IOException): Call {
        lateinit var call: Call
        call = mockk<Call>()
        every { call.request() } returns Request.Builder().url("https://cdn.example.network/1.jpg").build()
        every { call.cancel() } returns Unit
        every { call.isCanceled() } returns false
        every { call.enqueue(any()) } answers {
            firstArg<Callback>().onFailure(call, failure)
        }
        return call
    }
}
