package ephyra.domain.content.source

import ephyra.core.common.util.network.FailureLayer
import ephyra.core.common.util.network.LayeredFailure
import ephyra.core.common.util.network.MalformedImageUrlException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Demonstrates [ContentConformance] against the defect it was built for.
 *
 * The production adapters arrive with their providers; what is pinned here is the *harness*, so that
 * when Jellyfin lands there is a worked example of what "conforming" means and a test that fails if
 * the harness stops catching this class of defect.
 */
class ContentConformanceTest {

    private val adapter = ReferenceAdapter()

    /** The reported defect, verbatim: a host and a scheme joined by a comma. */
    private val mangadexSplicedUrl = "https://cmdxd98sb0x3yprd.mangadex.network,https"

    @Test
    fun `a well-formed at-home image address conforms`() {
        val good = "https://cmdxd98sb0x3yprd.mangadex.network/data/ab/cd/1.jpg"

        val accepted = ContentConformance.assertAccepted(
            adapter,
            adapter.toPages(ProviderResponse("getPages", "chapter/1", listOf(good))),
        )

        assertEquals(listOf(good), accepted.pageUrls)
    }

    /**
     * The test that would have caught the report.
     *
     * The adapter emits this string, it reaches DNS, and the user is told a host did not resolve.
     * Here it is refused at the seam, in a JVM, with the reason attached and the fault attributed to
     * the adapter rather than the network.
     */
    @Test
    fun `the MangaDex spliced address is refused at the adapter seam and attributed to the adapter`() {
        val thrown = runCatching {
            adapter.toPages(ProviderResponse("getPages", "chapter/1", listOf(mangadexSplicedUrl)))
        }.exceptionOrNull()

        assertTrue(
            thrown is MalformedImageUrlException,
            "expected the adapter to refuse the spliced address, got: $thrown",
        )

        assertEquals(
            FailureLayer.ADAPTER,
            LayeredFailure.classify("getPages", "chapter/1", thrown!!).layer,
        )
    }

    /**
     * The harness is the deliverable, so the harness is what is pinned.
     *
     * An adapter that *did* forward the malformed address must be caught by `assertAccepted` rather
     * than by the adapter's own good behaviour — otherwise a future adapter without the check would
     * pass simply because its fixture happened to be well-formed.
     */
    @Test
    fun `the harness rejects output that carries an unusable address`() {
        val lenient = object : ContentAdapter {
            override val adapterId: String = "lenient"
            override fun toItems(response: ProviderResponse): AdapterOutput = AdapterOutput.Accepted()

            // Deliberately does not validate: the exact mistake the harness exists to catch.
            override fun toPages(response: ProviderResponse): AdapterOutput {
                val urls = (response.payload as? List<*>)?.map { it?.toString().orEmpty() }.orEmpty()
                return AdapterOutput.Accepted(pageUrls = urls)
            }
        }

        val output = lenient.toPages(ProviderResponse("getPages", "chapter/1", listOf(mangadexSplicedUrl)))

        val failure = runCatching { ContentConformance.assertAccepted(lenient, output) }.exceptionOrNull()

        assertTrue(failure is AssertionError, "harness must refuse malformed output, got: $failure")
        assertTrue(
            failure?.message?.contains("lenient") == true,
            "the failure must name the adapter at fault: ${failure?.message}",
        )
    }

    /** A rejection credited to the wrong layer is a conformance failure even though it rejected. */
    @Test
    fun `a rejection attributed to the wrong layer fails conformance`() {
        val wrongLayer = AdapterOutput.Rejected("server said 500", layer = FailureLayer.ADAPTER)

        val failure = runCatching {
            ContentConformance.assertRejected(adapter, wrongLayer, expectedLayer = FailureLayer.SOURCE)
        }.exceptionOrNull()

        assertTrue(failure is AssertionError, "expected a layer mismatch to fail, got: $failure")
    }

    @Test
    fun `an accepted output the caller expected to be rejected fails conformance`() {
        val accepted = AdapterOutput.Accepted(pageUrls = listOf("https://cdn.example.com/1.jpg"))

        val failure = runCatching {
            ContentConformance.assertRejected(adapter, accepted)
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException, "expected a hard error, got: $failure")
    }
}
