package ephyra.domain.content.source.interactor

import ephyra.core.common.util.Result
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.source.ContentSourceOrchestrator
import ephyra.domain.content.source.PreferenceSourceProfileStore
import ephyra.domain.content.source.SourceProfile
import ephyra.domain.content.source.SourceProfileCache
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.source.registry.SourceRegistry
import ephyra.testutil.FakePreferenceStore
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fast JVM tests for [RemoveCustomSource] — removing a content source through the registry
 * (`SRC-011`): removal is an erase of every spelling of one origin, owned by the registry, not a
 * second copy of the domain list edited in place here.
 *
 * The [FakePreferenceStore] is shared by the interactor and the registry because in production
 * they share the same persisted state — mocking the store here would let the interactor "remove"
 * a domain the registry still registers, which is the exact class of disagreement this change
 * deletes.
 */
class RemoveCustomSourceTest {

    private val orchestrator = mockk<ContentSourceOrchestrator>(relaxed = true)
    private val store = FakePreferenceStore()
    private val json = Json { ignoreUnknownKeys = true }
    private val profileCache = SourceProfileCache(PreferenceSourceProfileStore(store, json))

    private val baseUrl = "https://mangadex.org"

    /** Built inside each test, after that test's seeding: the registry captures its preference at construction. */
    private fun interactor() = RemoveCustomSource(
        orchestrator = orchestrator,
        sourceRegistry = SourceRegistry(
            extensionManager = mockk<ExtensionManager> {
                every { installedExtensionsFlow } returns MutableStateFlow(emptyList())
            },
            preferenceStore = store,
            profileCache = profileCache,
        ),
        preferenceStore = store,
    )

    private fun heuristicProfile() = SourceProfile(
        baseUrl = baseUrl,
        contentType = ContentType.MANGA,
        enabled = true,
    )

    @Test
    fun `removeSource erases every spelling of the origin and only that origin`() = runTest {
        // The stored spelling differs from the removal input by a trailing slash — the exact
        // class of mismatch that used to leave a spelling behind to resurrect the source.
        store.getStringSet("profiled_domains_list", emptySet())
            .set(setOf("$baseUrl/", "https://example.org"))
        store.getString("baseUrl_scraper_mapping_mangadex.org", "").set("mangadex_scraper.js")
        coEvery { orchestrator.getAllProfiles() } returns listOf(heuristicProfile())

        val result = interactor().removeSource(baseUrl)

        assertTrue(result is Result.Success)
        coVerify { orchestrator.invalidateProfile(baseUrl) }
        // Every spelling of the removed origin is gone; the other origin is untouched.
        store.getStringSet("profiled_domains_list", emptySet()).get() shouldBe setOf("https://example.org")
        // A mapping left by a build that still had scrapers is cleared, so it cannot
        // resurrect if the mechanism ever returns.
        store.getString("baseUrl_scraper_mapping_mangadex.org", "").isSet() shouldBe false
    }

    @Test
    fun `removeSource returns Error when source not found`() = runTest {
        coEvery { orchestrator.getAllProfiles() } returns emptyList()

        val result = interactor().removeSource(baseUrl)

        assertTrue(result is Result.Error)
    }

    @Test
    fun `disableSource delegates to the orchestrator`() = runTest {
        coEvery { orchestrator.setSourceEnabled(baseUrl, false) } returns Result.Success(
            SourceProfile(baseUrl = baseUrl, contentType = ContentType.MANGA, enabled = false),
        )

        val result = interactor().disableSource(baseUrl)

        assertTrue(result is Result.Success)
        coVerify { orchestrator.setSourceEnabled(baseUrl, false) }
    }
}
