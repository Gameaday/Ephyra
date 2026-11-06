package ephyra.domain.source.diagnostics

import ephyra.domain.extension.model.Extension
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.source.model.StubSource
import ephyra.domain.source.registry.SourceEntryResolution
import ephyra.domain.source.registry.SourceRegistry
import ephyra.domain.source.service.SourceManager
import eu.kanade.tachiyomi.source.online.HttpSource
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins what the projection audit reports: the registry-to-map agreement is audited where it can
 * drift, and an entry resolution is the registry's typed answer rather than a re-derived rule.
 *
 * The registry is the **real** implementation over the real extension flow, not a mock, because
 * the instrument exists to watch real ownership — auditing a mock of the owner would assert the
 * mock, not the fact.
 */
class SourceResolutionDiagnosticsTest {

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

    private fun diagnostics() = SourceResolutionDiagnostics(
        sourceManager = sourceManager,
        extensionManager = extensionManager,
        sourceRegistry = SourceRegistry(extensionManager),
    )

    private fun httpSource(sourceId: Long, sourceName: String, sourceBaseUrl: String): HttpSource =
        mockk(relaxed = true) {
            every { id } returns sourceId
            every { name } returns sourceName
            every { baseUrl } returns sourceBaseUrl
        }

    private fun installedExtension(pkgName: String, vararg sources: HttpSource): Extension.Installed =
        Extension.Installed(
            name = "Extension $pkgName",
            pkgName = pkgName,
            versionName = "1.0",
            versionCode = 1L,
            libVersion = 1.0,
            lang = "en",
            isNsfw = false,
            pkgFactory = null,
            sources = sources.toList(),
            icon = null,
            isShared = false,
        )

    @Test
    fun `a registration with no map source is reported as lost by the projection`() = runTest {
        // Registered in the registry, deliberately absent from the map: the projection lost a
        // source the registry says exists.
        installedExtensions.value = listOf(
            installedExtension(
                "eu.kanade.tachiyomi.extension.missing",
                httpSource(101L, "Missing", "https://missing.example.org"),
            ),
        )

        val report = diagnostics().inspectRegistration()

        report.registrationIds shouldBe setOf(101L)
        report.registrationsWithoutMapSource shouldBe setOf(101L)
        report.mapSourcesWithoutRegistration shouldBe emptySet()
        report.sourceManagerReady shouldBe true
    }

    @Test
    fun `a map source the registry does not know is reported as invented by the projection`() = runTest {
        every { sourceManager.getOnlineSources() } returns
            listOf(httpSource(42L, "Foreign", "https://foreign.example.org"))

        val report = diagnostics().inspectRegistration()

        report.registrationsWithoutMapSource shouldBe emptySet()
        report.mapSourcesWithoutRegistration shouldBe setOf(42L)
    }

    @Test
    fun `a map source under the registry identity reports no disagreement at all`() = runTest {
        installedExtensions.value = listOf(
            installedExtension(
                "eu.kanade.tachiyomi.extension.agreed",
                httpSource(101L, "Agreed", "https://agreed.example.org"),
            ),
        )
        every { sourceManager.getOnlineSources() } returns
            listOf(httpSource(101L, "Agreed", "https://agreed.example.org"))

        val report = diagnostics().inspectRegistration()

        report.registrationsWithoutMapSource shouldBe emptySet()
        report.mapSourcesWithoutRegistration shouldBe emptySet()
        report.registeredSourceIds shouldBe setOf(101L)
    }

    @Test
    fun `an entry whose id the registry owns resolves and names its map class`() = runTest {
        installedExtensions.value = listOf(
            installedExtension(
                "eu.kanade.tachiyomi.extension.single",
                httpSource(101L, "Extension Source", "https://ext.example.org"),
            ),
        )
        val resolved = mockk<HttpSource> {
            every { id } returns 101L
        }
        every { sourceManager.get(101L) } returns resolved

        val report = diagnostics().inspectEntry(101L)

        assertTrue(report.resolution is SourceEntryResolution.Resolved)
        report.resolvedClass shouldBe resolved.javaClass.name
        report.isStub shouldBe false
    }

    @Test
    fun `an entry id nothing claims is absent with the stub view`() = runTest {
        val report = diagnostics().inspectEntry(12345L)

        assertTrue(report.resolution is SourceEntryResolution.Absent)
        report.resolvedClass shouldBe null
        report.isStub shouldBe false
    }

    @Test
    fun `a stub-held entry is reported as a stub with its name`() = runTest {
        every { sourceManager.getStubSources() } returns
            listOf(StubSource(id = 101L, lang = "en", name = "MangaDex"))

        val report = diagnostics().inspectEntry(101L)

        report.resolvedClass shouldBe null
        report.isStub shouldBe true
        report.stubName shouldBe "MangaDex"
    }

    @Test
    fun `the loaded-component inventory is reported`() = runTest {
        installedExtensions.value = listOf(
            installedExtension("eu.kanade.tachiyomi.extension.all.mangadex"),
        )

        val report = diagnostics().inspectRegistration()

        report.installedExtensions.map { it.pkgName } shouldBe
            listOf("eu.kanade.tachiyomi.extension.all.mangadex")
    }
}
