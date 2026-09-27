package ephyra.domain.reader.media

/**
 * Decides which pages are pinned, so eviction order reflects **distance from the viewport** rather
 * than pure recency.
 *
 * The store already protects pinned entries and already evicts least-recently-used. What it cannot
 * know is which pages the user is currently looking at. Left to recency alone, a fast fling through
 * a long chapter evicts the pages the reader is *about to* reach: the pages just scrolled past are
 * the most recently used, and the ones under the viewport are mid-history. The visible set is
 * therefore the one set that must be pinned, and this is the policy that identifies it.
 *
 * Reference counting is deliberately *not* the interface. A `pin`/`unpin` pair per composable is
 * exactly the shape that leaks when a composition is cancelled without its `DisposableEffect`
 * running — and a leaked pin is permanent, which is the same unbounded growth this whole mechanism
 * exists to remove. Instead the caller states the visible set declaratively and this computes the
 * delta, so a page cannot be pinned without also being unpin-able.
 *
 * Pure and Android-free: the window is arithmetic over a page range, so the bounds are testable on
 * the JVM rather than only on a device.
 */
class PageViewportPinPolicy(
    private val radius: Int = DEFAULT_RADIUS,
) {
    init {
        require(radius >= 0) { "radius must not be negative" }
    }

    /**
     * The pages that must be retained for a viewport showing [currentIndex] of [pageCount] pages:
     * [currentIndex] plus [radius] either side.
     *
     * Clamped to the chapter, so a viewport at the first page does not pin a negative index and one
     * at the last does not pin past the end. An empty chapter yields an empty window rather than
     * throwing, because "no pages" is a legitimate state during load and must not crash the reader.
     */
    fun visibleRange(currentIndex: Int, pageCount: Int): IntRange {
        if (pageCount <= 0) return IntRange.EMPTY
        val centre = currentIndex.coerceIn(0, pageCount - 1)
        val first = (centre - radius).coerceAtLeast(0)
        val last = (centre + radius).coerceAtMost(pageCount - 1)
        return first..last
    }

    /**
     * Applies the window for [currentIndex] to [store], returning the ids newly pinned and released.
     *
     * The returned pairs exist for diagnostics and tests; production callers can ignore them. The
     * whole update is a single pass over the previous and next windows, so a viewport that has not
     * moved performs no store mutations at all — important on a fling, where the index can settle
     * and then stop changing.
     */
    fun update(store: PageByteStore, currentIndex: Int, pageCount: Int, idFor: (Int) -> PageSourceId): PinUpdate {
        val next = visibleRange(currentIndex, pageCount)
        val pinned = pinnedIndices

        // `IntRange - IntRange` yields a List, and `intersect` a Set, so both sides are converted
        // explicitly rather than relying on an implicit coercion: the sets are stored and compared
        // as sets, and a silently-typed List would make `pinned - next` and `next - pinned` behave
        // differently from the Set-typed state they are compared against.
        val leaving = pinned - next.toSet()
        val entering = next.toSet() - pinned

        leaving.forEach { store.unpin(idFor(it)) }
        entering.forEach { store.pin(idFor(it)) }

        // Replace rather than mutate: `next` is a view onto a fresh IntRange, and retaining it would
        // alias state a later caller could still be iterating.
        pinnedIndices = if (next.isEmpty()) emptySet() else next.toSet()

        return PinUpdate(entered = entering, released = leaving)
    }

    /** Drops every pin this policy applied, e.g. on chapter change. */
    fun releaseAll(store: PageByteStore, idFor: (Int) -> PageSourceId) {
        pinnedIndices.forEach { store.unpin(idFor(it)) }
        pinnedIndices = emptySet()
    }

    /** The indices currently pinned. Exposed so tests can assert the policy rather than the store. */
    var pinnedIndices: Set<Int> = emptySet()
        private set

    /** What a single [update] changed. */
    data class PinUpdate(
        val entered: Set<Int>,
        val released: Set<Int>,
    ) {
        val changed: Boolean get() = entered.isNotEmpty() || released.isNotEmpty()
    }

    companion object {
        /**
         * Pages retained either side of the viewport.
         *
         * Two is the prefetch depth the reader already uses for the paged path (current, previous,
         * next), widened by one for the continuous path where a fling can overshoot further before
         * the next item composes. Beyond that the working set stops tracking what the user is
         * reading and starts being a second cache.
         */
        const val DEFAULT_RADIUS: Int = 2
    }
}
