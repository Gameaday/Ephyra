package ephyra.domain.download

/**
 * Recognises a downloaded page file by name.
 *
 * **Why this is domain code and not a private helper in the storage adapter.** The adapter that
 * observes a chapter artifact (`core:download`'s `UniFileDownloadArtifactProbe`) had this rule
 * inline and therefore **no test coverage at all** — `core:download` has no Robolectric and no
 * `isIncludeAndroidResources`, so a test of the adapter would have required new test infrastructure
 * before a single assertion could run. Extracting the rule puts the part that can actually be wrong
 * — the extension set, and the decision to ignore a leading-dot file — under a JVM test, and leaves
 * the adapter holding only I/O.
 *
 * The extension set is deliberately explicit rather than "is this a known image type somewhere in
 * the app": an artifact index that counted files the reader cannot open would report a healthy
 * chapter as valid, which is worse than reporting it invalid.
 */
object DownloadPageFiles {
    /** Extensions the reader can open, lower-case and without the dot. */
    val PAGE_EXTENSIONS: Set<String> = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "jxl")

    /**
     * True when [name] is a page file the reader will recognise.
     *
     * A leading dot is excluded: `.nomedia` and `.thumbnails` are storage metadata, not pages, and
     * counting them would inflate the page count against the source's own page list.
     */
    fun isPageFile(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        if (name.startsWith('.')) return false
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension.isNotEmpty() && extension in PAGE_EXTENSIONS
    }
}
