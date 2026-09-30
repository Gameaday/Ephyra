package ephyra.core.common.util.network

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
    /** The rejected URL, exactly as the source produced it. Carried so a caller can compare it. */
    val url: String,
    /** Human-readable reason, from [ImageUrlPolicy.defectOf]. */
    val reason: String,
) : IOException("Image URL is not a usable http(s) address ($reason): $url")

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
     * Root-relative rather than RFC 3986 relative resolution (`HttpUrl.resolve`) on purpose: this
     * codebase already treats a stored `manga.url`/`chapter.url` as base-relative — see
     * `HttpSource.pageListRequest`, which concatenates `baseUrl + chapter.url` — and a source that
     * emits `a/1.jpg` means it relative to the site, not to whatever directory the current chapter
     * URL happens to sit in. Where `baseUrl` is an origin, which is its documented contract, the two
     * rules agree exactly; where it is not, this one matches the convention the rest of the source
     * layer already follows.
     */
    fun resolve(url: String?, baseUrl: String?): String {
        if (url.isNullOrBlank()) return url.orEmpty()

        // The overwhelmingly common case, and the one that must not be touched: see the signed-URL
        // note above. Checked before the general scheme test purely to keep this a prefix compare.
        if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
            return url
        }

        // A non-http scheme is a decision, not an omission. Left alone on purpose; see above.
        if (ANY_SCHEME.containsMatchIn(url)) return url

        val base = baseUrl?.trim().orEmpty()
        if (base.isBlank()) return url

        if (url.startsWith("//")) {
            val scheme = base.substringBefore("://")
            // A base with no scheme cannot lend one. Returning the input keeps the failure the
            // caller's own verdict produces, rather than inventing a different one here.
            if (scheme.isBlank() || scheme == base) return url
            return "$scheme:$url"
        }

        return "${base.trimEnd('/')}/${url.trimStart('/')}"
    }

    /** Returns why [url] is unusable, or `null` when it is worth requesting. */
    fun defectOf(url: String?): String? {
        if (url.isNullOrBlank()) return "the URL is empty"

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
