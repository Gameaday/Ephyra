package ephyra.feature.reader.model

/**
 * The page a chapter should be opened at, or null when its start position is already correct.
 *
 * [savedRestoreResolved] states whether the process-restore gate has now been decided. It is
 * `true` for the first chapter that resolves regardless of outcome, so a later chapter can never be
 * mistaken for the restored one.
 */
data class ChapterStartRequest(
    val requestedPage: Int?,
    val savedRestoreResolved: Boolean,
)

/**
 * Decides how a chapter's start position is derived.
 *
 * There are three competing sources of truth and they are not interchangeable:
 *  - an **explicit direction** from adjacent-chapter navigation (`startingAtBeginning` /
 *    `startFromEnd`), which already set `requestedPage`;
 *  - a **process-restore** page index, which belongs only to the chapter that was saved; and
 *  - **saved progress** in the chapter itself (`lastPageRead`) for a partially read chapter.
 *
 * The defect this replaces treated the first chapter to become current as the restore target. The
 * live progress counter (`chapterPageIndex`) is written on every page selection and saved to the
 * same key, so after reading a page of chapter *N* it holds a page index belonging to chapter *N*.
 * The first transition to chapter *N+1* therefore wrote chapter *N*'s index into *N+1*'s start
 * position. Where the viewer used that index it opened partway in — reported as "it keeps starting
 * on page 2" and, when the index was near the end, as skipping straight through the next chapter.
 *
 * Matching [chapterId] against [savedChapterId] is what makes the restore apply to the saved
 * chapter and to nothing else.
 */
fun resolveChapterStartRequest(
    chapterId: Long,
    chapterRead: Boolean,
    lastPageRead: Long,
    startingAtBeginning: Boolean,
    startFromEnd: Boolean,
    savedChapterId: Long,
    savedPageIndex: Int,
    savedRestoreApplied: Boolean,
): ChapterStartRequest {
    val explicitDirection = startingAtBeginning || startFromEnd
    val isRestoreTarget = !savedRestoreApplied &&
        savedPageIndex >= 0 &&
        chapterId == savedChapterId &&
        !explicitDirection

    return when {
        // An explicit direction is already in requestedPage; never override it.
        explicitDirection -> ChapterStartRequest(requestedPage = null, savedRestoreResolved = true)

        isRestoreTarget -> ChapterStartRequest(
            requestedPage = savedPageIndex,
            savedRestoreResolved = true,
        )

        !chapterRead && lastPageRead > 0 -> ChapterStartRequest(
            requestedPage = lastPageRead.toInt(),
            savedRestoreResolved = true,
        )

        else -> ChapterStartRequest(requestedPage = null, savedRestoreResolved = true)
    }
}
