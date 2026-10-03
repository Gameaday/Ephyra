package ephyra.domain.content.source

import ephyra.core.common.util.network.ImageUrlPolicy

/**
 * The one way the reader and downloader are told where content lives.
 *
 * **What this is for.** An adapter may produce content from an extension APK, an OPDS catalogue, an
 * archive on disk, or a Jellyfin server, and each of those has its own idea of an address. The
 * reader and downloader must not learn any of them. Before this type existed, "where does this come
 * from" was a bare `String`, and interpreting it was the reader's problem — which is how a
 * three-part cache key, `host,https://api.example.com/chapter,1790986074071`, reached DNS and came
 * back as `Unable to resolve host`. Nothing between the adapter and the socket refused it, because
 * refusing it required understanding a shape that varies by source.
 *
 * **Why it is deliberately opaque.** The value is `private`, so the only way to obtain one is
 * through [of], which resolves and then judges. Callers cannot decompose it — they cannot read a
 * string out and branch on its shape. That is not stylistic: a type the reader *can* decompose will
 * eventually be decomposed, and the decision "is this address usable" will acquire a second home.
 *
 * **What judgement does and does not mean.** [ImageUrlPolicy]'s verdict is narrow — the value can
 * address a host. It does not imply the address is reachable, correct, or even the right content.
 * Nothing here promises a request will succeed.
 *
 * @see ImageUrlPolicy for what counts as an address
 * @see ephyra.core.common.util.network.ResolvedImageUrl for the same idea applied to images
 */
@JvmInline
value class ContentLocator private constructor(private val token: String) {

    /**
     * The persisted form, written by the store and read back by [fromPersisted].
     *
     * `internal` rather than private because persisting is the whole point of the type: a locator
     * that cannot survive a restart cannot address anything on the next launch. It returns the
     * engine id *and* the address together, so a caller cannot persist half of it.
     */
    internal fun persisted(): String = token

    /** Which engine owns this address. Safe to read: it is an identity, not a shape. */
    val engineId: EngineId get() = EngineId(token.substringBefore(SEPARATOR))

    override fun toString(): String = "ContentLocator($engineId)"

    companion object {
        /**
         * Unit separator: a control character that cannot occur in a URL's scheme, authority, or
         * path. The split is therefore unambiguous in a way that guessing "where does the host end"
         * never is, and unlike a space it cannot be introduced or removed by trimming — which is the
         * exact failure mode that produced the reported `hosthttps://…` splice.
         */
        private const val SEPARATOR: Char = '\u001F'

        /**
         * Resolve [url] against [baseUrl] and judge the result, or throw naming [url] and why.
         *
         * The sole constructor. There is no way to hold a locator whose value has not been judged.
         *
         * @param engineId the engine that produced this address; it is what will serve it later.
         * @throws ephyra.core.common.util.network.MalformedImageUrlException if the resolved value
         *   cannot address a host. That exception is classified as
         *   [ephyra.core.common.util.network.FailureLayer.ADAPTER], so the failure is attributed to
         *   the adapter that produced it rather than to the transport about to carry it.
         */
        fun of(url: String?, baseUrl: String?, engineId: EngineId): ContentLocator {
            val resolved = ImageUrlPolicy.resolve(url, baseUrl)
            ImageUrlPolicy.requireUsable(resolved)
            return ContentLocator(engineId.value + SEPARATOR + resolved)
        }

        /**
         * Rehydrate a persisted locator without re-judging it.
         *
         * **Why this exists and why it is narrow.** A locator is stored, so something has to read it
         * back. This is the only path that can construct one from a token without re-running the
         * check, and it exists because a persisted value was *already* judged when it was written.
         * It deliberately does not accept a bare URL — a caller holding a `String` from anywhere else
         * must go through [of], which judges it.
         *
         * @throws IllegalArgumentException if the token carries no engine id, which means it did not
         *   come from [of].
         */
        internal fun fromPersisted(token: String): ContentLocator {
            require(token.contains(SEPARATOR)) { "Not a locator: it carries no engine id" }
            return ContentLocator(token)
        }

        /**
         * Whether [token] is a well-formed persisted locator.
         *
         * For a migration that must triage a column: a value failing this was written before
         * locators existed, so it has to be converted or discarded rather than rehydrated.
         */
        internal fun isLocator(token: String): Boolean = token.contains(SEPARATOR)
    }
}
