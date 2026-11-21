package ephyra.feature.library

import androidx.compose.runtime.Immutable
import ephyra.feature.library.LibraryViewModel.LibraryData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The last library snapshot a live ViewModel produced, kept so a *recreated* one can paint it
 * immediately instead of showing a spinner.
 *
 * **Why this is needed.** The bottom nav clears the back stack on every tab switch, so the library
 * ViewModel is destroyed and rebuilt each time the user returns to the tab. A fresh ViewModel starts
 * at `isLoading = true` and clears it only when the five‑way combine emits, so returning to the tab
 * always shows the spinner — even though the user was just looking at this exact list, and even when
 * there is no memory pressure at all. That is the reported "spinny wheel on swipe back".
 *
 * Re‑querying is still correct: the cache is stale by definition. What changes is that staleness is
 * never *blank*. The snapshot paints, then the real query replaces it. This is the ordinary
 * stale‑while‑revalidate trade, and it is safe here because the library is a read‑only view of data
 * the user cannot be mid‑edit on from this tab.
 *
 * Process‑scoped deliberately: the point is to survive the ViewModel, and a ViewModel‑scoped cache
 * would die with the thing it is meant to outlive. It holds one snapshot, so it is bounded by the
 * size of one library list rather than growing per navigation.
 */
@Immutable
class LibrarySnapshotCache internal constructor(
    private val holder: MutableStateFlow<LibraryData?> = MutableStateFlow(null),
) {
    val snapshot: StateFlow<LibraryData?> = holder.asStateFlow()

    /** The last snapshot, or null on the first ever visit. */
    val current: LibraryData?
        get() = holder.value

    /** Records [data] as the newest snapshot. Called on every emission, not just the first. */
    fun store(data: LibraryData) {
        if (data.isInitialized) holder.value = data
    }

    /** Drops the snapshot. Exists for tests and for sign‑out, where the next user must not see it. */
    fun clear() {
        holder.value = null
    }

    companion object {
        private val shared = MutableStateFlow<LibraryData?>(null)

        /**
         * The process‑wide instance.
         *
         * Process‑scoped deliberately: the point is to survive the ViewModel, and a
         * ViewModel‑scoped cache would die with the thing it is meant to outlive. It holds one
         * snapshot, so it is bounded by the size of one library list rather than growing per
         * navigation.
         */
        fun get(): LibrarySnapshotCache = LibrarySnapshotCache(shared)

        /**
         * A cache that shares nothing.
         *
         * Tests must use this. [get] is process‑scoped, so two tests that both default to it
         * silently share state and become order‑dependent - the second one inherits the first
         * one's library and assertions about "nothing cached" fail for the wrong reason. That is
         * a defect in the *test*, not in the cache, but it is invisible until the suite runs in a
         * different order.
         */
        fun isolated(): LibrarySnapshotCache = LibrarySnapshotCache()
    }
}
