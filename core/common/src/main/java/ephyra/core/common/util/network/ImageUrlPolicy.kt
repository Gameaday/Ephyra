package ephyra.core.common.util.network

import ephyra.core.common.util.system.logcat
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException

/**
 * Raised when an image URL is structurally unusable, whatever the network does with it.
 *
 * **Why this needs its own type.** An unusable URL is a statement about the *address*, and the
 * reader's recovery for a bad address — ask the source for a different one — is different from
 * its recovery for a flaky connection. It arrives here as an [IOException] only so that the
 * existing "worth retrying" rule keeps working without a new arm; the *type* is what carries the
 * verdict that the URL, not the connection, is at fault. `TransientErrors.shouldReResolveUrl`
 * matches on it explicitly, exactly as it does for `UnknownHostException`.
 *
 * **Why "malformed" is a separate verdict from "did not resolve".** A name that does not resolve
 * (`DEF-023`) may well resolve on the next resolution — CDNs hand out fresh hosts. A host
 * containing a comma never resolves, on this attempt or any other, so re-requesting the identical
 * string is provably futile and the reader stops rather than burning its ladder on it. Folding the
 * two together would either give up on recoverable hosts or keep hammering the unrecoverable one.
 */
class MalformedImageUrlException(
    val url: String,
    val reason: String,
    /**
     * Diagnostic detail about *where this value came from*, appended to the message.
     *
     * Temporary and deliberately user-visible. There is no release audience for this build, and the
     * alternative was three commits of instrumentation that nobody could see: the reports were going
     * to logcat while the only reader had no logcat, so every round of "the error is unchanged" was
     * indistinguishable from "the error did not change". `url` and `reason` are unchanged, so
     * classification in `TransientErrors` and every existing assertion still hold.
     */
    val context: String? = null,
) : IOException(
    "Image URL is not a usable http(s) address ($reason): $url" +
        if (context == null) "" else "\n\n$context",
)

/**
 * Returns a copy of this failure carrying [details] as its visible context.
 *
 * Reconstructs rather than mutates so the exception stays immutable and the original is still the one
 * classification code matches on.
 */
fun MalformedImageUrlException.withContext(details: String): MalformedImageUrlException =
    MalformedImageUrlException(url, reason, details)

/**
 * Attaches [details] when this is already a malformed-URL failure, and otherwise wraps it in one.
 *
 * The wrapper is deliberately a `MalformedImageUrlException` so a source that throws something else
 * from `getImageUrl` still classifies as "ask the source again" rather than as an opaque crash —
 * but the original is kept as the cause, so nothing is lost.
 */
fun Throwable.asContextualised(details: String): Throwable =
    when (this) {
        is MalformedImageUrlException -> withContext(details)
        else -> MalformedImageUrlException(
            url = message ?: toString(),
            reason = "the source threw $javaClass.simpleName while resolving a page image",
            context = details,
        ).also { it.initCause(this) }
    }

/**
 * An image URL that has been resolved against a base URL **and** judged capable of addressing a
 * host. Holding one is proof both happened, in that order.
 *
 * **Why this is a type and not a function.** The pipeline's repeated defect was a two-step idiom —
 * resolve, then judge — written by hand at eight call sites, in two orders, and omitted entirely
 * from one of them. The ordering was never recorded anywhere except in the body of each function,
 * so a site could not forget the second half without a test failing, and did: `Downloader`
 * resolved without judging for its entire life. A `String` cannot carry "already judged", so this
 * makes the omission unrepresentable rather than merely discouraged.
 *
 * **What judgement does and does not mean.** See [ImageUrlPolicy] — the verdict is narrow. This
 * guarantees the value can address a host, not that it is reachable or correct. Nothing here
 * implies a request will succeed.
 */
@JvmInline
value class ResolvedImageUrl private constructor(val value: String) {

    companion object {

        /**
         * Resolve [url] against [baseUrl] and judge the result, or throw
         * [MalformedImageUrlException] naming [url] and why it was rejected.
         *
         * The sole constructor. There is no way to obtain one without the check having run.
         *
         * @throws MalformedImageUrlException if the resolved value cannot address a host.
         */
        fun of(url: String?, baseUrl: String?): ResolvedImageUrl {
            val resolved = ImageUrlPolicy.resolve(url, baseUrl)
            // Judged here rather than in each caller, and *after* resolution: a relative URL is
            // only meaningful once it has a base, so judging the raw value first would reject
            // shapes that resolve perfectly well.
            ImageUrlPolicy.requireUsable(resolved)
            return ResolvedImageUrl(resolved)
        }

        /**
         * A value the *source* produced, passed to the source's own `imageRequest` untouched.
         *
         * **Why this exists — the MangaDex contract.** A populated `Page.imageUrl` is opaque to
         * the host. MangaDex puts a **relative path** there (`"/data/<hash>/<file>"`) and its own
         * overridden `imageRequest` joins it onto an at-home host it reads out of `Page.url`
         * (`GET(mdAtHomeServerUrl + page.imageUrl)`). Resolving that path against `baseUrl` here
         * — or anywhere above the request builder — would splice two URLs into one
         * (`"<at-home-host>https://mangadex.org/data/..."`), a host that can never resolve, and
         * every page of every chapter would fail identically. Upstream Mihon never writes into a
         * populated `Page.imageUrl`; neither do we.
         *
         * Not judged, deliberately: a relative path is *incomplete*, not broken — the missing
         * half is knowledge only the source's `imageRequest` holds. Judging it as the host would
         * reject the majority of 1.6 extensions' pages for a rule the source is about to
         * supersede. The source that produced it owns both the resolution and the verdict.
         */
        fun opaque(value: String): ResolvedImageUrl = ResolvedImageUrl(value)
    }

    override fun toString(): String = value
}

/**
 * Decides whether an image URL is worth sending a request for, without sending one.
 *
 * **Why the reader needs this at all.** The reported failure was
 *
 * ```
 * Unable to resolve host "cmdxd98sb0x3yprd.mangadex.network,https": No address associated with hostname
 * ```
 *
 * That host is a *concatenation artifact*: a real image host with `,https` spliced onto the end of
 * it. OkHttp accepts it — a comma is not a forbidden character in a host, so
 * `toHttpUrlOrNull()` succeeds and DNS is asked for a name that cannot exist. The cost is a full
 * request that can only fail, a resolver message shown to the user verbatim, and, before `DEF-023`
 * was fixed, a retry that asked for the identical dead name.
 *
 * **Why this is a check on the URL and not a retry.** No number of requests makes
 * `cmxd98sb0x3yprd.mangadex.network,https` resolve. Detecting it before the request turns a
 * guaranteed failure into an immediate, correctly-classified one, and the reader's existing
 * response to a bad address — drop it, ask the source again — then applies to the case where it
 * previously did not.
 *
 * **Deliberately narrow.** This rejects only what cannot address a host. It does not judge whether
 * a URL is reachable, fresh, well-formed as *content*, or even one this app should be talking to;
 * those are network and source questions, and folding them in here would make a transient outage
 * look like a bug and discard URLs that would have worked.
 */
object ImageUrlPolicy {

    /**
     * A hostname as RFC 1123 permits it: dot-separated labels of letters, digits and inner
     * hyphens. Also matches IPv4, which is digits and dots. A trailing dot is deliberately not
     * matched: it is legal but never appears from a source and is more often a splicing artifact.
     */
    private val DNS_HOST = Regex(
        "^[A-Za-z0-9]([A-Za-z0-9_-]*[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9_-]*[A-Za-z0-9])?)*$",
    )

    /**
     * IPv6 literals, which `HttpUrl.host` returns *without* brackets. Permissive on purpose: the
     * point is to exclude a splicing artifact such as `host,https`, not to re-validate an address
     * the resolver is better placed to judge.
     */
    private val IPV6_HOST = Regex("^[0-9A-Fa-f:.]+$")

    /**
     * Any RFC 3986 scheme prefix: `scheme:` at the very start of the string.
     *
     * The character class excludes `/`, which is what keeps a relative path containing a colon
     * (`chapter/1:2.jpg`) from being read as a scheme — the colon is only a scheme delimiter when
     * nothing that could be a path separator precedes it.
     */
    private val ANY_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.\\-]*:")

    /**
     * ASCII control characters, which cannot appear in a URL and are what a `src` attribute read
     * across a line break — or a URL built from a multi-line template — leaves behind.
     *
     * Excludes space deliberately: a space inside a path is more likely to be a real, if
     * unencoded, space than a wrapping artifact, and silently deleting it would join two path
     * segments into a different file. Surrounding whitespace is handled by trimming instead.
     */
    private val CONTROL_CHARACTERS = Regex("[\\u0000-\\u001F\\u007F]")

    /**
     * The scheme separator. A real http(s) URL contains exactly one; a URL that contains more
     * than one is two URLs run together, whatever the character between them happened to be.
     *
     * **Why this check exists.** The reported failure arrived as
     * `cmdxd98sb0x3yprd.mangadex.networkhttps` — the at-home image server with the scheme of the
     * API URL glued onto its host, the two joined with no separator at all. Every hostname check
     * passes on it, because `networkhttps` is a perfectly legal RFC 1123 name. It is not a
     * hostname problem; it is two URLs where one was expected. Counting separators identifies
     * that without a heuristic, where "does the host end in `https`" would also reject the
     * perfectly legitimate `myhttpserver.com`.
     */
    private val SCHEME_SEPARATOR = Regex("://")

    /**
     * Turns [url] into an absolute `http(s)` address using [baseUrl] when it is not already one.
     *
     * **Why this exists.** The reported failure was
     *
     * ```
     * Expected URL scheme 'http' or 'https' but no scheme was found for //cdn.example.com/1.jpg
     * ```
     *
     * thrown by OkHttp while *building* a request — no request was sent, so nothing about the
     * network, the host, or a rate limit was involved. A source is free to name a page relatively:
     * `img.attr("src")` rather than `absUrl("src")` is ordinary source code, and a protocol-relative
     * `//cdn…` is what a `<base>`-tagged site emits. Upstream resolved those against `baseUrl` in
     * `HttpSource.getImageUrl`; this fork's `getImageUrl` is the deprecated *network* variant, so
     * that resolution existed nowhere and the raw string went straight to OkHttp.
     *
     * **Why resolving rather than rejecting.** [defectOf] already rejects a scheme-less URL, and
     * rejecting is the right verdict for a URL that *cannot* address a host. But a relative URL is
     * not broken — it is incomplete, and the missing half is a value the app already holds. Turning
     * it into an error spends a request's worth of work, a retry ladder and a user-facing message to
     * arrive at an address we could simply have formed. [defectOf] remains the backstop for what
     * genuinely cannot be resolved.
     *
     * **The rule**, in order:
     * - already absolute `http(s)` → returned **byte-identical**. Canonicalising here would be a
     *   regression, not a cleanup: a signed CDN URL is a query string whose exact spelling is the
     *   credential (`DEF-020`).
     * - protocol-relative (`//host/path`) → prefixed with [baseUrl]'s scheme only, as a browser does.
     * - any other scheme (`data:`, `file:`, `javascript:`) → returned **unchanged**, never prefixed.
     *   A `data:` URL is not a broken path to be completed, it is a payload the source chose to
     *   inline; prefixing a host onto it would manufacture a request that was never asked for, and
     *   [defectOf] is what rejects it.
     * - otherwise → [baseUrl] with its trailing slash and [url]'s leading slash collapsed, so the
     *   two cannot produce a doubled or missing separator.
     * - no usable [baseUrl] → returned unchanged, so the existing verdict path reports the original
     *   string instead of this function inventing a second, different failure.
     *
     * Root-relative rather than RFC 3986 relative resolution (`HttpUrl.resolve`) on purpose. This
     * codebase already reads a leading `/` as "relative to `baseUrl`": `getUrlWithoutDomain` stores
     * a manga or chapter URL as `uri.path`, and `pageListRequest` rebuilds it as
     * `baseUrl + chapter.url`. Resolving against the origin instead would make page resolution
     * disagree with chapter resolution for every source whose `baseUrl` carries a path — exactly
     * the set of sources for which the two answers differ.
     */
    fun resolve(url: String?, baseUrl: String?): String {
        if (url.isNullOrBlank()) return url.orEmpty()

        // Everything below reasons about the repaired string, never the raw one. An unrepaired value
        // that starts with `https://` only after trimming would otherwise take the absolute branch
        // below on the *raw* string and miss it entirely.
        val repaired = repair(url)
        if (repaired.isEmpty()) return url

        // The overwhelmingly common case, and the one that must not be touched: see the signed-URL
        // note above. Checked before the general scheme test purely to keep this a prefix compare.
        if (repaired.startsWith("http://", ignoreCase = true) || repaired.startsWith("https://", ignoreCase = true)) {
            return repaired
        }

        // A non-http scheme is a decision, not an omission. Left alone on purpose; see above.
        if (ANY_SCHEME.containsMatchIn(repaired)) return repaired

        // The base has to be able to *lend* a scheme, or there is no absolute address to form. A
        // base that cannot is returned against rather than concatenated, because
        // `mangadex.org/data/1.jpg` is a different string that still cannot address a host, and
        // reporting the original is the verdict the caller can act on.
        val base = baseUrl?.trim().orEmpty()
        val scheme = base.substringBefore("://")
        if (base.isBlank() || scheme.isBlank() || scheme == base) return repaired

        return if (repaired.startsWith("//")) {
            "$scheme:$repaired"
        } else {
            "${base.trimEnd('/')}/${repaired.trimStart('/')}"
        }
    }

    /**
     * Trims surrounding whitespace, and changes nothing else.
     *
     * **Why nothing else.** Rewriting what a source produced is not the app's job, and the other two
     * repairs were actively harmful:
     *
     *  - *Stripping control characters* deleted the character separating two URLs, turning a visibly
     *    malformed composite into a well-formed one whose host then failed at DNS. Guarding that
     *    hazard was worse than not having it.
     *  - *Decoding `&amp;`* is right in a query, where a signature is computed over the decoded form,
     *    and wrong in a path segment, where a literal `&amp;` is a literal.
     *
     * Trimming stays because the alternative is demonstrated and bad: leading whitespace stops the
     * value *looking* absolute, so it is treated as a relative path and joined onto `baseUrl`. The
     * result parses, has a valid host, and passes every check — then 404s, reading as a missing page
     * rather than a malformed URL. Silent corruption that nothing downstream can catch.
     */
    private fun repair(raw: String): String = raw.trim()

    private fun countSchemeSeparators(value: String): Int =
        SCHEME_SEPARATOR.findAll(value.takeWhile { it != '?' && it != '#' }).count()

    /** Renders control characters as escapes so they are visible in a log line. */
    private fun escapeControlCharacters(value: String): String = buildString {
        value.forEach { c ->
            when (c) {
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.isISOControl()) append("\\u%04x".format(c.code)) else append(c)
            }
        }
    }

    /** Returns why [url] is unusable, or `null` when it is worth requesting. */
    fun defectOf(url: String?): String? {
        if (url.isNullOrBlank()) return "the URL is empty"

        // Before parsing, and deliberately so: the reported splice parses cleanly, with the second
        // URL's scheme absorbed into the host. Counting first is what sees the whole string rather
        // than the part the parser kept.
        if (countSchemeSeparators(url) > 1) {
            return "it contains more than one scheme separator, so two URLs have been joined"
        }

        val parsed = url.toHttpUrlOrNull() ?: return "it does not parse as a URL"

        val scheme = parsed.scheme.lowercase()
        if (scheme != "http" && scheme != "https") {
            return "the scheme is \"$scheme\", not http or https"
        }

        val host = parsed.host
        if (host.isBlank()) return "it has no host"

        // This is the check the reported failure turns on. The name is well-formed enough for
        // OkHttp to canonicalise and hand to DNS, so parsing alone cannot catch it.
        if (!DNS_HOST.matches(host) && !IPV6_HOST.matches(host)) {
            return "the host \"$host\" is not a hostname"
        }

        return null
    }

    /** True when [url] is worth requesting. */
    fun isUsable(url: String?): Boolean = defectOf(url) == null

    /**
     * Throws [MalformedImageUrlException] when [url] is not worth requesting.
     *
     * Throwing rather than returning a boolean is deliberate: every caller of this is on the path
     * to a request, and a boolean that is ignored is a failed request. The exception carries the
     * reason into the reader's classification, which is what turns it into "drop the URL and ask
     * the source again" instead of "retry this address".
     */
    fun requireUsable(url: String?) {
        val reason = defectOf(url) ?: return
        throw MalformedImageUrlException(url.orEmpty(), reason)
    }
}
