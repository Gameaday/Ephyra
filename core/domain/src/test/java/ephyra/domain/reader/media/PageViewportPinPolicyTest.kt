package ephyra.domain.reader.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PageViewportPinPolicyTest {

    private fun id(index: Int) = PageSourceId("src", "page-$index", "r1")

    private fun storeOf(budget: Int = 300) = BoundedPageByteStore(budget)

    @Test
    fun `the window covers the viewport and its neighbours`() {
        val policy = PageViewportPinPolicy(radius = 2)
        assertEquals(0..2, policy.visibleRange(currentIndex = 0, pageCount = 10))
        assertEquals(3..7, policy.visibleRange(currentIndex = 5, pageCount = 10))
    }

    @Test
    fun `the window clamps at both ends of the chapter`() {
        val policy = PageViewportPinPolicy(radius = 2)
        assertEquals(0..2, policy.visibleRange(currentIndex = 0, pageCount = 10))
        assertEquals(7..9, policy.visibleRange(currentIndex = 9, pageCount = 10))
    }

    @Test
    fun `an out-of-range index is clamped rather than throwing`() {
        // A fling can report an index one past the end between a removal and the next layout.
        val policy = PageViewportPinPolicy(radius = 1)
        assertEquals(0..1, policy.visibleRange(currentIndex = -5, pageCount = 5))
        assertEquals(3..4, policy.visibleRange(currentIndex = 99, pageCount = 5))
    }

    @Test
    fun `an empty chapter yields an empty window`() {
        // "No pages" is legitimate during load; throwing here would crash the reader on a normal state.
        val policy = PageViewportPinPolicy()
        assertTrue(policy.visibleRange(currentIndex = 0, pageCount = 0).isEmpty())
    }

    @Test
    fun `the visible page is pinned and survives eviction pressure`() {
        // The budget must hold all three pages first. Pinning after the fact cannot resurrect an
        // already-evicted page, so a budget too small to admit the setup would fail for a reason
        // that has nothing to do with pinning.
        val store = storeOf(budget = 300)
        val policy = PageViewportPinPolicy(radius = 0)
        store.put(id(0), ByteArray(100))
        store.put(id(1), ByteArray(100))
        store.put(id(2), ByteArray(100))
        // `contains` rather than `get` for the setup check, and this is not incidental. `get`
        // re-inserts the key to mark it most-recently-used, which would make the page under test the
        // *last* entry evicted and let the assertion pass with pinning disabled entirely — the test
        // would then be asserting LRU ordering, not the pin. `contains` checks presence without
        // touching recency, which is exactly what it exists for.
        assertTrue(store.contains(id(0)), "setup: all three pages must fit before pinning")

        policy.update(store, currentIndex = 0, pageCount = 3) { id(it) }

        // 200 bytes of new data against a 300-byte budget: only the pinned page can absorb this.
        store.put(id(3), ByteArray(200))
        assertNotNull(store.get(id(0)), "the page under the viewport must not be evicted")
        assertTrue(store.retainedBytes <= 300)
    }

    @Test
    fun `scrolling releases the page that left the viewport`() {
        val store = storeOf(budget = 1000)
        val policy = PageViewportPinPolicy(radius = 0)
        repeat(3) { store.put(id(it), ByteArray(100)) }

        policy.update(store, currentIndex = 0, pageCount = 3) { id(it) }
        val update = policy.update(store, currentIndex = 1, pageCount = 3) { id(it) }

        assertEquals(setOf(1), update.entered)
        assertEquals(setOf(0), update.released)

        // Once released, page 0 is evictable like any other.
        repeat(2) { store.put(id(10 + it), ByteArray(400)) }
        assertNull(store.get(id(0)), "a page outside the viewport must be evictable")
    }

    @Test
    fun `an unmoved viewport performs no store mutations`() {
        // On a fling the index settles and stops changing; re-pinning every frame would churn the
        // store's LRU ordering for no reason and could evict a page the user is reading.
        val store = storeOf(budget = 1000)
        val policy = PageViewportPinPolicy(radius = 2)
        repeat(5) { store.put(id(it), ByteArray(100)) }

        policy.update(store, currentIndex = 2, pageCount = 5) { id(it) }
        val second = policy.update(store, currentIndex = 2, pageCount = 5) { id(it) }

        assertTrue(!second.changed, "an unchanged viewport must not re-pin")
    }

    @Test
    fun `releasing all drops every pin`() {
        val store = storeOf(budget = 200)
        val policy = PageViewportPinPolicy(radius = 1)
        repeat(3) { store.put(id(it), ByteArray(100)) }
        policy.update(store, currentIndex = 1, pageCount = 3) { id(it) }
        assertTrue(policy.pinnedIndices.isNotEmpty())

        policy.releaseAll(store) { id(it) }
        assertTrue(policy.pinnedIndices.isEmpty(), "a chapter change must not leave pins behind")

        // Now that nothing is pinned, the store can be filled without protecting anything.
        store.put(id(9), ByteArray(200))
        assertNull(store.get(id(0)))
    }

    @Test
    fun `the default radius keeps a bounded working set`() {
        // The point of the policy: retention tracks the viewport, not the chapter length. A
        // 1000-page chapter must not pin more than the window.
        val policy = PageViewportPinPolicy()
        val window = policy.visibleRange(currentIndex = 500, pageCount = 1000)
        assertEquals(PageViewportPinPolicy.DEFAULT_RADIUS * 2 + 1, window.count())
        assertTrue(window.count() < 10, "the pinned window must stay small on a long chapter")
    }

    @Test
    fun `a negative radius is rejected`() {
        assertThrows<IllegalArgumentException> { PageViewportPinPolicy(radius = -1) }
    }
}
