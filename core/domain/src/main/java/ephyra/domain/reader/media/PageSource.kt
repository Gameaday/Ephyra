package ephyra.domain.reader.media

/**
 * How a page's bytes are obtained, independent of what they contain.
 *
 * The reader needs this split because the four kinds fail differently and are cached differently.
 * A [Remote] page can expire or 404 and must be re-fetched; a [File] page cannot; an [Archive]
 * page needs its entry stream opened per read; a [Memory] page is already resident and must never
 * be closed by a caller. Collapsing them into one "load bytes" function is what forces callers to
 * guess, and guessing wrong shows up as a blank page rather than an error.
 *
 * This type carries no byte data. It is the plan for obtaining bytes, so that a page can be
 * described, cached, and reasoned about before anything is read.
 */
sealed interface PageSource {
    /** Network-backed. [url] may expire or become unavailable. */
    data class Remote(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
        val etag: String? = null,
        val lastModified: String? = null,
    ) : PageSource {
        init {
            require(url.isNotBlank()) { "remote page url must not be blank" }
        }

        /**
         * True when cached validators could be revalidated cheaply.
         *
         * A conditional request can return 304 with no body, so a valid [etag] or [lastModified]
         * is what lets an unchanged page skip both transfer and decode.
         */
        val isRevalidatable: Boolean get() = etag != null || lastModified != null
    }

    /** A local file, e.g. a downloaded page. Bytes are stable for the file's lifetime. */
    data class LocalFile(val path: String) : PageSource {
        init {
            require(path.isNotBlank()) { "local page path must not be blank" }
        }
    }

    /** An entry inside a container (zip/cbz). Requires the container to stay open while read. */
    data class ArchiveEntry(
        val archivePath: String,
        val entryName: String,
    ) : PageSource {
        init {
            require(archivePath.isNotBlank()) { "archive path must not be blank" }
            require(entryName.isNotBlank()) { "archive entry name must not be blank" }
        }
    }

    /** Already-resident bytes. Not repeatable, and must never be closed by a reader. */
    data class InMemory(val byteCount: Int) : PageSource {
        init {
            require(byteCount > 0) { "in-memory page must have a positive size" }
        }
    }

    /** True when the bytes are already in process memory and no I/O is required. */
    val isResident: Boolean get() = this is InMemory

    /** True when the source can change without the page identity changing. */
    val isVolatile: Boolean get() = this is Remote
}

/** Fails in a way a viewport can render, instead of as a missing image. */
sealed interface PageLoadFailure {
    /** Transport or IO error; a retry may succeed. */
    data class Transport(val cause: String) : PageLoadFailure

    /** The source answered, but with no usable image. Retrying will not help until the source changes. */
    data class Undecodable(val reason: String) : PageLoadFailure

    /** The page no longer exists upstream. Retrying is pointless. */
    data class Gone(val reason: String) : PageLoadFailure

    /** True when a plain retry is worth attempting. */
    val isRetryable: Boolean
        get() = this is Transport
}
