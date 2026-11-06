package ephyra.domain.source.registry

import ephyra.domain.content.model.ContentType
import ephyra.domain.content.source.PreferenceSourceProfileStore
import ephyra.domain.content.source.SourceProfile
import ephyra.domain.content.source.SourceProfileCache
import ephyra.domain.extension.model.Extension
import ephyra.domain.extension.service.ExtensionManager
import ephyra.testutil.FakePreferenceStore
import eu.kanade.tachiyomi.source.online.HttpSource
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the registry itself — existence, identity, health-as-availability and removal — over the
 * real [FakePreferenceStore] and the real preference-backed profile store, because the registry
 * owns the profiled-domains preference and must be tested against the persistence it will really
 * read, not against a mock of the shape of it.
 *
 * The registry captures its `Preference` instance once, at construction (one owner, one reader),
 * so every test seeds the store *before* calling [registry] — mirroring the one-construction-per-
 * process lifecycle Hilt gives production code.
 */
class SourceRegistryTest {

    private val store = FakePreferenceStore()
    private val json = Json { ignoreUnknownKeys = true }
    private val installedExtensions = MutableStateFlow(emptyList<Extension.Installed>())

    private val extensionManager: ExtensionManager = mockk {
        every { installedExtensionsFlow } returns installedExtensions
    }

    /** Built after each test's seeding, mirroring one construction per process. */
    private fun registry() = SourceRegistry(
        extensionManager = extensionManager,
        preferenceStore = store,
        profileCache = SourceProfileCache(PreferenceSourceProfileStore(store, json)),
    )

    private fun seedDomains(vararg domains: String) {
        store.getStringSet("profiled_domains_list", emptySet()).set(domains.toSet())
    }

    private fun httpSource(sourceId: Long, sourceName: String, sourceBaseUrl: String): HttpSource =
        mockk(relaxed = true) {
            every { id } returns sourceId
            every { name } returns sourceName
            every { baseUrl } returns sourceBaseUrl
        }

    @Test
    fun `an empty store registers nothing`() = runTest {
        registry().currentRegistrations() shouldBe emptyList()
    }

    @Test
    fun `a payload-less domain exists, degraded, named by its raw spelling`() = runTest {
        seedDomains("https://mangadex.org")

        val registrations = registry().currentRegistrations()

        registrations shouldHaveSize 1
        val registration = registrations.single()
        registration.kind shouldBe SourceRegistrationKind.ORIGIN_ADDRESSED
        registration.availability shouldBe SourceAvailability.DEGRADED
        registration.displayName shouldBe "https://mangadex.org"
        registration.id shouldBe SourceIdentityPolicy.identityOf("https://mangadex.org")
        registration.origin shouldBe SourceOrigin("https://mangadex.org")
        // Existence does not depend on health: no payload, but registered and renderable.
        registration.enabled shouldBe true
    }

    @Test
    fun `two spellings of one origin are one registration`() = runTest {
        seedDomains("https://mangadex.org/", "HTTPS://mangadex.org:443")

        val registrations = registry().currentRegistrations()

        registrations shouldHaveSize 1
        registrations.single().origin shouldBe SourceOrigin("https://mangadex.org")
    }

    @Test
    fun `a saved profile registers available under its display name`() = runTest {
        PreferenceSourceProfileStore(store, json).save(
            SourceProfile(
                baseUrl = "https://mangadex.org",
                contentType = ContentType.MANGA,
                displayName = "MangaDex",
            ),
        )

        val registration = registry().currentRegistrations().single()

        registration.availability shouldBe SourceAvailability.AVAILABLE
        registration.displayName shouldBe "MangaDex"
        registration.enabled shouldBe true
    }

    @Test
    fun `a failing profile is degraded, not absent`() = runTest {
        PreferenceSourceProfileStore(store, json).save(
            SourceProfile(
                baseUrl = "https://mangadex.org",
                contentType = ContentType.MANGA,
                displayName = "MangaDex",
                failureCount = 3,
            ),
        )

        val registration = registry().currentRegistrations().single()

        registration.availability shouldBe SourceAvailability.DEGRADED
        registration.failureCount shouldBe 3
    }

    @Test
    fun `an extension source registers under the extension's own id`() = runTest {
        installedExtensions.value = listOf(
            Extension.Installed(
                name = "Single Extension",
                pkgName = "eu.kanade.tachiyomi.extension.single",
                versionName = "1.0",
                versionCode = 1L,
                libVersion = 1.0,
                lang = "en",
                isNsfw = false,
                pkgFactory = null,
                sources = listOf(
                    httpSource(
                        sourceId = 101L,
                        sourceName = "Extension Source",
                        sourceBaseUrl = "https://ext.example.org",
                    ),
                ),
                icon = null,
                isShared = false,
            ),
        )

        val registration = registry().currentRegistrations().single()

        registration.id shouldBe 101L
        registration.kind shouldBe SourceRegistrationKind.EXTENSION_APK
        registration.availability shouldBe SourceAvailability.AVAILABLE
        registration.extensionPkgName shouldBe "eu.kanade.tachiyomi.extension.single"
        registration.origin shouldBe SourceOrigin("https://ext.example.org")
    }

    @Test
    fun `an entry id from an old spelling resolves as a spelling change naming the origin`() = runTest {
        seedDomains("https://mangadex.org/")

        val legacyId = "https://mangadex.org/".hashCode().toLong()
        val resolution = registry().resolveEntry(legacyId)

        resolution shouldBe SourceEntryResolution.SpellingChanged(
            id = legacyId,
            claimedOrigins = setOf(SourceOrigin("https://mangadex.org")),
        )
    }

    @Test
    fun `a clean-spelling entry id resolves`() = runTest {
        seedDomains("https://mangadex.org")

        // Compat property: for an already-canonical spelling the registry's id equals the
        // legacy raw hash, which is precisely why the upgrade orphans no clean-spelling entry.
        val resolution = registry().resolveEntry("https://mangadex.org".hashCode().toLong())

        assertTrue(resolution is SourceEntryResolution.Resolved)
        (resolution as SourceEntryResolution.Resolved).registration.id shouldBe
            SourceIdentityPolicy.identityOf("https://mangadex.org")
    }

    @Test
    fun `an unknown id is absent`() = runTest {
        seedDomains("https://mangadex.org")

        registry().resolveEntry(12345L) shouldBe SourceEntryResolution.Absent(12345L)
    }

    @Test
    fun `unregister erases every spelling of one origin and only that origin`() = runTest {
        seedDomains("https://mangadex.org/", "https://example.org")

        val removed = registry().unregister("https://mangadex.org")

        removed shouldBe true
        store.getStringSet("profiled_domains_list", emptySet()).get() shouldBe setOf("https://example.org")
        // A second removal of the same origin has nothing left to erase.
        registry().unregister("https://mangadex.org") shouldBe false
    }

    @Test
    fun `registrations react to an unregister`() = runTest {
        seedDomains("https://mangadex.org", "https://example.org")

        val registry = registry()
        registry.registrations.first() shouldHaveSize 2

        registry.unregister("https://example.org") shouldBe true
        registry.registrations.first().map { it.displayName } shouldBe listOf("https://mangadex.org")
    }
}
