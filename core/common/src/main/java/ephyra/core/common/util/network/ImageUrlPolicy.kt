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
