package ephyra.feature.reader.session

/**
 * Maps the reader's entities onto the session's stable identities.
 *
 * **Why the session does not use the numeric chapter id.** `ReaderChapterId` is documented as a
 * "stable target-reader identity" that does not use legacy numeric source ids, and a database id is
 * not one: it is reassigned by a restore, differs between a device and a server, and means nothing
 * to a source. The chapter's own URL, scoped by its source, is the identity the source itself
 * understands and the one that survives both.
 *
 * **Why these take strings rather than `Chapter` and `Page`.** They need a URL and an index, not a
 * DTO, and taking the entities would drag the whole legacy model — and Android with it — into the
 * one piece of the session that has to stay verifiable without a device. A wrong identity here fails
 * silently: the session concludes it is looking at a different chapter, and the symptom is a chapter
 * that will not restore rather than an error anyone can trace.
 */
object ReaderSessionIdentity {

    /**
     * A chapter's identity: its source, then its URL.
     *
     * [sourceId] is part of the identity rather than read from the chapter because a `Chapter` does
     * not carry its source — the manga does — and chapter URLs collide across sources.
     */
    fun chapterId(chapterUrl: String, sourceId: Long): ReaderChapterId =
        ReaderChapterId("$sourceId:$chapterUrl")

    /**
     * A page's identity, relative to its chapter.
     *
     * The page's own URL is preferred because it is the source's identity for that page. A page
     * constructed without one falls back to its position, which is what the session's `pageIds` list
     * is indexed by and so has to agree with it.
     */
    fun pageId(pageUrl: String, pageIndex: Int, chapterId: ReaderChapterId): ReaderPageId {
        // `chapterId.value`, never `$chapterId`: ReaderChapterId is a data class, so interpolating
        // it embeds `ReaderChapterId(value=...)` in the identity. That is still unique, and still
        // wrong -- these identities are persisted in saved state, so a change to that type's
        // toString would silently invalidate every restored page.
        val chapter = chapterId.value
        return if (pageUrl.isNotBlank()) {
            ReaderPageId("$chapter#$pageUrl")
        } else {
            ReaderPageId("$chapter#$pageIndex")
        }
    }

    /** The identity of every page in a loaded chapter, in order. */
    fun pageIds(pageUrls: List<String>, chapterId: ReaderChapterId): List<ReaderPageId> =
        pageUrls.mapIndexed { index, url -> pageId(url, index, chapterId) }

    /** The position [pageId] denotes within [pageIds], or null when it is not one of them. */
    fun indexOf(pageIds: List<ReaderPageId>, pageId: ReaderPageId): Int? =
        pageIds.indexOf(pageId).takeIf { it >= 0 }
}
