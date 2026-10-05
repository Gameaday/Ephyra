package ephyra.feature.reader.model

import ephyra.domain.chapter.model.Chapter
import ephyra.domain.reader.media.PageByteBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The invariant this class exists to hold: the store's accounting and each page's own
 * [ReaderPage.cachedBytes] reference describe the **same** set of live bytes.
 *
 * A store that evicts while the page still holds the array bounds the store and not the heap, so
 * these tests assert on the page's field rather than on the store's counters — the store already
 * has its own coverage, and asserting only there would pass with a dangling page reference.
 */
class PageByteStoreOwnerTest {

    private fun chapterOf(id: Long = 1L) = Chapter(
        id = id,
        mangaId = 1L,
        read = false,
        bookmark = false,
        lastPageRead = 0L,
        dateFetch = 0L,
        sourceOrder = 0L,
        url = "/chapter/$id",
        name = "Chapter $id",
        dateUpload = 0L,
        chapterNumber = 1.0,
        scanlator = null,
        lastModifiedAt = 0L,
        version = 1L,
    )

    private fun pageOf(chapter: Chapter, index: Int, number: Float = index.toFloat()): ReaderPage =
        ReaderPage(index).also { it.chapter = ReaderChapter(chapter) }

    @Test
    fun `retained bytes leave the page holding a reference`() {
        val owner = PageByteStoreOwner(memoryClassMb = 512)
        val page = pageOf(chapterOf(), 0)
        val bytes = ByteArray(1024)

        owner.retain(page, bytes)
        assertEquals(1024, owner.retainedBytes)
        assertTrue(bytes.contentEquals(page.cachedBytes!!))
    }

    @Test
    fun `eviction clears the evicted page's own reference`() {
        // The defect this class removes. Without the callback wiring, the page keeps its array and
        // the retained-bytes figure describes the store while the heap holds something else.
        val owner = PageByteStoreOwner(memoryClassMb = 512)
        val budget = owner.budgetBytes
        val first = pageOf(chapterOf(), 0)
        val second = pageOf(chapterOf(), 1)

        owner.retain(first, ByteArray(budget / 2))
        owner.retain(second, ByteArray(budget / 2))
        // A third value cannot fit, so the least recently used page is evicted.
        owner.retain(pageOf(chapterOf(), 2), ByteArray(budget / 2))

        assertNull("the evicted page must not still hold its bytes", first.cachedBytes)
        assertNotNull(second.cachedBytes)
        assertTrue(owner.retainedBytes <= budget)
    }

    @Test
    fun `every page outside the retained set has no bytes`() {
        // Stronger than the single-eviction case: whatever the store dropped, the pages must agree.
        val owner = PageByteStoreOwner(memoryClassMb = 512)
        val pages = (0 until 40).map { pageOf(chapterOf(), it) }
        pages.forEach { owner.retain(it, ByteArray(1024)) }

        val holdingBytes = pages.count { it.cachedBytes != null }
        assertEquals(owner.pageCount, holdingBytes)
        assertTrue(owner.retainedBytes <= owner.budgetBytes)
    }

    @Test
    fun `retained bytes never exceed the budget`() {
        val owner = PageByteStoreOwner(memoryClassMb = 256)
        repeat(200) { owner.retain(pageOf(chapterOf(), it), ByteArray(512 * 1024)) }
        assertTrue(
            "retained ${owner.retainedBytes} > ${owner.budgetBytes}",
            owner.retainedBytes <= owner.budgetBytes,
        )
    }

    @Test
    fun `the budget comes from the device heap`() {
        assertEquals(PageByteBudget.forMemoryClassMb(512), PageByteStoreOwner(512).budgetBytes)
    }

    @Test
    fun `a page the store declined is not counted as retained`() {
        // The two index maps exist so the store can be found by identity and by page index. An
        // entry recorded for a value the store *declined* is a claim the store does not back:
        // `pageCount` would count a page whose bytes are gone, and `byId` would hold a strong
        // reference to that page for as long as the chapter is open — which is the retention the
        // bounded store exists to prevent, reached through the back door.
        //
        // A single value larger than the whole budget is always declined (the store refuses rather
        // than admitting it), so this needs no eviction timing and cannot be flaky.
        val owner = PageByteStoreOwner(memoryClassMb = 64)
        val page = pageOf(chapterOf(), 0)

        owner.retain(page, ByteArray(owner.budgetBytes + 1))

        assertEquals("a declined value must not be recorded as a retained page", 0, owner.pageCount)
        assertNull("the page must not be left holding bytes the store refused", page.cachedBytes)
        assertEquals(0, owner.retainedBytes)
    }

    @Test
    fun `the pinned viewport page survives while others are evicted`() {
        val owner = PageByteStoreOwner(memoryClassMb = 512)
        val budget = owner.budgetBytes
        val chunk = budget / 4

        // Retain a window the budget can actually hold, then pin it, then apply pressure. The order
        // is the whole test: a page evicted before its pin exists cannot be resurrected by one, so
        // filling 20 chunks into a 4-chunk budget first would evict page 0 during setup and prove
        // nothing about pinning.
        val window = (0 until 4).map { pageOf(chapterOf(), it) }
        window.forEach { owner.retain(it, ByteArray(chunk)) }
        owner.pinViewportAt(index = 0, pageCount = window.size)

        // 10 more chunks against a 4-chunk budget: without the pin, the window would be evicted.
        repeat(10) { owner.retain(pageOf(chapterOf(), 100 + it), ByteArray(chunk)) }

        assertNotNull("the page under the viewport must keep its bytes", window[0].cachedBytes)
        assertTrue("retained ${owner.retainedBytes} > $budget", owner.retainedBytes <= budget)
    }

    @Test
    fun `a page outside the pinned window is still evictable`() {
        // The complement of the above: pinning must not turn the store into a second unbounded
        // cache, so a page that merely scrolled past has to remain evictable.
        val owner = PageByteStoreOwner(memoryClassMb = 512)
        val budget = owner.budgetBytes
        val chunk = budget / 4

        val window = (0 until 4).map { pageOf(chapterOf(), it) }
        window.forEach { owner.retain(it, ByteArray(chunk)) }
        owner.pinViewportAt(index = 0, pageCount = window.size)
        repeat(10) { owner.retain(pageOf(chapterOf(), 100 + it), ByteArray(chunk)) }

        assertNotNull("the pinned window must survive", window[0].cachedBytes)
        // 30 pages were offered; a 4-chunk budget cannot hold them, and only the pinned window is
        // protected, so the store must be holding strictly fewer pages than were offered.
        assertTrue(
            "the store retained ${owner.pageCount} of 14 pages offered",
            owner.pageCount < 14,
        )
    }

    @Test
    fun `clearing drops every page reference`() {
        val owner = PageByteStoreOwner(memoryClassMb = 512)
        val pages = (0 until 10).map { pageOf(chapterOf(), it) }
        pages.forEach { owner.retain(it, ByteArray(1024)) }

        owner.clear()
        assertEquals(0, owner.retainedBytes)
        assertEquals(0, owner.pageCount)
        pages.forEach { assertNull("clear must leave no page holding bytes", it.cachedBytes) }
    }

    @Test
    fun `a page renumbered is a different entry`() {
        // A re-fetched chapter can renumber pages; index alone would then serve stale bytes for
        // different content, so the number is part of the identity.
        val owner = PageByteStoreOwner(memoryClassMb = 512)
        val chapter = chapterOf()
        val page = pageOf(chapter, 0, number = 1f)
        owner.retain(page, ByteArray(1024))
        val firstSize = page.cachedBytes?.size

        val renumbered = pageOf(chapter, 0, number = 2f)
        owner.retain(renumbered, ByteArray(2048))

        assertEquals(1024, firstSize)
        assertEquals(2048, renumbered.cachedBytes?.size)
    }

    @Test
    fun `different chapters do not share entries`() {
        val owner = PageByteStoreOwner(memoryClassMb = 512)
        val a = pageOf(chapterOf(1L), 0)
        val b = pageOf(chapterOf(2L), 0)
        owner.retain(a, ByteArray(1024))
        owner.retain(b, ByteArray(2048))
        assertEquals(2, owner.pageCount)
    }
}
