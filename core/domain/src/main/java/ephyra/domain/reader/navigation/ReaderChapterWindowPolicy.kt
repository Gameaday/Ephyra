package ephyra.domain.reader.navigation

/** Source-neutral chapter data required to build a reader navigation window. */
data class ReaderChapterCandidate(
    val id: String,
    val number: Double?,
    val title: String,
    val read: Boolean,
    val downloaded: Boolean,
    val sourceOrder: Long,
    val uploadTimestamp: Long,
    val scanlator: String? = null,
)

enum class ReaderChapterOrder {
    SOURCE,
    NUMBER,
    UPLOAD_DATE,
    TITLE,
}

data class ReaderNavigationPolicy(
    val order: ReaderChapterOrder = ReaderChapterOrder.NUMBER,
    val skipReadForward: Boolean = false,
    val skipFilteredForward: Boolean = false,
    val skipDuplicates: Boolean = false,
    val downloadedOnly: Boolean = false,
    val preferredScanlator: String? = null,
    val isHardFiltered: (ReaderChapterCandidate) -> Boolean = { false },
    val isFiltered: (ReaderChapterCandidate) -> Boolean = { false },
)

data class ReaderChapterWindow(
    val previous: ReaderChapterCandidate?,
    val current: ReaderChapterCandidate,
    val next: ReaderChapterCandidate?,
)

sealed interface ReaderChapterWindowResult {
    data class Ready(val window: ReaderChapterWindow) : ReaderChapterWindowResult

    /** The requested chapter is absent after hard filtering and duplicate selection. */
    data object CurrentUnavailable : ReaderChapterWindowResult

    /** More than one candidate resolved to the same stable identity. */
    data class DuplicateIdentity(val identity: String) : ReaderChapterWindowResult
}

/**
 * Builds the bounded chapter window used by the replacement reader.
 *
 * Read/filter skipping is directional. The immediate previous eligible chapter is retained so the
 * user can deliberately revisit read or series-filtered material. Download-only mode applies to
 * both directions, while the explicitly opened current chapter is always kept.
 */
object ReaderChapterWindowPolicy {
    fun createWindow(
        candidates: List<ReaderChapterCandidate>,
        currentId: String,
        policy: ReaderNavigationPolicy,
    ): ReaderChapterWindowResult {
        if (currentId.isBlank()) return ReaderChapterWindowResult.CurrentUnavailable
        candidates.forEach { validateChapterNumber(it) }
        val duplicateIdentity = candidates
            .groupingBy { it.id }
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
            ?.key
        if (duplicateIdentity != null) return ReaderChapterWindowResult.DuplicateIdentity(duplicateIdentity)

        val hardFiltered = candidates.filter { candidate ->
            candidate.id == currentId ||
                (isDownloadEligible(candidate, policy) && !policy.isHardFiltered(candidate))
        }
        val ordered = hardFiltered.sortedWith(orderComparator(policy.order))
        val navigationUniverse = if (policy.skipDuplicates) {
            collapseDuplicates(ordered, currentId, policy.preferredScanlator)
        } else {
            ordered
        }
        val currentIndex = navigationUniverse.indexOfFirst { it.id == currentId }
        if (currentIndex < 0) return ReaderChapterWindowResult.CurrentUnavailable

        val current = navigationUniverse[currentIndex]
        val previous = navigationUniverse.getOrNull(currentIndex - 1)
        val next = navigationUniverse.asSequence()
            .drop(currentIndex + 1)
            .firstOrNull { isForwardEligible(it, policy) }

        return ReaderChapterWindowResult.Ready(
            ReaderChapterWindow(
                previous = previous,
                current = current,
                next = next,
            ),
        )
    }

    private fun isDownloadEligible(
        candidate: ReaderChapterCandidate,
        policy: ReaderNavigationPolicy,
    ): Boolean = !policy.downloadedOnly || candidate.downloaded

    private fun collapseDuplicates(
        ordered: List<ReaderChapterCandidate>,
        currentId: String,
        preferredScanlator: String?,
    ): List<ReaderChapterCandidate> {
        val selected = LinkedHashMap<String, ReaderChapterCandidate>()
        ordered.forEach { candidate ->
            val key = duplicateKey(candidate) ?: return@forEach
            val previous = selected[key]
            selected[key] = when {
                previous == null -> candidate
                previous.id == currentId -> previous
                candidate.id == currentId -> candidate
                preferredScanlator != null && previous.scanlator != preferredScanlator &&
                    candidate.scanlator == preferredScanlator -> candidate
                else -> previous
            }
        }
        return ordered.filter { candidate ->
            val key = duplicateKey(candidate) ?: return@filter true
            selected[key]?.id == candidate.id
        }
    }

    private fun validateChapterNumber(candidate: ReaderChapterCandidate) {
        val number = candidate.number ?: return
        require(number.isFinite() && number >= 0.0) {
            "Chapter number must be finite and non-negative when present"
        }
    }

    private fun duplicateKey(candidate: ReaderChapterCandidate): String? {
        val number = candidate.number ?: return null
        return "number:${number.toBits()}"
    }

    private fun isForwardEligible(
        candidate: ReaderChapterCandidate,
        policy: ReaderNavigationPolicy,
    ): Boolean {
        if (!isDownloadEligible(candidate, policy)) return false
        if (policy.skipReadForward && candidate.read) return false
        if (policy.skipFilteredForward && policy.isFiltered(candidate)) return false
        return true
    }

    private fun orderComparator(order: ReaderChapterOrder): Comparator<ReaderChapterCandidate> {
        val primary: Comparator<ReaderChapterCandidate> = when (order) {
            ReaderChapterOrder.SOURCE -> compareBy { it.sourceOrder }
            ReaderChapterOrder.NUMBER -> compareBy { it.number ?: Double.POSITIVE_INFINITY }
            ReaderChapterOrder.UPLOAD_DATE -> compareBy { it.uploadTimestamp }
            ReaderChapterOrder.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        }
        return primary.thenBy { it.id }
    }
}
