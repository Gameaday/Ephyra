package ephyra.feature.reader.viewer.webtoon

/**
 * Visibility-watchdog tuning for webtoon page items.
 *
 * Sections skipped during a fast fling can sit composed-but-blank (stale Queue, never
 * queued): when a visible item is still not Ready after [WATCHDOG_GRACE_MS], the item
 * re-queues its own load so parking on a blank middle section resolves it.
 */
object WebtoonVisibility {
    /** Grace period before a visible-but-not-Ready item re-queues its load. */
    const val WATCHDOG_GRACE_MS = 800L
}
