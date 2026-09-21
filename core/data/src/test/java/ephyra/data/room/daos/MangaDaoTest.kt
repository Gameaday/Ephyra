package ephyra.data.room.daos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.data.room.EphyraDatabase
import ephyra.data.room.entities.MangaEntity
import ephyra.domain.manga.model.LockedField
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class MangaDaoTest {

    private lateinit var database: EphyraDatabase
    private lateinit var mangaDao: MangaDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, EphyraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        mangaDao = database.mangaDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createMangaEntity(
        id: Long = 0L,
        url: String = "/manga/one-piece",
        source: Long = 100L,
        title: String = "One Piece",
        favorite: Boolean = false,
        dateAdded: Long = 0L,
        lockedFields: Long = 0L,
        thumbnailUrl: String? = "https://example.com/cover.jpg",
    ): MangaEntity {
        return MangaEntity(
            id = id,
            source = source,
            url = url,
            artist = "Oda",
            author = "Oda",
            description = "Pirates",
            genre = listOf("Action", "Adventure"),
            title = title,
            status = 1L,
            thumbnailUrl = thumbnailUrl,
            favorite = favorite,
            lastUpdate = 0L,
            nextUpdate = 0L,
            initialized = true,
            viewerFlags = 0L,
            chapterFlags = 0L,
            coverLastModified = 0L,
            dateAdded = dateAdded,
            updateStrategy = 0,
            calculateInterval = 0,
            lastModifiedAt = 0L,
            favoriteModifiedAt = null,
            version = 1L,
            isSyncing = false,
            notes = "",
            metadataSource = null,
            metadataUrl = null,
            canonicalId = null,
            sourceStatus = 0,
            alternativeTitles = null,
            deadSince = null,
            contentType = 0,
            lockedFields = lockedFields,
        )
    }

    @Test
    fun insertNetworkManga_newManga_insertsSuccessfullyWithGeneratedId() = runBlocking {
        val networkManga = createMangaEntity(id = 0L, url = "/manga/naruto", title = "Naruto", favorite = false)

        val result = mangaDao.insertNetworkManga(listOf(networkManga))

        assertEquals(1, result.size)
        val inserted = result.first()
        assertTrue("Generated ID should be positive", inserted.id > 0L)
        assertEquals("Naruto", inserted.title)
        assertFalse(inserted.favorite)

        val fromDb = mangaDao.getMangaById(inserted.id)
        assertNotNull(fromDb)
        assertEquals(inserted.id, fromDb!!.id)
        assertFalse(fromDb.favorite)
    }

    @Test
    fun insertNetworkManga_existingFavoriteManga_preservesFavoriteAndDateAdded() = runBlocking {
        // 1. Initial browse & insert into DB
        val initial = createMangaEntity(id = 0L, url = "/manga/op", title = "One Piece", favorite = false)
        val inserted = mangaDao.insertNetworkManga(listOf(initial)).first()
        val originalId = inserted.id

        // 2. User adds to library (favorite = true, dateAdded set)
        val favorited = inserted.copy(favorite = true, dateAdded = 1700000000L)
        mangaDao.update(favorited)

        val inLibraryBefore = mangaDao.getMangaById(originalId)
        assertTrue(inLibraryBefore!!.favorite)
        assertEquals(1700000000L, inLibraryBefore.dateAdded)

        // 3. User browses source again -> network returns item with favorite = false and dateAdded = 0
        val incomingNetwork = createMangaEntity(
            id = 0L,
            url = "/manga/op",
            title = "One Piece (Updated)",
            favorite = false,
            dateAdded = 0L,
        )
        val reloaded = mangaDao.insertNetworkManga(listOf(incomingNetwork)).first()

        // Verify returned entity still has favorite = true and same id
        assertEquals(originalId, reloaded.id)
        assertTrue("Favorite must not be stripped by network browse", reloaded.favorite)
        assertEquals(1700000000L, reloaded.dateAdded)
        assertEquals("One Piece (Updated)", reloaded.title)

        // Verify DB row also still has favorite = true
        val inLibraryAfter = mangaDao.getMangaById(originalId)
        assertNotNull(inLibraryAfter)
        assertTrue("DB row favorite must remain true", inLibraryAfter!!.favorite)
        assertEquals(1700000000L, inLibraryAfter.dateAdded)
        assertEquals("One Piece (Updated)", inLibraryAfter.title)
    }

    @Test
    fun insertNetworkManga_respectsLockedFieldsWhenFavorite() = runBlocking {
        val initial = createMangaEntity(
            id = 0L,
            url = "/manga/bleach",
            title = "Custom Bleach Title",
            favorite = true,
            dateAdded = 1000L,
            lockedFields = LockedField.TITLE,
        )
        val originalId = mangaDao.insert(initial)

        val incomingNetwork = createMangaEntity(
            id = 0L,
            url = "/manga/bleach",
            title = "Network Bleach Title",
            favorite = false,
        )
        val result = mangaDao.insertNetworkManga(listOf(incomingNetwork)).first()

        assertEquals(originalId, result.id)
        assertEquals("Custom Bleach Title", result.title)
        assertTrue(result.favorite)
    }

    @Test
    fun upsert_existingFavoriteManga_preservesFavorite() = runBlocking {
        val initial =
            createMangaEntity(
                id = 0L,
                url = "/manga/hxh",
                title = "Hunter x Hunter",
                favorite = true,
                dateAdded = 2000L,
            )
        val originalId = mangaDao.insert(initial)

        val networkIncoming =
            createMangaEntity(id = 0L, url = "/manga/hxh", title = "Hunter x Hunter (2011)", favorite = false)
        val upsertedId = mangaDao.upsert(networkIncoming)

        assertEquals(originalId, upsertedId)
        val fromDb = mangaDao.getMangaById(originalId)
        assertNotNull(fromDb)
        assertTrue(fromDb!!.favorite)
        assertEquals(2000L, fromDb.dateAdded)
        assertEquals("Hunter x Hunter (2011)", fromDb.title)
    }

    @Test
    fun getLibraryManga_showsOnlyFavorites() = runBlocking {
        val manga1 = createMangaEntity(id = 0L, url = "/manga/1", favorite = true, dateAdded = 1000L)
        val manga2 = createMangaEntity(id = 0L, url = "/manga/2", favorite = false, dateAdded = 0L)
        mangaDao.insert(manga1)
        mangaDao.insert(manga2)

        val libraryItems = mangaDao.getLibraryManga()
        assertEquals(1, libraryItems.size)
        assertEquals("/manga/1", libraryItems.first().url)
    }
}
