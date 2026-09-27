package ephyra.domain.reader.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PageByteBudgetTest {

    @Test
    fun `a small heap clamps down because the floor would swamp it`() {
        // 64 MB heap -> 8 MB by fraction. The 48 MiB floor cannot apply: it is three quarters of
        // the entire heap, so the store would evict the renderer instead of sitting beside it.
        val budget = PageByteBudget.forMemoryClassMb(64)
        assertEquals(16 * 1024 * 1024, budget, "floor must yield to the heap-share ceiling")
        assertTrue(budget < PageByteBudget.MIN_BYTES, "the floor is unreachable on a 64 MB heap")
    }

    @Test
    fun `a large heap clamps down`() {
        // 4096 MB heap -> 512 MB by fraction, which is itself a memory-pressure risk.
        assertEquals(PageByteBudget.MAX_BYTES, PageByteBudget.forMemoryClassMb(4096))
    }

    @Test
    fun `a mid-range heap tracks the fraction`() {
        // 512 MB * 0.125 = 64 MB: inside the clamps, so the fraction governs.
        assertEquals(64 * 1024 * 1024, PageByteBudget.forMemoryClassMb(512))
    }

    @Test
    fun `the budget grows with the device`() {
        val small = PageByteBudget.forMemoryClassMb(256)
        val large = PageByteBudget.forMemoryClassMb(1024)
        assertTrue(large > small, "a larger heap must not get a smaller budget")
    }

    @Test
    fun `a nonsensical memory class cannot disable caching`() {
        // The failure this prevents: a zero budget makes every put() return false, so nothing is
        // ever cached and the store silently stops existing.
        assertTrue(PageByteBudget.forMemoryClassMb(0) > 0)
        assertTrue(PageByteBudget.forMemoryClassMb(-8) > 0)
    }

    @Test
    fun `the budget is always positive and bounded`() {
        for (mb in intArrayOf(1, 16, 64, 128, 256, 512, 1024, 2048, 4096, 16384)) {
            val budget = PageByteBudget.forMemoryClassMb(mb)
            assertTrue(budget > 0, "mb=$mb produced a non-positive budget of $budget")
            assertTrue(budget <= PageByteBudget.MAX_BYTES, "mb=$mb budget=$budget above ceiling")
        }
    }

    @Test
    fun `the store never claims a quarter of the heap or more`() {
        // The invariant that actually matters, and the one a naive floor breaks. A store holding
        // half the heap is not a cache sitting beside the renderer; it is the renderer being
        // evicted. Asserted as a strict share so widening the fraction later has to be deliberate.
        for (mb in intArrayOf(16, 64, 128, 256, 512, 1024, 2048, 4096, 16384)) {
            val heap = mb * 1024L * 1024L
            assertTrue(
                PageByteBudget.forMemoryClassMb(mb).toLong() <= heap / 4,
                "mb=$mb claims more than a quarter of the heap",
            )
        }
    }

    @Test
    fun `a mid-range heap never falls below the floor`() {
        // The floor still applies where there is room for it: above ~384 MB the share ceiling stops
        // binding, and 48 MiB becomes achievable again.
        for (mb in intArrayOf(384, 512, 768, 1024)) {
            assertTrue(
                PageByteBudget.forMemoryClassMb(mb) >= PageByteBudget.MIN_BYTES,
                "mb=$mb should reach the floor",
            )
        }
    }

    @Test
    fun `a derived budget is usable as a store budget`() {
        // The policy's only consumer is a BoundedPageByteStore, and that rejects a non-positive
        // budget in its init. This is the integration seam, not a restatement of the numbers.
        val store = BoundedPageByteStore(PageByteBudget.forMemoryClassMb(512))
        assertEquals(64 * 1024 * 1024, store.budgetBytes)
    }
}
