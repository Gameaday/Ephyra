package ephyra.domain.reader.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ReaderChapterWindowPolicyTest {
    private val candidates = listOf(
        chapter("c1", number = 1.0, read = true, downloaded = true),
        chapter("c2", number = 2.0, read = true, downloaded = true),
        chapter("c3", number = 3.0, read = true, downloaded = false),
        chapter("c4", number = 4.0, read = false, downloaded = true),
        chapter("c5", number = 5.0, read = false, downloaded = true),
    )

    @Test
    fun `boundaries return only the current chapter`() {
        val first = ready(candidates, "c1")
        assertNull(first.previous)
        assertEquals("c2", first.next?.id)

        val last = ready(candidates, "c5")
        assertEquals("c4", last.previous?.id)
        assertNull(last.next)
    }

    @Test
    fun `skip read applies forward but never hides the previous chapter`() {
        val window = ready(
            candidates,
            currentId = "c2",
            policy = ReaderNavigationPolicy(skipReadForward = true),
        )

        assertEquals("c1", window.previous?.id)
        assertEquals("c4", window.next?.id)
    }

    @Test
    fun `hard filtered chapters are unavailable in both directions`() {
        val window = ready(
            candidates,
            currentId = "c3",
            policy = ReaderNavigationPolicy(isHardFiltered = { it.id == "c2" || it.id == "c4" }),
        )

        assertEquals("c1", window.previous?.id)
        assertEquals("c5", window.next?.id)
    }

    @Test
    fun `hard filtering never removes the explicitly opened current chapter`() {
        val window = ready(
            candidates,
            currentId = "c3",
            policy = ReaderNavigationPolicy(isHardFiltered = { it.id == "c3" }),
        )

        assertEquals("c3", window.current.id)
        assertEquals("c2", window.previous?.id)
    }

    @Test
    fun `skip filtered applies forward but never hides the previous chapter`() {
        val window = ready(
            candidates,
            currentId = "c2",
            policy = ReaderNavigationPolicy(
                skipFilteredForward = true,
                isFiltered = { it.id == "c3" || it.id == "c4" },
            ),
        )

        assertEquals("c1", window.previous?.id)
        assertEquals("c5", window.next?.id)
    }

    @Test
    fun `downloaded only applies in both directions and retains the opened current chapter`() {
        val window = ready(
            candidates,
            currentId = "c3",
            policy = ReaderNavigationPolicy(downloadedOnly = true),
        )

        assertEquals("c2", window.previous?.id)
        assertEquals("c4", window.next?.id)
    }

    @Test
    fun `downloaded only returns explicit current when there is no adjacent download`() {
        val isolated = listOf(
            chapter("remote-1", number = 1.0, downloaded = false),
            chapter("current", number = 2.0, downloaded = false),
            chapter("remote-2", number = 3.0, downloaded = false),
        )

        val window = ready(isolated, "current", ReaderNavigationPolicy(downloadedOnly = true))
        assertNull(window.previous)
        assertNull(window.next)
    }

    @Test
    fun `duplicate reduction keeps the current candidate for its chapter number`() {
        val duplicates = listOf(
            chapter("other", number = 1.0, scanlator = "Other"),
            chapter("current", number = 1.0, scanlator = "Current"),
            chapter("next", number = 2.0),
        )

        val window = ready(
            duplicates,
            currentId = "current",
            policy = ReaderNavigationPolicy(skipDuplicates = true, preferredScanlator = "Current"),
        )

        assertNull(window.previous)
        assertEquals("current", window.current.id)
        assertEquals("next", window.next?.id)
    }

    @Test
    fun `duplicate reduction otherwise prefers the configured scanlator`() {
        val duplicates = listOf(
            chapter("other", number = 1.0, scanlator = "Other"),
            chapter("preferred", number = 1.0, scanlator = "Preferred"),
            chapter("next", number = 2.0),
        )

        val window = ready(
            duplicates,
            currentId = "next",
            policy = ReaderNavigationPolicy(skipDuplicates = true, preferredScanlator = "Preferred"),
        )

        assertEquals("preferred", window.previous?.id)
    }

    @Test
    fun `unknown chapter numbers are never collapsed as duplicates`() {
        val specials = listOf(
            chapter("special-a", number = null),
            chapter("special-b", number = null),
        )

        val window = ready(
            specials,
            currentId = "special-a",
            policy = ReaderNavigationPolicy(skipDuplicates = true),
        )

        assertNull(window.previous)
        assertEquals("special-b", window.next?.id)
    }

    @Test
    fun `explicit order is honored with a stable identity tie breaker`() {
        val unordered = listOf(
            chapter("z", number = 2.0, sourceOrder = 2, title = "B", uploadTimestamp = 20),
            chapter("a", number = 2.0, sourceOrder = 1, title = "a", uploadTimestamp = 10),
        )

        val sourceOrder = ready(unordered, "z", ReaderNavigationPolicy(order = ReaderChapterOrder.SOURCE))
        assertEquals("a", sourceOrder.previous?.id)

        val titleOrder = ready(unordered, "z", ReaderNavigationPolicy(order = ReaderChapterOrder.TITLE))
        assertEquals("a", titleOrder.previous?.id)
    }

    @Test
    fun `missing blank and duplicate identities fail explicitly`() {
        assertEquals(
            ReaderChapterWindowResult.CurrentUnavailable,
            ReaderChapterWindowPolicy.createWindow(candidates, "missing", ReaderNavigationPolicy()),
        )
        assertEquals(
            ReaderChapterWindowResult.CurrentUnavailable,
            ReaderChapterWindowPolicy.createWindow(candidates, " ", ReaderNavigationPolicy()),
        )
        assertEquals(
            ReaderChapterWindowResult.DuplicateIdentity("same"),
            ReaderChapterWindowPolicy.createWindow(
                listOf(chapter("same", 1.0), chapter("same", 2.0)),
                currentId = "same",
                policy = ReaderNavigationPolicy(),
            ),
        )
    }

    @Test
    fun `invalid chapter numbers are always rejected`() {
        assertThrows<IllegalArgumentException> {
            ready(
                listOf(chapter("invalid", number = -1.0), chapter("valid", number = 1.0)),
                currentId = "valid",
            )
        }
    }

    private fun ready(
        values: List<ReaderChapterCandidate>,
        currentId: String,
        policy: ReaderNavigationPolicy = ReaderNavigationPolicy(),
    ): ReaderChapterWindow {
        val result = ReaderChapterWindowPolicy.createWindow(values, currentId, policy)
        return assertInstanceOf(ReaderChapterWindowResult.Ready::class.java, result).window
    }

    private fun chapter(
        id: String,
        number: Double? = null,
        read: Boolean = false,
        downloaded: Boolean = true,
        sourceOrder: Long = number?.toLong() ?: 0L,
        title: String = id,
        uploadTimestamp: Long = 0L,
        scanlator: String? = null,
    ) = ReaderChapterCandidate(
        id = id,
        number = number,
        title = title,
        read = read,
        downloaded = downloaded,
        sourceOrder = sourceOrder,
        uploadTimestamp = uploadTimestamp,
        scanlator = scanlator,
    )
}
