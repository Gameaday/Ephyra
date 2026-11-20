package ephyra.domain.content.source

import ephyra.core.common.util.network.ImageUrlPolicy

/**
 * The one way the reader and downloader are told where content lives.
 *
 * An adapter may produce content from an extension APK, an OPDS catalogue, an archive on disk, or
 * a Jellyfin server, and each has its own idea of an address. The reader and downloader must not
 * learn any of them. Before this type, "where does this come from" was a bare `String` — which is
 * how a three‑part cache key (`host,https://api.example.com/chapter,1790986074071`) reached DNS
 * and came back as `Unable to resolve host`.
 *
 * **Deliberately opaque.** The value is `private`, so the only way to obtain one is [of], which
 * resolves then judges. Callers cannot decompose it and branch on its shape — not a style choice:
 * a decomposable type gets decomposed, and "is this address usable" acquires a second home.
 *
 * [ImageUrlPolicy]'s verdict is narrow: the value can address a host. Nothing promises a request
 * succeeds.
 *
 * @see ImageUrlPolicy for what counts as an address
 * @see ephyra.core.common.util.network.ResolvedImageUrl for the same idea applied to images
 */
@JvmInline
value class ContentLocator private constructor(private val token: String) {

    /**
     * The persisted form, written by the store and read back by [fromPersisted].
     *
     * `internal` because persisting is the point: a locator that cannot survive a restart addresses
     * nothing on the next launch. Returns the engine id *and* the address together, so a caller
     * cannot persist half of it.
     */
    internal fun persisted(): String = token

    /** Which engine owns this address. Safe to read: it is an identity, not a shape. */
    val engineId: EngineId get() = EngineId(token.substringBefore(SEPARATOR))

    override fun toString(): String = "ContentLocator($engineId)"

    companion object {
        /**
         * Unit separator: a control character that cannot occur in a URL's scheme, authority, or
         * path. The split is unambiguous, and unlike a space it cannot be introduced or removed by
         * trimming — the exact failure mode behind the reported `hosthttps://…` splice.
         */
        private const val SEPARATOR: Char = '\u001F'

        /**
         * Resolve [url] against [baseUrl] and judge the result, or throw naming [url] and why.
         *
         * The sole constructor: no way to hold a locator whose value has not been judged.
         *
         * @param engineId the engine that produced this address; it is what will serve it later.
         * @throws ephyra.core.common.util.network.MalformedImageUrlException if the resolved value
         *   cannot address a host. Classified as [ephyra.core.common.util.network.FailureLayer.ADAPTER],
         *   so the failure is attributed to the adapter rather than the transport.
         */
        fun of(url: String?, baseUrl: String?, engineId: EngineId): ContentLocator {
            val resolved = ImageUrlPolicy.resolve(url, baseUrl)
            ImageUrlPolicy.requireUsable(resolved)
            return ContentLocator(engineId.value + SEPARATOR + resolved)
        }

        /**
         * Rehydrate a persisted locator without re-judging it.
         *
         * The only path that can build one from a token without re-running the check, because a
         * persisted value was *already* judged when written. Deliberately refuses a bare URL —
         * callers holding a `String` from anywhere else must go through [of].
         *
         * @throws IllegalArgumentException if the token carries no engine id, so did not come from [of].
         */
        internal fun fromPersisted(token: String): ContentLocator {
            require(token.contains(SEPARATOR)) { "Not a locator: it carries no engine id" }
            return ContentLocator(token)
        }

        /**
         * Whether [token] is a well‑formed persisted locator.
         *
         * For a migration triaging a column: a value failing this was written before locators
         * existed, so it must be converted or discarded rather than rehydrated.
         */
        internal fun isLocator(token: String): Boolean = token.contains(SEPARATOR)
    }
}
