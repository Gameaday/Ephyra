package eu.kanade.tachiyomi.source.online

import ephyra.core.common.util.network.MalformedImageUrlException
import ephyra.core.common.util.network.ResolvedImageUrl
import ephyra.core.common.util.system.logcat
import eu.kanade.tachiyomi.source.model.Page

/**
 * The one addressable image URL for a [Page], together with where it came from.
 *
 * **Why this exists.** A `Page` has two URL fields and the contract does not say which one to use.
 * API sources (MangaDex is the canonical one) build `Page(index, imageUrl = absolute)` with `url`
 * left at its `""` default; HTML sources do the opposite. Reading the wrong one yields either `""`
 * or an NPE depending on which method you happened to write — so the rule was implemented twice
 * inside `HttpSource` and **not at all** in the reader and the downloader, which are the two
 * consumers that actually run in production.
 *
 * **The rule.** [Page.imageUrl] first, [Page.url] as the fallback; first that can address a host
 * wins. *Preferring* is not *trusting*: a source that fills `imageUrl` with something that is not
 * an address must not hide a good `url` behind it, so the second field is still tried.
 *
 * **Lives in `source-api` because it needs [Page].** `core/common` cannot depend on `source-api`,
 * so the type that reasons about `Page` fields has to sit above it. The reader and downloader
 * already depend on both modules, so every consumer reaches it without inverting the graph.
 */
data class PageImageAddress(
    /** The resolved, judged address. Holding one proves both steps ran. */
    val url: ResolvedImageUrl,
    /** Which field it came from. */
    val field: Field,
    /** The value the source produced, before resolution — what a diagnostic should show. */
    val raw: String,
) {
    /** Which [Page] field an address came from. */
    enum class Field {
        /** [Page.imageUrl] — the preferred field. */
        IMAGE_URL,

        /** [Page.url] — the fallback, for sources that only populate the legacy field. */
        URL,
    }

    companion object {

        /**
         * The address for [page] against [baseUrl], or [MalformedImageUrlException] when neither
         * field can address a host.
         *
         * The failure names the *preferred* field, because that is the value the source should have
         * fixed; the reason is the first defect found, so the message describes the value the app
         * tried first rather than whichever happened to be tried last.
         *
         * @throws MalformedImageUrlException if neither field yields a usable address.
         */
        fun of(page: Page, baseUrl: String?): PageImageAddress {
            val candidates = listOfNotNull(
                page.imageUrl?.takeIf { it.isNotBlank() }?.let { it to Field.IMAGE_URL },
                page.url.takeIf { it.isNotBlank() && it != page.imageUrl }?.let { it to Field.URL },
            )
            var firstDefect: String? = null
            for ((candidate, field) in candidates) {
                try {
                    return PageImageAddress(ResolvedImageUrl.of(candidate, baseUrl), field, candidate)
                } catch (e: MalformedImageUrlException) {
                    if (firstDefect == null) firstDefect = e.reason
                }
            }

            // **Why this exists.** The reported MangaDex failure is a page whose fields both fail:
            // the `imageUrl` is a composite the source built for its own purposes, not an address.
            // Knowing *which* other values were available decides whether this is the source's
            // fault alone or whether a usable address was discarded here — and those need different
            // fixes. The exception can only name one value, so both are reported here instead.
            logcat {
                buildString {
                    append("PageImageAddress: no field of this page resolved to an address.\n")
                    append("  baseUrl  = $baseUrl\n")
                    if (candidates.isEmpty()) {
                        append("  imageUrl = ${page.imageUrl.orEmpty()} (blank)\n")
                        append("  url      = ${page.url} (blank)\n")
                    }
                    candidates.forEach { (value, field) ->
                        append("  ${field.name.padEnd(8)} = $value\n")
                    }
                    append("  reason   = $firstDefect")
                }
            }

            throw MalformedImageUrlException(
                url = candidates.firstOrNull()?.first ?: page.imageUrl.orEmpty(),
                reason = firstDefect ?: "the URL is empty",
            )
        }
    }
}
