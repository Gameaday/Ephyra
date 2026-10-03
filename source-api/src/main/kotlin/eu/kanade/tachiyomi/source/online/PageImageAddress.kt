package eu.kanade.tachiyomi.source.online

import ephyra.core.common.util.network.ImageUrlPolicy
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
}

/**
 * Whether this source can produce an image address for a page that arrives without one.
 *
 * **Currently unused — kept for the next consumer, deliberately public.** It was written for two
 * consumers that no longer exist: `resolvePageImage` below no longer gates on it (the gate was
 * removed because both of its failure directions broke working sources — see
 * `SourceCapabilities.customisesImageUrlChain`), and the reader's cache gate was replaced by
 * `needsFreshPageList`, which answers the same question about a *stored* list from the page values
 * alone. When a future consumer needs "will this source answer `getImageUrl` itself", read this
 * rather than re-deriving it from the probe list.
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
 * 1. `Page.imageUrl` when populated — passed through **opaquely** (`ResolvedImageUrl.opaque`), not
 *    resolved and not judged. This is the MangaDex contract: a 1.6 source may put a *relative path*
 *    there that only its own `imageRequest` can join onto a host, so the host must not touch it.
 * 2. Otherwise ask [HttpSource.getImageUrl]. The inherited default runs the deprecated chain, which
 *    *fetches* `page.url` and parses the response — so a source that relies on `imageUrlParse` still
 *    works. A source that customises none of the chain and populates neither field will hand back
 *    something unusable, and that is caught at the next step rather than spent on a request.
 * 3. The value `getImageUrl` returned is resolved against `baseUrl` and judged here, because it is
 *    the answer to *our* question and the app is the one about to request it. This is where the
 *    old gate's protection lives now: a `(host, tokenUrl, fetchTime)` at-home cache key fetched by
 *    the inherited default is rejected here, with the source and its overrides named, instead of
 *    producing a DNS error the reader cannot attribute.
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
suspend fun HttpSource.resolvePageImage(page: Page, listOrigin: String? = null): ResolvedImageUrl {
    val populated = page.imageUrl
    if (!populated.isNullOrEmpty()) {
        // A populated `Page.imageUrl` is opaque to the host. MangaDex — the canonical 1.6
        // source — puts a *relative path* there (`/data/<hash>/<file>`) and its own overridden
        // `imageRequest` joins it onto an at-home host it reads from `Page.url`. Resolving it
        // against `baseUrl` here, as this branch once did, splices two URLs into one and fails
        // every page of every chapter; upstream Mihon never rewrites a populated field, and the
        // value is handed on untouched for the source's own request builder to interpret.
        return ResolvedImageUrl.opaque(populated)
    }

    // Ask the source. A page that arrives without an address is the normal state of a source that
    // resolves one itself, and the pre-regression code asked rather than refused — `getImageUrl`
    // runs the chain that fetches `page.url` and parses the response. Refusing first is what made a
    // page with no address a hard failure instead of a question for the source, and that is a
    // behavioural regression independent of any one source: it fails fast on pages it should have
    // asked about. There was a gate here to avoid pointing the chain at a value that cannot address a
    // host; it is removed, and the value the source returns is judged instead — which is the same
    // check, applied to the answer rather than to a guess about what the answer would be.
    //
    // `Page.url` is not read as an image address anywhere on this path. That is upstream's rule, and
    // it is not merely conventional: MangaDex keeps an at-home token cache key there, and only its
    // own override knows how to read it.
    val returned = try {
        getImageUrl(page)
    } catch (e: Throwable) {
        throw e.asContextualised(
            describePageImageRejection(
                page,
                at = "resolvePageImage/getImageUrl",
                resolvedVia = "getImageUrl (threw)",
                listOrigin = listOrigin,
            ),
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
                listOrigin = listOrigin,
            ),
        )
    }
}

/**
 * Whether this page list cannot yield a usable image address, and so must be refetched.
 *
 * **The distinction this draws.** A blank `Page.imageUrl` means two opposite things depending on the
 * page: a source that resolves lazily, where the app asks `getImageUrl` per page and the value in
 * `Page.url` is a fetchable address; and a copy of a list from a source that populates
 * `Page.imageUrl` itself, where `Page.url` is **not** an address and asking cannot help. Only the second
 * is stale, and the page itself says which — no probe and no recorded flag.
 *
 * The second case is exactly what the reported failure was. MangaDex does not override the
 * URL-resolving chain at all — it overrides `pageListParse` and `imageRequest` — so it puts a
 * **relative path** in `Page.imageUrl` and keeps an at-home token cache key in `Page.url`. A list
 * whose `imageUrl` was empty therefore could not be repaired: `getImageUrl` ran the inherited
 * chain, fetched the cache key, and threw from a method the source does not implement.
 *
 * A *populated* `imageUrl` is judged on its own merits and does not consult `Page.url` at all, so a list
 * carrying a malformed address is discarded whether or not its `url` happens to be fetchable. Both
 * halves are needed: dropping the first would serve a chapter whose pages cannot be requested, and
 * dropping the second would refetch a list that was fine.
 */
fun List<Page>.needsFreshPageList(baseUrl: String?): Boolean =
    any { page ->
        val populated = page.imageUrl
        val candidate = if (!populated.isNullOrEmpty()) populated else page.url
        !ImageUrlPolicy.isUsable(ImageUrlPolicy.resolve(candidate, baseUrl))
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
    listOrigin: String? = null,
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
    // Reported by the caller rather than read from a static. These were `@Volatile` fields on
    // `PageListDiagnostics`, and the reader prefetches neighbouring chapters — so they described
    // whichever list was fetched most recently, not the chapter whose page had just failed. Two
    // lines of a report then contradicted each other while both were true of different objects.
    append("\n  listOrigin    = ${listOrigin ?: "not reported by this caller"}")
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
 *    declares — MangaDex declares 1.6 and overrides `pageListParse`/`imageRequest`, neither of
 *    which is the modern `getImageUrl`;
 *  - a source declaring 1.4 may equally override any of the others.
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
     * **Measurement only — nothing in the app branches on this.**
     *
     * It was a gate, and that was the mistake. A name-based probe of a loaded class hierarchy has two
     * failure directions and both break sources that work: reporting "does not customise" for a
     * source that does refuses pages that were readable, and the reverse lets the inherited chain treat
     * `page.url` as an address. It failed both ways in one day — once for an entry point missing from
     * the enumeration, once because `getMethods` returns only public methods while two entry points
     * are `protected`.
     *
     * It is kept because a diagnostic nobody can see is not a diagnostic, and this one now appears in
     * the exception the reader shows on screen. It costs the same whether or not anything reads it, and
     * measurement cannot break a source the way a decision can.
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
         * Every entry point an extension can override on the image path, not just the ones the
         * reader calls directly.
         *
         * **`imageRequest` is in the list even though it is not URL-resolving**, because the
         * diagnostic reports what the source *overrides*, and a source that overrides it —
         * MangaDex, the canonical case, joins an at-home host onto a relative `Page.imageUrl`
         * there — is precisely a source whose `imageUrl` the host must not interpret. When this
         * list named only the four chain entry points, the real MangaDex was reported as
         * `overrides = none` in the on-screen rejection diagnostic, which contradicted the
         * failure it was explaining.
         *
         * Kept as one list so the probe and the diagnostic cannot disagree — the mismatch is what
         * let a customising source be reported as `none`.
         */
        val CHAIN_ENTRY_POINTS = listOf(
            "getImageUrl",
            "fetchImageUrl",
            "imageUrlRequest",
            "imageUrlParse",
            "imageRequest",
        )

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
 * API sources (MangaDex is the canonical one) build `Page(index, url = cacheKey, imageUrl = "/data/…")`
 * — a **relative path**, not an absolute one — while HTML sources put the fetchable address in
 * `url` and leave `imageUrl` empty. Reading the wrong one yields either `""` or an NPE depending on
 * which method you happened to write — so the rule was implemented twice
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
