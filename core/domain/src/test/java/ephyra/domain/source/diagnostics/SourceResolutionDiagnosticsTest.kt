package ephyra.domain.source.diagnostics

import ephyra.domain.content.model.ContentType
import ephyra.domain.content.source.PreferenceSourceProfileStore
import ephyra.domain.content.source.SourceProfile
import ephyra.domain.content.source.SourceProfileCache
import ephyra.domain.extension.model.Extension
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.source.model.StubSource
import ephyra.domain.source.registry.SourceEntryResolution
import ephyra.domain.source.registry.SourceIdentityPolicy
import ephyra.domain.source.registry.SourceRegistry
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins what `DEF-029`'s instrument reports now that the registry is the one owner: the
 * registry-to-map projection is audited where it can drift, and an entry resolution is the
 * registry's typed answer rather than a re-derived rule.
 *
 * The registry is the **real** implementation over [FakePreferenceStore] and the real
 * preference-backed profile store, not a mock, because the instrument exists to watch real
 * ownership — auditing a mock of the owner would assert the mock, not the fact.
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
    private val installedExtensions = MutableStateFlow(emptyList<Extension.Installed>())
    private val extensionManager: ExtensionManager = mockk(relaxed = true) {
        every { installedExtensionsFlow } returns installedExtensions
    }

    /** Built inside each test, after that test's seeding: the registry snapshots its preference at construction. */
    private fun diagnostics() = SourceResolutionDiagnostics(
        sourceManager = sourceManager,
        extensionManager = extensionManager,
        sourceRegistry = SourceRegistry(
            extensionManager = extensionManager,
            preferenceStore = store,
            profileCache = SourceProfileCache(PreferenceSourceProfileStore(store, Json)),
        ),
    )

    private fun httpSource(sourceOrigin: String, sourceId: Long): HttpSource = mockk(relaxed = true) {
        every { baseUrl } returns sourceOrigin
        every { id } returns sourceId
    }

    private fun legacyIdentity(url: String): Long = url.hashCode().toLong()

    @Test
    fun `a payload-less registered domain with no map source is reported as unregistered`() = runTest {
        // In the list (so the registry registers it, degraded) and deliberately absent from the
        // map: the projection lost a source the registry says exists — the DEF-029 signature.
        store.getStringSet("profiled_domains_list", emptySet()).set(setOf(origin))

        val report = diagnostics().inspectRegistration()

        report.profiledDomains shouldBe setOf(origin)
        report.domainsWithoutRegisteredSource shouldBe setOf(origin)
        report.domainsWithIdentityMismatch shouldBe emptySet()
        report.sourceManagerReady shouldBe true
    }

    @Test
    fun `an unprofiled install reports no disagreement at all`() = runTest {
        // No preference written: nothing exists, so nothing can disagree. Worth pinning because
        // the pre-ADR-0015 fallback made this state unobservable — a healthy install looked
        // identical to a broken one.
        val report = diagnostics().inspectRegistration()

        report.profiledDomains shouldBe emptySet()
        report.domainsWithoutRegisteredSource shouldBe emptySet()
        report.domainsWithIdentityMismatch shouldBe emptySet()
        report.sourceManagerReady shouldBe true
    }

    @Test
    fun `a map source under the registry identity is neither missing nor mismatched`() = runTest {
        store.getStringSet("profiled_domains_list", emptySet()).set(setOf(origin))
        // Compat property: for a clean spelling the registry's id equals the legacy raw hash,
        // so this simulates exactly what a correctly projected map entry looks like.
        every { sourceManager.getOnlineSources() } returns listOf(httpSource(origin, legacyIdentity(origin)))

        val report = diagnostics().inspectRegistration()

        report.domainsWithoutRegisteredSource shouldBe emptySet()
        report.domainsWithIdentityMismatch shouldBe emptySet()
        report.registeredSourceIds shouldBe setOf(legacyIdentity(origin))
    }

    @Test
    fun `a map source with a foreign id is an identity mismatch, not an absence`() = runTest {
        store.getStringSet("profiled_domains_list", emptySet()).set(setOf(origin))
        every { sourceManager.getOnlineSources() } returns listOf(httpSource(origin, 42L))

        val report = diagnostics().inspectRegistration()

        // Registered, so it is not "missing", and that is exactly why the mismatch needs its own
        // field: an entry written against the registry's id would resolve to nothing while the
        // source looks present.
        report.domainsWithoutRegisteredSource shouldBe emptySet()
        report.domainsWithIdentityMismatch shouldBe setOf(origin)
    }

    @Test
    fun `a disabled profile is a deliberate absence, not a disagreement`() = runTest {
        // The registry registers the domain; its profile is disabled, so the map is right to
        // omit it. Reporting it would make "the user turned this off" indistinguishable from the
        // defect this instrument exists to catch.
        PreferenceSourceProfileStore(store, Json).save(
            SourceProfile(baseUrl = origin, contentType = ContentType.MANGA, enabled = false),
        )

        val report = diagnostics().inspectRegistration()

        report.domainsWithoutRegisteredSource shouldBe emptySet()
        report.domainsWithIdentityMismatch shouldBe emptySet()
    }

    @Test
    fun `an entry whose id the registry owns resolves with no claiming origin`() = runTest {
        store.getStringSet("profiled_domains_list", emptySet()).set(setOf(origin))

        val report = diagnostics().inspectEntry(SourceIdentityPolicy.identityOf(origin))

        assertTrue(report.resolution is SourceEntryResolution.Resolved)
        report.domainsClaimingId shouldBe emptySet()
    }

    @Test
    fun `an entry id from an old spelling reports the canonical origin that claims it`() = runTest {
        // The stored spelling carries a trailing slash; the pre-SRC-011 entry was written against
        // that spelling's hash. The registry now canonicalizes, so the entry resolves as a
        // spelling change naming the origin — "re-register this and the entry recovers" instead
        // of an unexplained "source not registered".
        store.getStringSet("profiled_domains_list", emptySet()).set(setOf("$origin/"))

        val report = diagnostics().inspectEntry(legacyIdentity("$origin/"))

        assertTrue(report.resolution is SourceEntryResolution.SpellingChanged)
        report.domainsClaimingId shouldBe setOf(origin)
    }

    @Test
    fun `an entry id nothing claims is absent`() = runTest {
        store.getStringSet("profiled_domains_list", emptySet()).set(setOf(origin))

        val report = diagnostics().inspectEntry(12345L)

        assertTrue(report.resolution is SourceEntryResolution.Absent)
        report.domainsClaimingId shouldBe emptySet()
        report.resolvedClass shouldBe null
        report.isStub shouldBe false
    }

    @Test
    fun `a stub-held entry is reported as a stub with its name`() = runTest {
        store.getStringSet("profiled_domains_list", emptySet()).set(setOf(origin))
        every { sourceManager.getStubSources() } returns
            listOf(StubSource(id = legacyIdentity(origin), lang = "en", name = "MangaDex"))

        val report = diagnostics().inspectEntry(legacyIdentity(origin))

        report.resolvedClass shouldBe null
        report.isStub shouldBe true
        report.stubName shouldBe "MangaDex"
        assertTrue(report.resolution is SourceEntryResolution.Resolved)
    }

    @Test
    fun `the loaded-component inventory is reported`() = runTest {
        store.getStringSet("profiled_domains_list", emptySet()).set(setOf(origin))
        installedExtensions.value = listOf(
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
        )

        val report = diagnostics().inspectRegistration()

        report.installedExtensions.map { it.pkgName } shouldContainExactly
            listOf("eu.kanade.tachiyomi.extension.all.mangadex")
    }
}
