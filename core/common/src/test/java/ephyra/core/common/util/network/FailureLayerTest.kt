package ephyra.core.common.util.network

import eu.kanade.tachiyomi.network.HttpException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Pins that a failure is attributed to the layer that *owns* it rather than the layer that noticed.
 *
 * The MangaDex case is the reason this file exists and it is pinned here verbatim, because the
 * instinct it corrects is a reasonable one: `UnknownHostException` really is a transport exception,
 * and classifying it as anything else looks wrong until you remember the resolver was handed a string
 * no hostname could ever match.
 */
class FailureLayerTest {

    @Test
    fun `the MangaDex spliced URL is an adapter failure, not a transport one`() {
        // Raised by ImageUrlPolicy before any request exists.
        val spliced = MalformedImageUrlException(
            url = "https://cmdxd98sb0x3yprd.mangadex.network,https",
            reason = "the host \"cmdxd98sb0x3yprd.mangadex.network,https\" is not a hostname",
        )

        val failure = LayeredFailure.classify("image request", "page 3", spliced)

        assertEquals(FailureLayer.ADAPTER, failure.layer)
    }

    @Test
    fun `a name that did not resolve is a transport failure`() {
        val failure = LayeredFailure.classify("search", "https://example.com", UnknownHostException("nope"))

        assertEquals(FailureLayer.TRANSPORT, failure.layer)
    }

    @Test
    fun `a non-2xx is a source failure because it is the server's verdict`() {
        val failure = LayeredFailure.classify("getPages", "chapter/1", HttpException(500))

        assertEquals(FailureLayer.SOURCE, failure.layer)
    }

    @Test
    fun `network exception types classify as transport`() {
        listOf(
            SocketTimeoutException(),
            ConnectException(),
            IOException(),
        ).forEach { error ->
            assertEquals(
                FailureLayer.TRANSPORT,
                LayeredFailure.classify("image request", "page 1", error).layer,
                "expected ${error::class.simpleName} to be TRANSPORT",
            )
        }
    }

    /**
     * `MalformedImageUrlException` extends `IOException`, so it is caught by the transport arm too.
     *
     * The ordering in [LayeredFailure.classify] is therefore load-bearing, not stylistic: moving the
     * malformed-URL arm below the `IOException` arm would silently reclassify the MangaDex defect as a
     * network fault, which is the exact misdiagnosis this type exists to prevent. Pinned so a
     * well-meaning reorder fails here rather than in a bug report.
     */
    @Test
    fun `the malformed-URL arm is matched ahead of the IOException arm it subclasses`() {
        assertTrue(
            MalformedImageUrlException("https://x", "r") is IOException,
            "precondition: this is only interesting while it is an IOException subtype",
        )

        assertEquals(
            FailureLayer.ADAPTER,
            LayeredFailure.classify("image request", null, MalformedImageUrlException("https://x", "r")).layer,
        )
    }

    /**
     * The default matters more than it looks.
     *
     * An unrecognised failure defaults to SOURCE rather than TRANSPORT because guessing "network"
     * is what sent the MangaDex investigation to the resolver when the string was the defect. A
     * default of TRANSPORT would re-create that bias in every future unknown failure.
     */
    @Test
    fun `an unrecognised failure defaults to source rather than transport`() {
        val failure = LayeredFailure.classify("getChapters", "item/7", IllegalStateException("?"))

        assertEquals(FailureLayer.SOURCE, failure.layer)
    }

    @Test
    fun `describe leads with the layer and names the subject before the cause`() {
        val failure = LayeredFailure.classify(
            operation = "getPages",
            subject = "chapter/12",
            error = MalformedImageUrlException("https://host,https", "not a hostname"),
        )

        // The cause's own message is long but carries the URL, which is the part worth having. What
        // this pins is the ordering: layer, operation, subject, cause — so a reader can triage on the
        // prefix without reading the whole line.
        val line = failure.describe()
        assertTrue(line.startsWith("ADAPTER getPages [chapter/12]: "), "was: $line")
        assertTrue(line.contains("https://host,https"), "the URL must survive into the line: $line")
    }

    @Test
    fun `describe omits an absent subject without leaving empty brackets`() {
        val failure = LayeredFailure.classify("discover", subject = null, error = SocketTimeoutException("slow"))

        assertEquals("TRANSPORT discover: slow", failure.describe())
        assertTrue(!failure.describe().contains("[]"), "should not render an empty subject")
    }

    /** The cause is retained so the existing classifier can still inspect its type. */
    @Test
    fun `classify retains the cause so TransientErrors can still see the type`() {
        val error = UnknownHostException("dead host")

        val failure = LayeredFailure.classify("image request", "page 2", error)

        assertEquals(error, failure.cause)
        assertTrue(TransientErrors.shouldReResolveUrl(failure.cause!!))
    }
}
