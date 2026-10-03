package eu.kanade.tachiyomi.source.online

import ephyra.core.common.util.network.MalformedImageUrlException
import ephyra.core.common.util.network.ResolvedImageUrl
import ephyra.core.common.util.network.asContextualised
import ephyra.core.common.util.network.withContext
import ephyra.core.common.util.system.logcat
import eu.kanade.tachiyomi.source.model.Page

/**
 * Where the loader last saw what `getPageList` actually returned.
 *
 * **Why this is static state.** The reported failure is a contradiction between two facts about one
 * call: `getPageList` is declared on the source, yet the pages carry no address. Only the loader can
 * count what came back and only the resolver can report the failure, so one has to leave a trace for
 * the other. It is a diagnostic deliberately — read-only, overwritten each fetch, and consulted by
 * no decision.
 *
 * **Why the count goes in the user-visible error and not only to logcat.** Every round of this
 * investigation stalled on the same request — "send me the logcat line" — because the fact lived in a
 * log while the person reporting the bug was reading the error on screen. Putting it in the message
 * means the next report carries the answer with it.
 */
object PageListDiagnostics {
    /** Set by the loader immediately after each `getPageList` call. */
    @Volatile
    @JvmStatic
    var lastFetchSummary: String = "<getPageList has not been called>"

    /** Records [total] pages returned, of which [withAddress] carried an image address. */
    @JvmStatic
    fun record(total: Int, withAddress: Int) {
        lastFetchSummary = "$total page(s), $withAddress with an address"
    }

    /**
     * Which list the reader is actually holding, recorded by the loader when it chooses.
     *
     * Needed because `lastFetchSummary` describes a *fetch* while the failure describes a *page*, and
     * the two need not be the same list: a list persisted by an earlier open can be the one in hand.
     * Without this, "21 pages with an address" and "this page has none" could each be true of
     * different objects, with nothing on screen saying which list is which.
     */
    @Volatile
    @JvmStatic
    var lastListOrigin: String = "<no page list chosen>"

    @JvmStatic
    fun recordOrigin(origin: String) {
        lastListOrigin = origin
    }
}

/**
 * Whether this source can produce an image address for a page that arrives without one.
 *
 * Two consumers need this and neither should be reaching into [SourceCapabilities]:
 * `resolvePageImage` below, and the reader's cache gate — which asks the same question about a
 * *stored* page list, to decide whether a cached list can ever be read. Naming it here keeps
 * "extensions have two image paths" inside the module that owns extensions.
 */
val HttpSource.resolvesOwnPageImages: Boolean
    get() = capabilities.customisesImageUrlChain

/**
 * Resolves the one addressable image URL for [page], asking this source when it has to.
 *
 * **Why this exists.** Everything an extension consumer needs to know about the extension ABI lived in
 * the consumers: which of `Page.url` and `Page.imageUrl` holds the address, whether this source
 * customises the image-URL chain, and how to ask it. `HttpPageLoader` and `Downloader` each spelled
 * that out, and both had to be corrected separately more than once. With Jellyfin and local archives
 * arriving next, a third consumer would have learned the same rules and drifted the same way.
 *
 * So the rules live here, once, and a consumer asks one question.
 *
 * **The steps, and why each exists.**
 *
 * 1. `Page.imageUrl` when populated. This is the 1.6 contract: `getPageList` puts the address there.
 * 2. Otherwise, if this source customises *none* of the four chain entry points, `page.url` is not an
 *    image address and asking the inherited default would spend a request on it. That produced the
 *    reported failure — a request on a `(host, tokenUrl, fetchTime)` at-home cache key, reported as a
 *    DNS error. So the value is checked first and the failure is reported here, where the source and
 *    its overrides are known.
 * 3. Otherwise ask [HttpSource.getImageUrl]. The inherited default runs the deprecated chain, which
 *    *fetches* `page.url` and parses the response — so a source that relies on `imageUrlParse` still
 *    works, and its result is judged here rather than trusted.
 *
 * **What the caller still owns.** Page-load policy — pacing, retry classification, whether a repeat
 * address is worth another round-trip — belongs to the reader and the downloader, because it depends
 * on state this function does not have. This function answers only "what address does this page
 * have", and it answers it identically for every consumer.
 *
 * @throws MalformedImageUrlException naming the source and what it overrides, when the page yields
 *   no address. Classified as `ADAPTER`, so the failure is attributed to the source that produced it
 *   rather than to the transport about to carry it.
 */
suspend fun HttpSource.resolvePageImage(page: Page): ResolvedImageUrl {
    val populated = page.imageUrl
    if (!populated.isNullOrEmpty()) {
        return ResolvedImageUrl.of(populated, baseUrl)
    }

    if (!capabilities.customisesImageUrlChain) {
        // Gate, not a substitute. The chain would still fetch and parse; this only refuses to point it
        // at a value that cannot address a host. Using `page.url` as the answer instead would skip
        // `imageUrlParse` and quietly break every source that depends on it.
        try {
            ResolvedImageUrl.of(page.url, baseUrl)
        } catch (cause: MalformedImageUrlException) {
            throw cause.withContext(describePageImageRejection(page, at = "resolvePageImage/gate"))
        }
    }

    // Whatever the source hands back is judged here rather than trusted, and the failure reports what
    // it actually returned. Previously this path threw bare, which made the two ways it can go wrong
    // indistinguishable: a source that returns an unusable address, and a source whose override is
    // never reached because our inherited chain ran instead. Both surface as the same sentence.
    val returned = try {
        getImageUrl(page)
    } catch (e: Throwable) {
        throw e.asContextualised(
            describePageImageRejection(page, at = "resolvePageImage/getImageUrl", resolvedVia = "getImageUrl (threw)"),
        )
    }
    return try {
        ResolvedImageUrl.of(returned, baseUrl)
    } catch (e: MalformedImageUrlException) {
        throw e.withContext(
            describePageImageRejection(
                page,
                at = "resolvePageImage/getImageUrl",
                returned = returned,
                resolvedVia = "getImageUrl",
            ),
        )
    }
}

/**
 * Describes a rejected page address: where the value came from, and what the source actually returns.
 *
 * **This returns text for the exception message, deliberately.** Three earlier commits added these
 * fields to `logcat`, and the only person reading the failure had no logcat — so the instrumented
 * build and the uninstrumented one produced an identical on-screen error, and "the error did not
 * change" could not be told apart from "the error changed". A diagnostic nobody can see is not a
 * diagnostic. `url` and `reason` are untouched, so classification is unaffected.
 */
fun HttpSource.describePageImageRejection(
    page: Page,
    at: String,
    returned: String? = null,
    resolvedVia: String? = null,
): String = buildString {
    append("Why this was rejected:")
    append("\n  at            = $at")
    // `javaClass` must name the source explicitly: inside `buildString` the receiver is the
    // StringBuilder, so a bare `javaClass` reported `java.lang.StringBuilder` — the one field that
    // was wrong in every report, and the one that identifies which source failed.
    append("\n  source        = ${this@describePageImageRejection.javaClass.name}")
    append("\n  overrides     = ${capabilities.overriddenChainMethods()}")
    append("\n  resolvedVia   = ${resolvedVia ?: "not consulted"}")
    append("\n  pageImageUrl  = ${page.imageUrl ?: "<null>"}")
    append("\n  page.url      = ${page.url.ifEmpty { "<blank>" }}")
    if (returned != null) {
        append("\n  returned      = $returned")
    }
    append("\n  getPageList   = ${PageListDiagnostics.lastFetchSummary}")
    append("\n  listOrigin    = ${PageListDiagnostics.lastListOrigin}")
    append("\n  getPageListBy = ${capabilities.declaringClassOf("getPageList")}")
    append("\n  baseUrl       = $baseUrl")
}

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
     * Whether this source customises the image-URL chain, through any of its four entry points.
     *
     * `getImageUrl` is the modern one. The deprecated chain has **three** of its own, and this list
     * was short by one until the reported failure proved it:
     *
     * - `fetchImageUrl` — the whole chain, replaced wholesale. The most direct override available,
     *   and the one a source that resolves its own addressing reaches for first.
     * - `imageUrlRequest` — chooses what to fetch.
     * - `imageUrlParse` — reads the address out of the response.
     *
     * **How the fourth was found.** The probe listed three and reported MangaDex as `overrides=none`,
     * so the reader refused its pages. But MangaDex does customise — through `fetchImageUrl`, which
     * reads the `(host, tokenRequestUrl, fetchTime)` at-home cache key it keeps in `Page.url`. A
     * probe that answers "no" for a source that answers "yes" is worse than no probe at all: it
     * turns a working source into a refusal, and it does so with a confident diagnostic attached.
     *
     * **What the app must do differently.** It must not assume `Page.url` is an image address for such
     * a source: MangaDex keeps that at-home cache key there, because MangaDex@Home tokens expire after
     * five minutes, and only its own override knows how to read it. The app's job is to fetch and
     * parse through the chain, not to second-guess the field.
     */
    val customisesImageUrlChain: Boolean by lazy {
        overrides("getImageUrl") || overrides("fetchImageUrl") ||
            overrides("imageUrlRequest") || overrides("imageUrlParse")
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
        while (current != null && current.name != HTTP_SOURCE_CLASS_NAME) {
            current.declaredMethods.firstOrNull { it.name == name }
                ?.let { return it.declaringClass.simpleName }
            current = current.superclass
        }
        return "<base>"
    }

    private companion object {
        /**
         * Every entry point into the image-URL chain, not just the ones the reader calls directly.
         *
         * Kept as one list so the probe and the diagnostic cannot disagree — the mismatch is what let
         * a customising source be reported as `none`.
         */
        val CHAIN_ENTRY_POINTS = listOf("getImageUrl", "fetchImageUrl", "imageUrlRequest", "imageUrlParse")

        /**
         * Identifies [HttpSource] by name rather than by reference, because a delegated class loader
         * can hand the extension a *different* `Class` with the same name. See [overrides].
         */
        const val HTTP_SOURCE_CLASS_NAME = "eu.kanade.tachiyomi.source.online.HttpSource"
    }

    /**
     * Walks *declared* methods from the concrete class up to — but not including — [HttpSource].
     *
     * Declared, not inherited-and-public: `imageUrlRequest` and `imageUrlParse` are `protected`, and
     * [Class.getMethods] returns public members only. A check written against it silently misses
     * both even with the right names, which is precisely how an earlier version of this probe
     * reported MangaDex as uncustomising and blocked the chain it was written to accommodate.
     *
     * **Compared by name, not by identity.** Extensions load through
     * `DelegateLastClassLoaderCompat`, which consults the extension's own dex *before* the host's.
     * So if an extension ever bundles its own copy of `HttpSource` — which is exactly what a plugin
     * that shades or relocates dependencies would do — then `MangaDex`'s superclass chain ends at a
     * `Class` that is not `HttpSource::class.java`, and an identity comparison never terminates
     * where it should. The walk would run into the bundled copy's own `getImageUrl` and
     * `fetchImageUrl` and report **every** source as customising, silently disabling the gate and
     * letting `Page.url` be fetched as an image address.
     *
     * That failure is the opposite direction from the one this probe already had, and worse: the
     * gate is what stops the app sending a cache key to DNS. Name comparison is immune to there
     * being two copies, and loses nothing when there is only one.
     */
    private fun overrides(name: String): Boolean {
        var current: Class<*>? = type
        while (current != null && current.name != HTTP_SOURCE_CLASS_NAME) {
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
