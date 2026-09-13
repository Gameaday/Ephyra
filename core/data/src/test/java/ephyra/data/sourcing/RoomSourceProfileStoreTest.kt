package ephyra.data.sourcing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.data.room.EphyraDatabase
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.source.Endpoint
import ephyra.domain.content.source.EndpointPattern
import ephyra.domain.content.source.SourceProfile
import ephyra.domain.content.source.SourceType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RoomSourceProfileStoreTest {

    private lateinit var database: EphyraDatabase
    private lateinit var store: RoomSourceProfileStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, EphyraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = RoomSourceProfileStore(dao = database.sourceProfileDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun saveAndRetrieveProfile() = runBlocking {
        val profile = SourceProfile(
            baseUrl = "https://example-manga.com",
            displayName = "Example Manga Source",
            contentType = ContentType.MANGA,
            sourceType = SourceType.HEURISTIC,
            endpoints = mapOf(
                Endpoint.SEARCH to EndpointPattern("/search?q={query}"),
                Endpoint.POPULAR to EndpointPattern("/popular"),
            ),
        )

        assertFalse(store.exists(profile.baseUrl))
        assertNull(store.get(profile.baseUrl))

        store.save(profile)

        assertTrue(store.exists(profile.baseUrl))
        val retrieved = store.get(profile.baseUrl)
        assertNotNull(retrieved)
        assertEquals("https://example-manga.com", retrieved?.baseUrl)
        assertEquals("Example Manga Source", retrieved?.displayName)
        assertEquals(ContentType.MANGA, retrieved?.contentType)
        assertEquals(2, retrieved?.endpoints?.size)
        assertEquals("/search?q={query}", retrieved?.endpoints?.get(Endpoint.SEARCH)?.pathTemplate)

        val all = store.getAll()
        assertEquals(1, all.size)
        assertEquals(profile.baseUrl, all[0].baseUrl)

        store.invalidate(profile.baseUrl)
        assertFalse(store.exists(profile.baseUrl))
        assertNull(store.get(profile.baseUrl))
    }
}
