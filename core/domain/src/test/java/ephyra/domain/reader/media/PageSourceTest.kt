package ephyra.domain.reader.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PageSourceTest {

    @Test
    fun `only an in-memory source is already resident`() {
        assertTrue(PageSource.InMemory(2048).isResident)
        assertFalse(PageSource.Remote("https://example.test/1.jpg").isResident)
        assertFalse(PageSource.LocalFile("/tmp/1.jpg").isResident)
        assertFalse(PageSource.ArchiveEntry("/tmp/1.cbz", "1.jpg").isResident)
    }

    @Test
    fun `only a remote source can change without the page identity changing`() {
        assertTrue(PageSource.Remote("https://example.test/1.jpg").isVolatile)
        assertFalse(PageSource.LocalFile("/tmp/1.jpg").isVolatile)
        assertFalse(PageSource.InMemory(10).isVolatile)
    }

    @Test
    fun `validators make a remote page revalidatable`() {
        assertFalse(PageSource.Remote("u").isRevalidatable)
        assertTrue(PageSource.Remote("u", etag = "W/abc").isRevalidatable)
        assertTrue(PageSource.Remote("u", lastModified = "Mon, 01 Jan 2024 00:00:00 GMT").isRevalidatable)
    }

    @Test
    fun `blank identity fields are rejected`() {
        assertThrows<IllegalArgumentException> { PageSource.Remote("") }
        assertThrows<IllegalArgumentException> { PageSource.LocalFile("  ".trim()) }
        assertThrows<IllegalArgumentException> { PageSource.ArchiveEntry("/a.cbz", "") }
        assertThrows<IllegalArgumentException> { PageSource.InMemory(0) }
    }

    @Test
    fun `different remote pages are not equal`() {
        assertNotEquals(
            PageSource.Remote("https://example.test/1.jpg"),
            PageSource.Remote("https://example.test/2.jpg"),
        )
    }

    @Test
    fun `headers and validators are part of remote identity`() {
        // A page behind an auth header is a different request than the same URL without it.
        assertNotEquals(
            PageSource.Remote("u", headers = mapOf("Referer" to "https://a.test")),
            PageSource.Remote("u"),
        )
        assertNotEquals(PageSource.Remote("u", etag = "a"), PageSource.Remote("u", etag = "b"))
    }

    @Test
    fun `only transport failures are worth a blind retry`() {
        assertTrue(PageLoadFailure.Transport("timeout").isRetryable)
        assertFalse(PageLoadFailure.Undecodable("not an image").isRetryable)
        assertFalse(PageLoadFailure.Gone("404").isRetryable)
    }
}
