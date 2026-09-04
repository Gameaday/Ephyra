package ephyra.domain.content.model

import ephyra.domain.manga.model.Manga
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ContentMappers], the bidirectional mappers between the legacy
 * manga-specific ([Manga], [Chapter]) domain model and the media-agnostic
 * ([ContentItem], [ContentUnit]) model that the whole content pipeline uses.
 *
 * These tests lock the "store & update" contract: if a field is dropped or
 * remapped incorrectly when persisting or hydrating content, users lose
 * library metadata silently.
 */
class ContentMappersTest {

    private val manga = Manga.create().copy(
        id = 42L,
        source = 7L,
        url = "/manga/42",
        title = "One Piece",
        author = "Eiichiro Oda",
        artist = "Oda",
        description = "Sea adventure",
        genre = listOf("Action", "Adventure"),
        status = 1L, // Ongoing
        thumbnailUrl = "https://img/op.jpg",
        favorite = true,
        dateAdded = 1000L,
        lastUpdate = 2000L,
        initialized = true,
        contentType = ContentType.MANGA,
    )

    // ── Manga → ContentItem ──────────────────────────────────────────────

    @Test
    fun `toContentItem maps all core fields`() {
        val item = manga.toContentItem()

        assertEquals(42L, item.id)
        assertEquals(7L, item.sourceId)
        assertEquals("/manga/42", item.url)
        assertEquals("One Piece", item.title)
        assertEquals("Eiichiro Oda", item.author)
        assertEquals("Oda", item.artist)
        assertEquals("https://img/op.jpg", item.thumbnailUrl)
        assertEquals(ContentType.MANGA, item.contentType)
        assertEquals(true, item.favorite)
        assertEquals(1000L, item.dateAdded)
        assertEquals(2000L, item.lastUpdate)
        assertEquals(true, item.initialized)
    }

    @Test
    fun `toContentItem maps genre list`() {
        assertEquals(listOf("Action", "Adventure"), manga.toContentItem().genres)
    }

    @Test
    fun `toContentItem maps legacy status to ContentStatus`() {
        assertEquals(ContentStatus.Ongoing, manga.toContentItem().status)
    }

    @Test
    fun `toContentItem handles null genre`() {
        val noGenre = Manga.create().copy(genre = null)
        assertTrue(noGenre.toContentItem().genres.isEmpty())
    }
}