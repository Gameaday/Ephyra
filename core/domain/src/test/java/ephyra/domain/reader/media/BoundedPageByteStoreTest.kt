package ephyra.domain.reader.media

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BoundedPageByteStoreTest {

    private fun id(name: String) = PageSourceId("src", name, "r1")

    private fun bytes(size: Int, fill: Byte = 1) = ByteArray(size) { fill }

    @Test
    fun `a stored value is returned on the next read`() {
        val store = BoundedPageByteStore(budgetBytes = 1024)
        assertTrue(store.put(id("a"), bytes(100)))
        assertArrayEquals(bytes(100), store.get(id("a")))
    }

    @Test
    fun `an unknown page is a miss`() {
        val store = BoundedPageByteStore(budgetBytes = 1024)
        assertNull(store.get(id("missing")))
    }

    @Test
    fun `retained bytes never exceed the budget`() {
        // The defect this replaces: unbounded growth on the page object as the reader scrolls.
        val store = BoundedPageByteStore(budgetBytes = 1000)
        repeat(50) { store.put(id("page-$it"), bytes(300)) }
        assertTrue(store.retainedBytes <= 1000, "retained ${store.retainedBytes} > 1000")
    }

    @Test
    fun `a long scroll keeps the store within budget`() {
        val store = BoundedPageByteStore(budgetBytes = 2048)
        repeat(500) { store.put(id("page-$it"), bytes(512)) }
        assertTrue(store.retainedBytes <= 2048)
        assertTrue(store.entryCount <= 4)
    }

    @Test
    fun `the least recently used entry is evicted first`() {
        val store = BoundedPageByteStore(budgetBytes = 300)
        store.put(id("a"), bytes(100))
        store.put(id("b"), bytes(100))
        store.put(id("c"), bytes(100))
        // Touch a and b so c is now the least recently used.
        store.get(id("a"))
        store.get(id("b"))
        store.put(id("d"), bytes(100))

        assertNull(store.get(id("c")), "c was least recently used and should be evicted")
        assertNotNull(store.get(id("a")))
        assertNotNull(store.get(id("b")))
    }

    @Test
    fun `a value larger than the whole budget is refused`() {
        val store = BoundedPageByteStore(budgetBytes = 100)
        assertFalse(store.put(id("huge"), bytes(500)))
        assertNull(store.get(id("huge")))
    }

    @Test
    fun `an oversized value does not empty a populated store`() {
        // Admitting it would evict everything and then itself, leaving the store worse off.
        val store = BoundedPageByteStore(budgetBytes = 300)
        store.put(id("a"), bytes(100))
        store.put(id("b"), bytes(100))
        store.put(id("c"), bytes(100))

        assertFalse(store.put(id("huge"), bytes(1000)))
        assertEquals(300, store.retainedBytes)
        assertEquals(3, store.entryCount)
    }

    @Test
    fun `a pinned page survives eviction pressure`() {
        val store = BoundedPageByteStore(budgetBytes = 300)
        store.put(id("keep"), bytes(100))
        store.pin(id("keep"))
        store.put(id("a"), bytes(100))
        store.put(id("b"), bytes(100))
        store.put(id("c"), bytes(100))

        assertNotNull(store.get(id("keep")), "a pinned page must not be evicted")
        assertTrue(store.retainedBytes <= 300)
    }

    @Test
    fun `a value is refused when only pinned pages could be evicted`() {
        val store = BoundedPageByteStore(budgetBytes = 200)
        store.put(id("p1"), bytes(100))
        store.put(id("p2"), bytes(100))
        store.pin(id("p1"))
        store.pin(id("p2"))

        assertFalse(store.put(id("new"), bytes(100)))
        assertEquals(1, store.refusedForPinnedEntries)
        assertNotNull(store.get(id("p1")))
        assertNotNull(store.get(id("p2")))
    }

    @Test
    fun `unpinning makes a page evictable again`() {
        val store = BoundedPageByteStore(budgetBytes = 200)
        store.put(id("p1"), bytes(100))
        store.put(id("p2"), bytes(100))
        store.pin(id("p1"))
        // Forces p2 (unpinned, least recently used) out to make room.
        store.put(id("p3"), bytes(100))
        assertTrue(store.contains(id("p1")), "pinned p1 survived")

        store.unpin(id("p1"))
        // Only p1 is unpinned now, so the next insertion has to take it.
        store.put(id("p4"), bytes(100))
        assertFalse(store.contains(id("p1")), "an unpinned page should now be evictable")
        assertTrue(store.contains(id("p4")))
    }

    @Test
    fun `pins are reference counted`() {
        val store = BoundedPageByteStore(budgetBytes = 200)
        store.put(id("a"), bytes(100))
        store.pin(id("a"))
        store.pin(id("a"))
        store.unpin(id("a"))

        store.put(id("b"), bytes(100))
        assertNotNull(store.get(id("a")), "one pin remains outstanding")
    }

    @Test
    fun `an extra unpin cannot drive the count negative`() {
        // One real pin, then more unpins than pins: the count must land on zero, not wrap to a
        // large value and make the page permanently unevictable.
        val store = BoundedPageByteStore(budgetBytes = 100)
        store.put(id("a"), bytes(100))
        store.pin(id("a"))
        repeat(5) { store.unpin(id("a")) }

        store.put(id("b"), bytes(100))
        assertFalse(store.contains(id("a")), "the pin count must have reached zero, not wrapped")
        assertTrue(store.contains(id("b")))
    }

    @Test
    fun `contains does not make an entry look recently used`() {
        // A presence check that reordered the LRU would silently change what is evicted next.
        val store = BoundedPageByteStore(budgetBytes = 200)
        store.put(id("a"), bytes(100))
        store.put(id("b"), bytes(100))
        assertTrue(store.contains(id("a")))

        store.put(id("c"), bytes(100))
        assertTrue(store.contains(id("b")), "b is still the least recently used")
        assertFalse(store.contains(id("a")))
    }

    @Test
    fun `replacing bytes keeps the page pinned`() {
        // The UI may still be reading the previous value mid-composition.
        val store = BoundedPageByteStore(budgetBytes = 300)
        store.put(id("a"), bytes(100))
        store.pin(id("a"))
        store.put(id("a"), bytes(100, fill = 2))

        assertArrayEquals(bytes(100, fill = 2), store.get(id("a")))
        store.put(id("b"), bytes(100))
        store.put(id("c"), bytes(100))
        assertNotNull(store.get(id("a")), "the outstanding pin must survive a byte replacement")
    }

    @Test
    fun `replacing bytes does not leak the old accounting`() {
        val store = BoundedPageByteStore(budgetBytes = 1000)
        store.put(id("a"), bytes(100))
        store.put(id("a"), bytes(300))
        assertEquals(300, store.retainedBytes)
    }

    @Test
    fun `remove drops the entry and its bytes`() {
        val store = BoundedPageByteStore(budgetBytes = 1000)
        store.put(id("a"), bytes(100))
        assertNotNull(store.remove(id("a")))
        assertEquals(0, store.retainedBytes)
        assertNull(store.remove(id("a")))
    }

    @Test
    fun `clear drops everything including pinned entries`() {
        val store = BoundedPageByteStore(budgetBytes = 1000)
        store.put(id("a"), bytes(100))
        store.pin(id("a"))
        store.clear()
        assertEquals(0, store.retainedBytes)
        assertEquals(0, store.entryCount)
    }

    @Test
    fun `retained bytes stay exact across a mixed workload`() {
        // A drifting counter eventually reports a nearly empty store as full.
        val store = BoundedPageByteStore(budgetBytes = 500)
        repeat(30) { i ->
            store.put(id("page-$i"), bytes(120))
            if (i % 3 == 0) store.remove(id("page-${i - 1}"))
            if (i % 5 == 0) store.get(id("page-0"))
        }
        assertTrue(store.retainedBytes <= 500, "retained ${store.retainedBytes}")
        assertTrue(store.retainedBytes >= 0)
        // The counter must agree with the entries actually held.
        val byEntries = store.retainedEntryBytes
        assertEquals(byEntries, store.retainedBytes, "accounting drifted from the entries held")
    }

    @Test
    fun `empty bytes are refused`() {
        val store = BoundedPageByteStore(budgetBytes = 100)
        assertFalse(store.put(id("a"), ByteArray(0)))
    }

    @Test
    fun `pinning an absent page is a no-op`() {
        val store = BoundedPageByteStore(budgetBytes = 100)
        store.pin(id("nope"))
        store.unpin(id("nope"))
        assertEquals(0, store.entryCount)
    }

    @Test
    fun `a non positive budget is rejected`() {
        assertThrows<IllegalArgumentException> { BoundedPageByteStore(budgetBytes = 0) }
        assertThrows<IllegalArgumentException> { BoundedPageByteStore(budgetBytes = -1) }
    }

    @Test
    fun `different identities do not collide`() {
        val store = BoundedPageByteStore(budgetBytes = 1000)
        store.put(PageSourceId("src", "a", "r1"), bytes(10, fill = 1))
        store.put(PageSourceId("src", "a", "r2"), bytes(10, fill = 2))
        assertEquals(2, store.entryCount)
        assertEquals(20, store.retainedBytes)
    }
}
