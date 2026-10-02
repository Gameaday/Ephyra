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
 * **The rule, and why there is no fallback.** Mihon's `HttpSource` reads two deliberately different
 * fields: `imageUrlRequest` uses `page.url`, `imageRequest` uses `page.imageUrl`. That is the
 * extension contract, and it is not an oversight — `imageUrlRequest` serves the deprecated chain in
 * which the app *fetches* `page.url` and parses the response with `imageUrlParse`, while
 * `imageRequest` is for a source that already put the address in `imageUrl`.
 *
 * An earlier version of this file tried `imageUrl` first and fell back to `url` for both builders.
 * That is a divergence from the reference implementation with no justification, and it changes the
 * request an extension actually receives: a source relying on the deprecated path got a different
 * URL, therefore a different response body, therefore a different `imageUrlParse` result. So each
 * caller now names the field it is allowed to read, and no field is ever read as a substitute for
 * another. Resolution and judgement are retained — they are what make a relative `img.attr("src")`
 * requestable — but they operate on one value and never choose it.
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
         * Resolves and judges exactly one named field of [page] against [baseUrl].
         *
         * **Which field a caller may read is the extension contract, not a preference.** Passing
         * [Field.URL] reads [Page.url] and nothing else; passing [Field.IMAGE_URL] reads
         * [Page.imageUrl] and nothing else. Neither falls back to the other, because the reference
         * implementation does not and an extension written against it will not be written to tolerate
         * it.
         *
         * @throws MalformedImageUrlException if the named field cannot address a host.
         */
        fun of(page: Page, baseUrl: String?, field: Field): PageImageAddress {
            val raw = when (field) {
                Field.IMAGE_URL -> page.imageUrl
                Field.URL -> page.url
            }
            return try {
                PageImageAddress(ResolvedImageUrl.of(raw, baseUrl), field, raw.orEmpty())
            } catch (e: MalformedImageUrlException) {
                reportFailure(page, baseUrl, field, raw, e)
                throw MalformedImageUrlException(url = raw.orEmpty(), reason = e.reason)
            }
        }

        /**
         * Reports why a page yielded no address, naming **both** fields.
         *
         * The exception can carry one value, and on the reported MangaDex failure it was not
         * possible to tell from it whether the unused field held something usable — which decides
         * whether the defect is the source's alone. Both are printed so the next report does not
         * have to be re-derived.
         */
        private fun reportFailure(
            page: Page,
            baseUrl: String?,
            field: Field,
            raw: String?,
            cause: MalformedImageUrlException,
        ) {
            logcat {
                buildString {
                    append("PageImageAddress: ${field.name} cannot address a host.\n")
                    append("  baseUrl  = $baseUrl\n")
                    append("  imageUrl = ${page.imageUrl.orEmpty().ifEmpty { "<blank>" }}\n")
                    append("  url      = ${page.url.ifEmpty { "<blank>" }}\n")
                    append("  read     = ${field.name}\n")
                    append("  raw      = ${raw.orEmpty().ifEmpty { "<blank>" }}\n")
                    append("  reason   = ${cause.reason}")
                }
            }
        }
    }
}
