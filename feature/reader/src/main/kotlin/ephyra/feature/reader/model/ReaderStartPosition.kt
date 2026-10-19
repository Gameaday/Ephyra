package ephyra.feature.reader.model

/**
 * Resolves the index a reader surface must open a chapter at.
 *
 * **Why this is a policy rather than two copies of an expression.** The pager and the continuous
 * reader address their content by *item* index: the chapter's pages, plus a
 * [ChapterTransition.Prev] before them and a [ChapterTransition.Next] after them. The chapter's own
 * flags, however, describe a *page*: "start at the beginning" does not mean "item 1", it means
 * "the first page that is actually shown". Those two coincide only while the item list is exactly
 * `[Prev, page0, ...]`. A leading page that is hidden, or a list whose pages have not arrived yet,
 * breaks that assumption: the same offset then addresses the second page or the *end-of-chapter*
 * card, which is how a chapter could open partway in or skip straight through.
 *
 * Resolving the page object instead of the offset makes the start position independent of how many
 * non-page items happen to precede it.
 *
 * @param items the reader surface's current item list, in display order.
 * @param chapter the chapter those items belong to, or null while it is being replaced.
 * @return an index into [items], always inside `0 until items.size` when [items] is non-empty.
 */
fun resolveReaderStartIndex(items: List<Any>, chapter: ReaderChapter?): Int {
    if (items.isEmpty()) return 0

    val firstPageIndex = items.indexOfFirst { it is ReaderPage }
    val lastPageIndex = items.indexOfLast { it is ReaderPage }

    // The page the chapter itself names as its first visible one. Resolving the *object* rather
    // than assuming an offset is what makes a hidden leading page (blocked, or absorbed by
    // smart-combine) irrelevant: items.indexOf is -1 for a page that is not displayed.
    val firstVisiblePageIndex = chapter?.pages
        ?.firstOrNull { !it.isHidden }
        ?.let { items.indexOf(it) }
        ?.takeIf { it >= 0 }

    return when {
        chapter?.startFromEnd == true ->
            if (lastPageIndex >= 0) lastPageIndex else items.lastIndex

        chapter?.startingAtBeginning == true ->
            // A forward chapter opens on its first displayed page. The fallback keeps a list that
            // has not materialised yet at its first page item, never on the Next transition.
            firstVisiblePageIndex ?: firstPageIndex.coerceAtLeast(0)

        else -> {
            val requestedPage = chapter?.let { it.pages?.getOrNull(it.requestedPage) }
            val requestedIndex = requestedPage
                ?.let { items.indexOf(it) }
                ?.takeIf { it >= 0 }
            requestedIndex ?: firstVisiblePageIndex ?: firstPageIndex.coerceAtLeast(0)
        }
    }
}
