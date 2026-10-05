package ephyra.domain.content.source

import ephyra.domain.content.model.ContentType
import ephyra.testutil.FakePreferenceStore
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SourceProfileCache], the persistent store of discovered
 * [SourceProfile]s. Guards the "discover → cache → reuse" half of content retrieval:
 * a profile saved for a source URL must come back intact on the next request,
 * and failures must not resurrect stale/corrupt entries.
 */
class SourceProfileCacheTest {

    private val store = FakePreferenceStore()
    private val cache = SourceProfileCache(store, Json { ignoreUnknownKeys = true })

    @Test
    fun `get returns null for a never cached source`() = runTest {
        assertNull(cache.get("https://unknown.example"))
    }

    @Test
    fun `save then get round-trips a full profile`() = runTest {
        val profile = SourceProfile(
            baseUrl = "https://mangadex.org",
            contentType = ContentType.MANGA,
            sourceType = SourceType.REMOTE_EXTENSION,
            displayName = "MangaDex",
            enabled = true,
            verified = true,
            rateLimitMs = 500L,
            authType = AuthType.NONE,
        )

        cache.save(profile)

        val loaded = cache.get("https://mangadex.org")
        assertNotNull(loaded)
        val result = loaded!!
        assertEquals("https://mangadex.org", result.baseUrl)
        assertEquals(ContentType.MANGA, result.contentType)
        assertEquals(SourceType.REMOTE_EXTENSION, result.sourceType)
        assertEquals("MangaDex", result.displayName)
        assertEquals(true, result.enabled)
        assertEquals(true, result.verified)
        assertEquals(500L, result.rateLimitMs)
        assertEquals(AuthType.NONE, result.authType)
    }

    @Test
    fun `isSet reflects whether the source is cached`() = runTest {
        cache.get("https://mangadex.org").shouldBeNull()
        cache.exists("https://mangadex.org") shouldBe false

        cache.save(SourceProfile(baseUrl = "https://mangadex.org", contentType = ContentType.MANGA))

        cache.exists("https://mangadex.org") shouldBe true
        cache.exists("https://manganato.com") shouldBe false
    }

    @Test
    fun `invalidate removes the cached profile`() = runTest {
        cache.save(SourceProfile(baseUrl = "https://mangadex.org", contentType = ContentType.MANGA))
        assertNotNull(cache.get("https://mangadex.org"))

        cache.invalidate("https://mangadex.org")

        assertNull(cache.get("https://mangadex.org"))
        cache.exists("https://mangadex.org") shouldBe false
    }

    @Test
    fun `corrupt cached entry is treated as absent`() = runTest {
        // Seed a malformed JSON under the source profile key.
        store.getString("source_profile_mangadex_org", "").set("{ this is not json")

        assertNull(cache.get("https://mangadex.org"))
    }

    @Test
    fun `getAll returns profiles for all profiled domains`() = runTest {
        cache.save(SourceProfile(baseUrl = "https://mangadex.org", contentType = ContentType.MANGA))
        cache.save(SourceProfile(baseUrl = "https://manganato.com", contentType = ContentType.MANGA))

        val all = cache.getAll()
        assertEquals(2, all.size)
        assertEquals(setOf("https://mangadex.org", "https://manganato.com"), all.map { it.baseUrl }.toSet())
    }

    /**
     * No sources configured is not "these three sites".
     *
     * This test used to assert the opposite — that `getAllProfiledDomains` returned 3 defaults
     * before any profiling — which is how the app came to list mangadex.org, manganato.com and
     * asuratoons.com as available sources despite having no way to serve them. `ADR-0015` removed the
     * only engine that could create a profile, so the real list is now always empty, and the fallback
     * was the permanent answer rather than a fallback.
     *
     * An empty answer is the honest one: the Sources list renders empty until the user installs an
     * extension APK.
     */
    @Test
    fun `getAllProfiledDomains is empty when nothing is profiled`() = runTest {
        assertEquals(
            emptySet<String>(),
            cache.getAllProfiledDomains(),
            "no profile can be created, so inventing domains here would list sources that cannot load",
        )
    }
}
