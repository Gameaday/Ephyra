package ephyra.domain.content.ingest

import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.model.ContentStatus
import ephyra.domain.content.model.ContentType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [CanonicalDeduplicator], the component that produces stable
 * content hashes and merges duplicate [ContentItem]s during ingestion.
 *
 * Deduplication keys on the `canonical_hash` metadata entry when present, else on
 * the hash of (title, author, genres). Items only merge when they carry the same
 * key — exactly how [IngestEngine] tags scanned items before dedup.
 */
class CanonicalDeduplicatorTest {

    private fun item(
        title: String = "Series",
        author: String? = null,
        genres: List<String> = emptyList(),
        url: String = "/$title",
        sourceId: Long = 1L,
        canonicalHash: String? = null,
    ) = ContentItem(
        id = -1L,
        sourceId = sourceId,
        url = url,
        title = title,
        author = author,
        artist = null,
        description = null,
        genres = genres,
        status = ContentStatus.Unknown,
        thumbnailUrl = null,
        contentType = ContentType.MANGA,
        metadata = if (canonicalHash != null) mapOf("canonical_hash" to canonicalHash) else emptyMap(),
    )

    // ── Hash stability ───────────────────────────────────────────────────

    @Test
    fun `same structural metadata produces identical hash`() {
        val a = CanonicalDeduplicator.generateContentHash("One Piece", "Oda", listOf("Action", "Adventure"))
        val b = CanonicalDeduplicator.generateContentHash("One Piece", "Oda", listOf("Action", "Adventure"))
        assertEquals(a, b)
    }

    @Test
    fun `hash is case and whitespace insensitive`() {
        val a = CanonicalDeduplicator.generateContentHash("One Piece", "Oda", listOf("Action"))
        val b = CanonicalDeduplicator.generateContentHash("one piece", " oda ", listOf(" action "))
        assertEquals(a, b)
    }

    @Test
    fun `hash ignores genre order`() {
        val a = CanonicalDeduplicator.generateContentHash("One Piece", "Oda", listOf("Action", "Drama"))
        val b = CanonicalDeduplicator.generateContentHash("One Piece", "Oda", listOf("Drama", "Action"))
        assertEquals(a, b)
    }

    @Test
    fun `different title produces different hash`() {
        val a = CanonicalDeduplicator.generateContentHash("One Piece", "Oda", listOf("Action"))
        val b = CanonicalDeduplicator.generateContentHash("Naruto", "Oda", listOf("Action"))
        assertNotEquals(a, b)
    }

    @Test
    fun `null author is handled deterministically`() {
        val a = CanonicalDeduplicator.generateContentHash("One Piece", null, listOf("Action"))
        val b = CanonicalDeduplicator.generateContentHash("One Piece", null, listOf("Action"))
        assertEquals(a, b)
    }

    @Test
    fun `hash is a 64 character hex string`() {
        val hash = CanonicalDeduplicator.generateContentHash("One Piece", null, emptyList())
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in "0123456789abcdef" })
    }

    // ── Deduplication without explicit hash (structural equality) ────────

    @Test
    fun `structurally identical items deduplicate to one`() {
        val result = CanonicalDeduplicator.deduplicate(
            listOf(
                item(title = "One Piece", author = "Oda", genres = listOf("Action")),
                item(title = "One Piece", author = "Oda", genres = listOf("Action")),
            ),
        )
        assertEquals(1, result.size)
    }

    @Test
    fun `items differing in author do not merge without a canonical hash`() {
        val result = CanonicalDeduplicator.deduplicate(
            listOf(
                item(title = "One Piece", author = "Oda"),
                item(title = "One Piece", author = null),
            ),
        )
        assertEquals(2, result.size)
    }

    @Test
    fun `distinct items are preserved`() {
        val result = CanonicalDeduplicator.deduplicate(
            listOf(
                item(title = "One Piece", author = "Oda"),
                item(title = "Naruto", author = "Kishimoto"),
            ),
        )
        assertEquals(2, result.size)
    }

    @Test
    fun `same title without author or genres but a shared hash merge`() {
        val result = CanonicalDeduplicator.deduplicate(
            listOf(
                item(title = "Solo", url = "/a", canonicalHash = "X"),
                item(title = "Solo", url = "/b", canonicalHash = "X"),
            ),
        )
        assertEquals(1, result.size)
        // The merge keeps the first item's identity (url) but unions metadata.
        assertEquals("/a", result.first().url)
    }

    // ── Deduplication with explicit canonical_hash (IngestEngine path) ───

    @Test
    fun `duplicates sharing a hash merge genres from both items`() {
        val result = CanonicalDeduplicator.deduplicate(
            listOf(
                item(title = "One Piece", genres = listOf("Action"), canonicalHash = "H1"),
                item(title = "One Piece", genres = listOf("Comedy"), canonicalHash = "H1"),
            ),
        )
        assertEquals(1, result.size)
        assertEquals(setOf("Action", "Comedy"), result.first().genres.toSet())
    }

    @Test
    fun `merge keeps first non-null author`() {
        val result = CanonicalDeduplicator.deduplicate(
            listOf(
                item(title = "One Piece", author = "Eiichiro Oda", canonicalHash = "H1"),
                item(title = "One Piece", author = null, canonicalHash = "H1"),
            ),
        )
        assertEquals("Eiichiro Oda", result.first().author)
    }

    @Test
    fun `merge fills missing author from second item`() {
        val result = CanonicalDeduplicator.deduplicate(
            listOf(
                item(title = "One Piece", author = null, canonicalHash = "H1"),
                item(title = "One Piece", author = "Eiichiro Oda", canonicalHash = "H1"),
            ),
        )
        assertEquals("Eiichiro Oda", result.first().author)
    }

    @Test
    fun `merged item records is_merged metadata flag`() {
        val result = CanonicalDeduplicator.deduplicate(
            listOf(
                item(title = "One Piece", canonicalHash = "H1"),
                item(title = "One Piece", canonicalHash = "H1"),
            ),
        )
        assertEquals("true", result.first().metadata["is_merged"])
    }
}