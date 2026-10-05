package ephyra.domain.content.source

import ephyra.core.common.util.network.ImageUrlPolicy
import ephyra.core.common.util.network.MalformedImageUrlException

/**
 * A worked example of the adapter contract, using the MangaDex defect verbatim.
 *
 * This is a reference implementation rather than a test of production code — real adapters arrive with
 * their providers. It exists so the contract has a copyable example and so
 * [ContentConformance] is demonstrated against the exact defect the suite was built for.
 *
 * The one behaviour worth copying is in [toPages]: check what the provider handed back, using the one
 * owner of the rule, and refuse rather than forward something impossible.
 */
class ReferenceAdapter : ContentAdapter {

    override val adapterId: String = "reference"

    override fun toItems(response: ProviderResponse): AdapterOutput = AdapterOutput.Accepted()

    override fun toPages(response: ProviderResponse): AdapterOutput {
        val urls = (response.payload as? List<*>)?.map { it?.toString().orEmpty() }.orEmpty()

        // Raises before anything is forwarded. In the reported defect this string reached DNS and the
        // user was told a host did not resolve; here it is refused at the layer that produced it.
        urls.forEach { url ->
            ImageUrlPolicy.requireUsable(url)
        }

        return AdapterOutput.Accepted(pageUrls = urls)
    }
}
