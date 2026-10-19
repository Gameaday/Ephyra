package ephyra.feature.reader.model

import ephyra.domain.chapter.model.Chapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins the rule that decides where a chapter opens.
 *
 * The reader addresses content by *item* index (transition cards included), while a chapter's
 * start flags describe a *page*. Computing one from the other with a fixed offset only holds while
 * the item list is exactly `[Prev, page0, ...]`; a hidden leading page or a list whose pages have
 * not arrived points the offset at the second page or at the end-of-chapter card. These cases
 * state the contract in terms of the pages a user sees.
 */
class ReaderStartPositionTest {

    private fun chapter(id: Long, pageCount: Int, hidden: Set<Int> = emptySet()): ReaderChapter {
        val readerChapter = ReaderChapter(
            Chapter.create().copy(id = id, mangaId = 1L, name = "Chapter $id"),
        )
        val pages = (0 until pageCount).map { index ->
            ReaderPage(index, "http://page/$index").apply {
                this.chapter = readerChapter
                isAbsorbed = index in hidden
            }
        }
        readerChapter.state = ReaderChapter.State.Loaded(pages)
        return readerChapter
    }

    private fun itemsFor(chapter: ReaderChapter, prev: ReaderChapter? = null): List<Any> {
        val items = mutableListOf<Any>()
        if (prev != null) items.add(ChapterTransition.Prev(chapter, prev))
        // Matches PagerViewer/WebtoonViewer.rebuildItems: hidden pages never become items.
        chapter.pages.orEmpty().filterNot { it.isHidden }.let(items::addAll)
        items.add(ChapterTransition.Next(chapter, null))
        return items
    }

    @Test
    fun `a forward chapter opens on the first page, not on the transition`() {
        val current = chapter(2, pageCount = 3).apply { startingAtBeginning = true }
        val items = itemsFor(current, prev = chapter(1, pageCount = 2))

        assertEquals(1, resolveReaderStartIndex(items, current))
    }

    /**
     * A leading hidden page means the first item is not the first page. "Beginning" still means the
     * first page that is displayed, resolved by identity rather than by offset.
     */
    @Test
    fun `a hidden first page does not make the chapter open on the transition`() {
        val current = chapter(2, pageCount = 3, hidden = setOf(0)).apply { startingAtBeginning = true }
        val items = itemsFor(current, prev = chapter(1, pageCount = 2))

        assertEquals(items.indexOf(current.pages!![1]), resolveReaderStartIndex(items, current))
        assertEquals(1, resolveReaderStartIndex(items, current))
    }

    /**
     * Before the page list arrives the item list is `[Prev, Next]`. A fixed offset of 1 therefore
     * addresses the *end-of-chapter* card — the reader would continue straight into the next
     * chapter. The first page item is the only safe fallback.
     */
    @Test
    fun `an item list without pages never resolves to the end-of-chapter card`() {
        val current = chapter(2, pageCount = 0).apply { startingAtBeginning = true }
        val items = listOf(
            ChapterTransition.Prev(current, chapter(1, pageCount = 2)),
            ChapterTransition.Next(current, null),
        )

        assertEquals(0, resolveReaderStartIndex(items, current))
    }

    @Test
    fun `a chapter without a previous transition opens at item zero`() {
        val current = chapter(1, pageCount = 3).apply { startingAtBeginning = true }
        val items = itemsFor(current)

        assertEquals(0, resolveReaderStartIndex(items, current))
    }

    @Test
    fun `backward navigation opens on the last displayed page`() {
        val current = chapter(2, pageCount = 4, hidden = setOf(3)).apply { startFromEnd = true }
        val items = itemsFor(current, prev = chapter(1, pageCount = 2))

        val expected = items.indexOf(current.pages!![2])
        assertEquals(expected, resolveReaderStartIndex(items, current))
    }

    @Test
    fun `resume opens on the requested page even when earlier pages are hidden`() {
        val current = chapter(2, pageCount = 4, hidden = setOf(0, 2)).apply { requestedPage = 3 }
        val items = itemsFor(current, prev = chapter(1, pageCount = 2))

        val expected = items.indexOf(current.pages!![3])
        assertEquals(expected, resolveReaderStartIndex(items, current))
        assertEquals(2, expected)
    }

    @Test
    fun `an out-of-range request falls back to the first displayed page`() {
        val current = chapter(2, pageCount = 3, hidden = setOf(0)).apply { requestedPage = 99 }
        val items = itemsFor(current, prev = chapter(1, pageCount = 2))

        assertEquals(1, resolveReaderStartIndex(items, current))
    }

    @Test
    fun `a request naming a hidden page falls back to the first displayed page`() {
        val current = chapter(2, pageCount = 3).apply {
            requestedPage = 0
            pages!![0].isBlockedByFilter = true
        }
        val items = itemsFor(current, prev = chapter(1, pageCount = 2))

        assertEquals(1, resolveReaderStartIndex(items, current))
    }

    @Test
    fun `empty items resolve to zero rather than throwing`() {
        assertEquals(0, resolveReaderStartIndex(emptyList(), null))
        assertEquals(0, resolveReaderStartIndex(emptyList(), chapter(1, pageCount = 2)))
    }

    @Test
    fun `an unresolved chapter still resolves to its first page item`() {
        val current = ReaderChapter(Chapter.create().copy(id = 5L, mangaId = 1L, name = "Ch 5"))
        val items = listOf(
            ChapterTransition.Prev(current, null),
            ReaderPage(0, "http://page/0").apply { chapter = current },
            ChapterTransition.Next(current, null),
        )

        assertEquals(1, resolveReaderStartIndex(items, current))
    }
}
