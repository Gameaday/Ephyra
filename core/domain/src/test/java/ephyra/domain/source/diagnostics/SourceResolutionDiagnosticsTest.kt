package ephyra.domain.source.diagnostics

import ephyra.domain.content.source.PreferenceSourceProfileStore
import ephyra.domain.content.source.SourceProfileCache
import ephyra.domain.extension.model.Extension
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.source.model.StubSource
import ephyra.domain.source.service.SourceManager
import ephyra.testutil.FakePreferenceStore
import eu.kanade.tachiyomi.source.online.HttpSource
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

/**
 * Pins what `DEF-029`'s instrument reports, including the state that produces the reported defect:
 * a domain the profile cache claims while the source map holds no source for it.
 *
 * The profile cache is the **real** implementation over [FakePreferenceStore], not a mock, because
 * the divergence being instrumented lives in that class's fallback rather than in any caller.
 */
class SourceResolutionDiagnosticsTest {

    private val store = FakePreferenceStore()
    private val origin = "https://mangadex.org"

    private val sourceManager: SourceManager = mockk(relaxed = true) {
        every { isInitialized } returns MutableStateFlow(true)
        every { getOnlineSources() } returns emptyList()
        every { getStubSources() } returns emptyList()
        every { get(any<Long>()) } returns null
    }
    private val extensionManager: ExtensionManager = mockk(relaxed = true) {
        every { installedExtensionsFlow } returns MutableStateFlow(emptyList<Extension.Installed>())
    }

    private fun diagnostics() = SourceResolutionDiagnostics(
        sourceManager = sourceManager,
        extensionManager = extensionManager,
        profileCache = SourceProfileCache(PreferenceSourceProfileStore(store, Json)),
    )

    private fun httpSource(sourceOrigin: String, sourceId: Long): HttpSource = mockk(relaxed = true) {
        every { baseUrl } returns sourceOrigin
        every { id } returns sourceId
    }

    private fun legacyIdentity(url: String): Long = url.hashCode().toLong()

    @Test
    fun `a profiled domain with no registered source is reported as unregistered`() = runTest {
        store.getStringSet("profiled_domains_list", emptySet()).set(setOf(origin))

        val report = diagnostics().inspectRegistration()

        report.profiledDomains shouldBe setOf(origin)
        report.domainsWithoutRegisteredSource shouldBe setOf(origin)
        report.domainsWithIdentityMismatch shouldBe emptySet()
    }

    /**
     * A profiled domain with no registered source is the DEF-029 signature, and the diagnostic must
     * report it rather than pass.
     *
     * This test used to reach that state through the cache's *hardcoded fallback* — writing no
     * preference and asserting the invented trio came back unregistered. That fallback is gone
     * (`ADR-0015` removed the only engine that could create a profile, so it was permanently the
     * answer rather than a fallback), which means the scenario has to be set up honestly: a domain
     * really is in the list, and really is absent from the source map. That is the actual defect —
     * "the resolution machinery claims domains the map does not have" — rather than an artifact of
     * where the claim came from.
     */
    @Test
    fun `a profiled domain with no registered source is reported as missing`() = runTest {
        // Written to the preference, and deliberately *not* registered in the source map.
        store.getStringSet("profiled_domains_list", emptySet()).set(setOf(origin))

        val report = diagnostics().inspectRegistration()

        report.profiledDomains shouldBe setOf(origin)
        report.domainsWithoutRegisteredSource shouldBe setOf(origin)
        report.sourceManagerReady shouldBe true
    }

    /**
     * With nothing profiled there is nothing to disagree about.
     *
     * Worth pinning separately because the old fallback made this state impossible to reach: the
     * cache always answered with three domains, so "no sources configured" was never observable and
     * a healthy install looked identical to a broken one.
     */
    @Test
    fun `an unprofiled install reports no disagreement at all`() = runTest {
        // No preference written, and the cache now honestly answers empty.
        val report = diagnostics().inspectRegistration()

        report.profiledDomains shouldBe emptySet()
        report.domainsWithoutRegisteredSource shouldBe emptySet()
        report.sourceManagerReady shouldBe true
    }

    @Test
    fun `a source registered under the origin hash is neither missing nor mismatched`() = runTest {
        store.getStringSet("profiled_domains_list", emptySet()).set(setOf(origin))
        every { sourceManager.getOnlineSources() } returns listOf(httpSource(origin, legacyIdentity(origin)))

        val report = diagnostics().inspectRegistration()

        report.domainsWithoutRegisteredSource shouldBe emptySet()
        report.domainsWithIdentityMismatch shouldBe emptySet()
        report.registeredSourceIds shouldBe setOf(legacyIdentity(origin))
    }

    @Test
    fun `a source whose id is not the origin hash is reported as an identity mismatch`() = runTest {
        store.getStringSet("profiled_domains_list", emptySet()).set(setOf(origin))
        every { sourceManager.getOnlineSources() } returns listOf(httpSource(origin, 42L))

        val report = diagnostics().inspectRegistration()

        // Registered, so it is not "missing", and that is exactly why the mismatch needs its own
        // field: an entry written against the other id would resolve to nothing while the source
        // looks present.
        report.domainsWithoutRegisteredSource shouldBe emptySet()
        report.domainsWithIdentityMismatch shouldBe setOf(origin)
    }

    @Test
    fun `an entry whose source is missing reports the domain that claims its id`() = runTest {
        store.getStringSet("profiled_domains_list", emptySet()).set(setOf(origin))

        val report = diagnostics().inspectEntry(legacyIdentity(origin))

        report.resolvedClass shouldBe null
        report.isStub shouldBe false
        // A non-empty claim means the source is genuinely absent: re-registering the domain
        // recovers this entry.
        report.domainsClaimingId shouldBe setOf(origin)
    }

    @Test
    fun `an entry whose id matches no profiled domain reports no claiming domain`() = runTest {
        store.getStringSet("profiled_domains_list", emptySet()).set(setOf(origin))

        val report = diagnostics().inspectEntry(12345L)

        report.resolvedClass shouldBe null
        // Empty claim is the orphaning ADR-0012 describes: the id was written against a baseUrl
        // whose spelling no longer exists, so no domain can even be named as its owner.
        report.domainsClaimingId shouldBe emptySet()
    }

    @Test
    fun `a stub-held entry is reported as a stub with its name`() = runTest {
        every { sourceManager.getStubSources() } returns
            listOf(StubSource(id = legacyIdentity(origin), lang = "en", name = "MangaDex"))

        val report = diagnostics().inspectEntry(legacyIdentity(origin))

        report.resolvedClass shouldBe null
        report.isStub shouldBe true
        report.stubName shouldBe "MangaDex"
    }

    @Test
    fun `the loaded-component inventory and scraper mapping are reported`() = runTest {
        store.getStringSet("profiled_domains_list", emptySet()).set(setOf(origin))
        store.getString("baseUrl_scraper_mapping_mangadex.org", "").set("mangadex_scraper.js")
        every { extensionManager.installedExtensionsFlow } returns MutableStateFlow(
            listOf(
                Extension.Installed(
                    name = "MangaDex",
                    pkgName = "eu.kanade.tachiyomi.extension.all.mangadex",
                    versionName = "1.5.0",
                    versionCode = 15L,
                    libVersion = 1.5,
                    lang = "all",
                    isNsfw = false,
                    pkgFactory = null,
                    sources = emptyList(),
                    icon = null,
                    isShared = false,
                ),
            ),
        )

        val report = diagnostics().inspectRegistration()

        report.installedExtensions.map { it.pkgName } shouldContainExactly
            listOf("eu.kanade.tachiyomi.extension.all.mangadex")
    }
}
