package ephyra.domain.source.registry

/**
 * The canonical origin of a source: scheme, host, optional port, and path.
 *
 * `SRC-011` (`ADR-0012`): source identity is a pure function of the source's origin, so this is
 * the single value the whole registry family agrees on. Query and fragment are deliberately not
 * part of it — they are per-request state, not identity.
 */
@JvmInline
value class SourceOrigin(val value: String) {
    init {
        require(value.isNotBlank()) { "A source origin must not be blank" }
    }
}

/**
 * The one rule for turning whatever spelling of a source address the app has seen into the one
 * origin it names.
 *
 * Why a hand-written parser instead of `java.net.URI`: the rule must be deterministic across the
 * JVM and Android for the same input, and `URI` leaves too much of it to platform behavior
 * (empty paths, case handling, opaque URIs). This parser is total: any input either canonicalizes
 * or is rejected as `null`, and nothing throws.
 *
 * The path is kept, not just the host: two sources may legitimately live on the same host under
 * different paths, and collapsing them would merge two identities into one.
 */
object SourceOriginPolicy {

    /**
     * Canonicalizes [raw] into a [SourceOrigin], or returns null when it is not an http(s) origin.
     *
     * Rules: the scheme must be `http` or `https` (lowercased); the host is lowercased (IPv6 hosts
     * keep their `[...]` brackets); default ports (80/443) are dropped while explicit ports are
     * kept; the path is kept with trailing slashes removed; query and fragment are dropped;
     * userinfo is rejected.
     */
    fun canonicalize(raw: String): SourceOrigin? {
        // Query and fragment are per-request state; drop them before anything else so they can
        // never leak into the authority (a `?` after a bare host would otherwise become part of it).
        val noQuery = raw.trim().substringBefore('?').substringBefore('#')
        if (noQuery.isEmpty()) return null

        val schemeEnd = noQuery.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = noQuery.substring(0, schemeEnd).lowercase()
        if (scheme != "http" && scheme != "https") return null

        val rest = noQuery.substring(schemeEnd + 3)
        if (rest.isEmpty()) return null

        // Split authority from path before anything else: only a `/` can introduce the path.
        val pathStart = rest.indexOf('/')
        val authority = if (pathStart >= 0) rest.substring(0, pathStart) else rest
        val rawPath = if (pathStart >= 0) rest.substring(pathStart) else ""

        if (authority.isEmpty()) return null
        // Userinfo (`https://user@host/`) is a credential, not part of an origin; rejecting it
        // keeps credential-bearing spellings from silently becoming distinct identities — or from
        // collapsing into the credential-free spelling.
        if (authority.contains('@')) return null

        val host: String
        val port: String?
        if (authority.startsWith("[")) {
            // IPv6 literal: the host is bracketed, and only a `]:` sequence can introduce a port.
            val close = authority.indexOf(']')
            if (close <= 1) return null
            host = authority.substring(0, close + 1)
            val afterHost = authority.substring(close + 1)
            port = when {
                afterHost.isEmpty() -> null
                afterHost.startsWith(":") -> afterHost.substring(1)
                else -> return null
            }
        } else {
            val colon = authority.lastIndexOf(':')
            host = if (colon >= 0) authority.substring(0, colon) else authority
            port = if (colon >= 0) authority.substring(colon + 1) else null
            // A non-bracketed host must not contain a colon: anything that does is not a host
            // this rule can name, and guessing would mint identity from a malformed value.
            if (host.contains(':')) return null
        }
        if (host.isEmpty()) return null
        if (port != null && port.toIntOrNull() == null) return null

        val isDefaultPort = (scheme == "http" && port == "80") || (scheme == "https" && port == "443")
        val portPart = if (port != null && !isDefaultPort) ":$port" else ""

        val path = rawPath.trimEnd('/')

        return SourceOrigin("$scheme://${host.lowercase()}$portPart$path")
    }

    /**
     * Whether two spellings name the same origin.
     *
     * Inputs that cannot be canonicalized fall back to trimmed raw equality rather than being
     * declared unequal: an unregister must not fail to erase a malformed stored spelling just
     * because the rule cannot name its origin.
     */
    fun isSameOrigin(a: String, b: String): Boolean {
        val ca = canonicalize(a)
        val cb = canonicalize(b)
        return if (ca != null && cb != null) ca == cb else a.trim() == b.trim()
    }
}
