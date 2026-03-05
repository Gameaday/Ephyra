package eu.kanade.tachiyomi.data.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate

import tachiyomi.domain.manga.model.toMangaUpdate

class DeadSinceTrackingTest {

    // --- Manga model ---

    @Test
    fun `Manga create() has null deadSince`() {
        val manga = Manga.create()
        assertNull(manga.deadSince)
    }

    @Test
    fun `Manga copy preserves deadSince`() {
        val manga = Manga.create().copy(deadSince = 1000L)
        assertEquals(1000L, manga.deadSince)
    }

    @Test
    fun `Manga copy can clear deadSince to null`() {
        val manga = Manga.create().copy(deadSince = 1000L)
        val cleared = manga.copy(deadSince = null)
        assertNull(cleared.deadSince)
    }

    // --- MangaUpdate model ---

    @Test
    fun `MangaUpdate default deadSince is null`() {
        val update = MangaUpdate(id = 1L)
        assertNull(update.deadSince)
    }

    @Test
    fun `MangaUpdate can set deadSince timestamp`() {
        val now = System.currentTimeMillis()
        val update = MangaUpdate(id = 1L, deadSince = now)
        assertEquals(now, update.deadSince)
    }

    @Test
    fun `MangaUpdate can set DEAD_SINCE_CLEARED sentinel`() {
        val update = MangaUpdate(
            id = 1L,
            deadSince = LibraryUpdateJob.DEAD_SINCE_CLEARED,
        )
        assertEquals(0L, update.deadSince)
    }

    // --- toMangaUpdate conversion ---

    @Test
    fun `toMangaUpdate preserves deadSince`() {
        val manga = Manga.create().copy(id = 42L, deadSince = 5000L)
        val update = manga.toMangaUpdate()
        assertEquals(5000L, update.deadSince)
    }

    @Test
    fun `toMangaUpdate preserves null deadSince`() {
        val manga = Manga.create().copy(id = 42L, deadSince = null)
        val update = manga.toMangaUpdate()
        assertNull(update.deadSince)
    }

    // --- Migration threshold logic ---

    @Test
    fun `manga DEAD for 4 days exceeds threshold`() {
        val fourDaysAgo = System.currentTimeMillis() - (4L * 24 * 60 * 60 * 1000)
        val elapsed = System.currentTimeMillis() - fourDaysAgo
        assert(elapsed >= LibraryUpdateJob.DEAD_MIGRATION_THRESHOLD_MS)
    }

    @Test
    fun `manga DEAD for 1 day does not exceed threshold`() {
        val oneDayAgo = System.currentTimeMillis() - (1L * 24 * 60 * 60 * 1000)
        val elapsed = System.currentTimeMillis() - oneDayAgo
        assert(elapsed < LibraryUpdateJob.DEAD_MIGRATION_THRESHOLD_MS)
    }

    @Test
    fun `manga DEAD for exactly 3 days meets threshold`() {
        val threeDaysAgo = System.currentTimeMillis() - (3L * 24 * 60 * 60 * 1000)
        val elapsed = System.currentTimeMillis() - threeDaysAgo
        assert(elapsed >= LibraryUpdateJob.DEAD_MIGRATION_THRESHOLD_MS)
    }
}
