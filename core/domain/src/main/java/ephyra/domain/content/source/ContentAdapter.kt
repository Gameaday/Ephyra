package ephyra.domain.content.source

import ephyra.core.common.util.network.FailureLayer

/**
 * The one seam every content provider passes through, whichever transport it uses.
 *
 * **Why this exists, and what it is not.** There were already two content contracts in the tree —
 * `ContentItem`/`ContentUnit` behind [RemoteSource], and `CatalogEntry`/`ChapterInfo`/`ContentPage`
 * behind `UnifiedContentSource] — and an extension source used *neither* directly: it went
 * APK → Tachiyomi ABI → `DynamicHttpSource` → `ContentItem`. So "where does a source's data become
 * ours" had three answers, and a defect could be introduced in one and surface in another with
 * nothing naming the crossing.
 *
 * This is not a third vocabulary. It is the *narrow* one — the point where a provider's own shape
 * stops and ours begins — so a failure can say which side produced it. Concretely, it is what makes
 * [FailureLayer.ADAPTER] implementable: an adapter that returns [AdapterOutput.Rejected] has declared
 * that the defect is in the mapping, before any request is spent and before the reader is told a story
 * about the network that is not true.
 *
 * **Deliberately not an engine registry.** [ContentSourceEngine.handles] selects *which* engine serves
 * a source type; this describes *what crossing the boundary looks like*. Jellyfin, an extension
 * repository, and a local archive folder are three implementations of the same contract with three
 * unrelated transports, which is exactly the property the app should not have to know about.
 */
interface ContentAdapter {

    /** Identifies the provider, for logs and for attributing a failure to one provider. */
    val adapterId: String

    /**
     * Translates one provider response into canonical items.
     *
     * @return [AdapterOutput.Accepted] with what the provider actually said, or
     *   [AdapterOutput.Rejected] with the reason the translation was impossible. Implementations must
     *   not return a half-populated item as accepted: a missing title or an unusable URL is a
     *   rejection, because an item that reaches the library with a broken address is far more
     *   expensive to trace than one that never arrived.
     */
    fun toItems(response: ProviderResponse): AdapterOutput

    /**
     * Translates one provider response into canonical page addresses.
     *
     * The validation here is the whole point of the seam. `host,https` — the MangaDex defect — is a
     * string an adapter emitted and every downstream layer carried faithfully. An adapter that checks
     * its own output converts that class of defect from a network error reported to the user into a
     * named rejection at the layer that caused it.
     */
    fun toPages(response: ProviderResponse): AdapterOutput
}

/**
 * What a provider returned, in its own terms.
 *
 * Intentionally loose: an HTTP status, an HTML document, a JSON object, an archive manifest. The point
 * is that the adapter — not the orchestrator, not the reader — is the only thing that has to know
 * what this particular shape means.
 */
data class ProviderResponse(
    val operation: String,
    val subject: String?,
    val payload: Any?,
)

/** The result of translating a [ProviderResponse]. */
sealed interface AdapterOutput {

    /** The translation succeeded. [items] and [units] are canonical shapes. */
    data class Accepted(
        val items: List<ephyra.domain.content.model.ContentItem> = emptyList(),
        val units: List<ephyra.domain.content.model.ContentUnit> = emptyList(),
        val pageUrls: List<String> = emptyList(),
    ) : AdapterOutput

    /**
     * The translation was impossible, and this is why.
     *
     * @property reason what could not be translated, in terms a user report can act on.
     * @property layer which side owns the defect. Defaults to [FailureLayer.ADAPTER] because a
     *   rejection is by definition our mapping declining to produce something — if the *provider*
     *   were at fault the adapter should still surface it, with [FailureLayer.SOURCE] set
     *   explicitly, rather than mislabelling a server problem as ours.
     */
    data class Rejected(
        val reason: String,
        val layer: FailureLayer = FailureLayer.ADAPTER,
        val cause: Throwable? = null,
    ) : AdapterOutput
}
