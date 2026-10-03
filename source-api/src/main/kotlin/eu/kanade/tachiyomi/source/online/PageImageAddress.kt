package eu.kanade.tachiyomi.source.online

import ephyra.core.common.util.network.MalformedImageUrlException
import ephyra.core.common.util.network.ResolvedImageUrl
import ephyra.core.common.util.system.logcat
import eu.kanade.tachiyomi.source.model.Page

/**
 * What a source **actually implements**, probed from the loaded class rather than read from metadata.
 *
 * **Why this exists, and why it is not the version number.** `ExtensionLoader` already knows each
 * extension's declared `extension-lib` version and refuses to load anything outside
 * `SUPPORTED_LIB_VERSIONS`. That answers "can this be loaded at all". It does not answer "what will
 * this source do when asked for an image URL", and the two are not the same question:
 *
 *  - an extension may override whichever chain entry points it likes regardless of the version it
 *    declares — MangaDex declares 1.6 and overrides `imageUrlRequest`/`imageUrlParse`, neither of
 *    which is the modern `getImageUrl`;
 *  - a source declaring 1.4 may equally override any of the three.
 *
 * Branching behaviour on the declared version is therefore a guess about code that is already loaded
 * and inspectable. The reported MangaDex failure is what that guess costs: treating `Page.url` as an
 * image address, for a source where `url` is an at-home token cache key
 * (`host,tokenUrl,fetchTime`) that only the source itself can read.
 *
 * **How to use it.** Each capability is a named probe with a comment saying what the app must do
 * differently when it is present. Call sites read `capabilities.customisesImageUrlChain`, never a
 * version comparison, and a new extension generation adds a probe here rather than another `when` at
 * each place that has to care.
 *
 * **Cost.** One instance per source, computed once. Probes are reflection over the class hierarchy and
 * are therefore cached per instance: they cannot change for a loaded class, and re-probing per page
 * would be wasteful on a long chapter.
 */
class SourceCapabilities internal constructor(private val type: Class<*>) {

    /**
     * Whether this source customises the image-URL chain, through any of its three entry points.
     *
     * `getImageUrl` is the modern one. The deprecated chain has two of its own — `imageUrlRequest`
     * chooses what to fetch and `imageUrlParse` reads the address out of the response — and overriding
     * either is a complete customisation on its own.
     *
     * **What the app must do differently.** It must not assume `Page.url` is an image address for such
     * a source: MangaDex keeps a `(host, tokenRequestUrl, fetchTime)` at-home cache key there, because
     * MangaDex@Home tokens expire after five minutes, and only its own `imageUrlRequest` knows how to
     * read it. The app's job is to fetch and parse through the chain, not to second-guess the field.
     */
    val customisesImageUrlChain: Boolean by lazy {
        overrides("getImageUrl") || overrides("imageUrlRequest") || overrides("imageUrlParse")
    }

    /**
     * Which of the chain entry points this source declares, for a diagnostic.
     *
     * Reported rather than assumed, because "the URL is bad" is true of every malformed URL and
     * distinguishes nothing. A source that overrides none of them is the one case where the app''s own
     * chain runs, and therefore the only case where Page.url has to be fetchable — which is what the
     * reported failure turned on, and what its error message did not say.
     */
    fun overriddenChainMethods(): String =
        CHAIN_ENTRY_POINTS.filter { overrides(it) }.ifEmpty { listOf("none") }.joinToString("+")

    /**
     * The class that declares [name], or `"<base>"` when nothing overrides it, for a diagnostic.
     *
     * Answering "did our own base implementation run, or the extension's?" is the question a value
     * cannot answer. An extension that overrides `getPageList` and populates `Page.imageUrl` never
     * reaches the reader's image-URL fallback at all, so reaching it is itself the finding.
     */
    fun declaringClassOf(name: String): String {
        var current: Class<*>? = type
        while (current != null && current != HttpSource::class.java) {
            current.declaredMethods.firstOrNull { it.name == name }
                ?.let { return it.declaringClass.simpleName }
            current = current.superclass
        }
        return "<base>"
    }

    private companion object {
        val CHAIN_ENTRY_POINTS = listOf("getImageUrl", "imageUrlRequest", "imageUrlParse")
    }

    /**
     * Walks *declared* methods from the concrete class up to — but not including — [HttpSource].
     *
     * Declared, not inherited-and-public: `imageUrlRequest` and `imageUrlParse` are `protected`, and
     * [Class.getMethods] returns public members only. A check written against it silently misses
     * both even with the right names, which is precisely how an earlier version of this probe reported
     * MangaDex as uncustomising and blocked the chain it was written to accommodate.
     */
    private fun overrides(name: String): Boolean {
        var current: Class<*>? = type
        while (current != null && current != HttpSource::class.java) {
            if (current.declaredMethods.any { it.name == name }) return true
            current = current.superclass
        }
        return false
    }
}

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
