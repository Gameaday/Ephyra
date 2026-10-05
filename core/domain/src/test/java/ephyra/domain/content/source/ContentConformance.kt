package ephyra.domain.content.source

import ephyra.core.common.util.network.FailureLayer
import ephyra.core.common.util.network.ImageUrlPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * The suite every adapter must pass, as a reusable harness rather than a copy per adapter.
 *
 * **Why this is the thing that would have caught MangaDex.** That defect was a string an adapter
 * emitted — `cmdxd98sb0x3yprd.mangadex.network,https` — which every layer downstream carried
 * faithfully until the resolver refused it. A test asserting "the adapter produced a well-formed image
 * address" would have failed in a JVM, in seconds, on a machine with no network and no device. The
 * user-facing symptom was a DNS error: the furthest possible distance from the cause.
 *
 * This is therefore not primarily a regression test. It is what makes the adapter/source distinction
 * *checkable* rather than asserted in a comment. When Jellyfin arrives as a second implementation,
 * this harness is what says whether it is a peer or a fork.
 */
object ContentConformance {

    /**
     * Asserts an adapter's output is a legal canonical shape.
     *
     * @return the accepted output, so a caller can assert further without re-running.
     * @throws AssertionError naming the adapter and the specific rule that failed.
     */
    fun assertAccepted(
        adapter: ContentAdapter,
        output: AdapterOutput,
    ): AdapterOutput.Accepted {
        val accepted = output as? AdapterOutput.Accepted
            ?: error(
                "${adapter.adapterId} rejected a conforming fixture: " +
                    (output as AdapterOutput.Rejected).let { "${it.reason} (layer=${it.layer})" },
            )

        accepted.pageUrls.forEachIndexed { index, url ->
            // The rule that matters, stated as a rule rather than as one case: anything an adapter
            // emits as an address must be requestable. `defectOf` is the single owner of that
            // judgement, so this cannot drift from what the loader later enforces.
            val defect = ImageUrlPolicy.defectOf(url)
            assertTrue(
                defect == null,
                "${adapter.adapterId} emitted an unusable page URL at index $index: \"$url\" — $defect",
            )
        }

        accepted.items.forEach { item ->
            assertTrue(
                item.title.isNotBlank(),
                "${adapter.adapterId} emitted an item with a blank title at ${item.url}",
            )
            item.thumbnailUrl?.let { thumb ->
                val defect = ImageUrlPolicy.defectOf(thumb)
                assertTrue(
                    defect == null,
                    "${adapter.adapterId} emitted an unusable cover for ${item.url}: \"$thumb\" — $defect",
                )
            }
        }

        accepted.units.forEach { unit ->
            assertTrue(
                unit.url.isNotBlank(),
                "${adapter.adapterId} emitted a unit with a blank url at ${unit.title}",
            )
        }

        return accepted
    }

    /**
     * Asserts an adapter refused a malformed fixture *and* attributed it to the right layer.
     *
     * A rejection with the wrong layer fails even though the input was correctly refused: mislabelling
     * a source fault as ours sends the next person to the wrong file.
     */
    fun assertRejected(
        adapter: ContentAdapter,
        output: AdapterOutput,
        expectedLayer: FailureLayer = FailureLayer.ADAPTER,
    ) {
        val rejected = output as? AdapterOutput.Rejected
            ?: error("${adapter.adapterId} accepted a fixture that must be refused: ${describe(output)}")

        assertEquals(
            expectedLayer,
            rejected.layer,
            "${adapter.adapterId} refused the fixture but attributed it to ${rejected.layer}, " +
                "not $expectedLayer: ${rejected.reason}",
        )
    }

    private fun describe(output: AdapterOutput): String = when (output) {
        is AdapterOutput.Accepted ->
            "items=${output.items.size} units=${output.units.size} pages=${output.pageUrls.size}"
        is AdapterOutput.Rejected -> "rejected(${output.reason})"
    }
}
