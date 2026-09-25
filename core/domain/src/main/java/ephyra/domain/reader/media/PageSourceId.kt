package ephyra.domain.reader.media

/**
 * Stable identity of the bytes behind a page, plus the revision that last changed them.
 *
 * A cache key built from the URL alone is not an identity. Sources routinely serve different
 * bytes from the same URL: a re-uploaded page, a regenerated scan, a source that renumbers its
 * image path after a chapter refresh. Two loads of one URL can therefore produce different pixels
 * while looking identical to any key that ignores [revision].
 *
 * [revision] is opaque on purpose. Callers must not derive it from a timestamp they compute
 * themselves, because two processes computing "now" for the same page must agree. A source that
 * cannot supply a meaningful revision should use [UNKNOWN_REVISION], which is a distinct value
 * from any real revision and keeps "no signal" from silently comparing equal to "unchanged".
 */
data class PageSourceId(
    val sourceKey: String,
    val pageKey: String,
    val revision: String = UNKNOWN_REVISION,
) {
    init {
        require(sourceKey.isNotBlank()) { "sourceKey must not be blank" }
        require(pageKey.isNotBlank()) { "pageKey must not be blank" }
    }

    /**
     * The single string form used as a cache-key component.
     *
     * Components are length-prefixed so that no combination of values can be re-partitioned into
     * a different one: without the prefix, pageKey "a_b" with sourceKey "x" and pageKey "a" with
     * sourceKey "x_b" would both render as `x_a_b` and collide.
     */
    fun cacheComponent(): String = buildString {
        appendField(sourceKey)
        appendField(pageKey)
        appendField(revision)
    }

    private fun StringBuilder.appendField(value: String) {
        append(value.length).append(':').append(value).append('|')
    }

    companion object {
        const val UNKNOWN_REVISION: String = "?"
    }
}
