package ephyra.domain.source.registry

import ephyra.domain.extension.model.Extension
import ephyra.domain.extension.service.ExtensionManager
import eu.kanade.tachiyomi.source.online.HttpSource
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the registry itself — existence, identity and origin canonicalisation for the only kind
 * of source that exists post-ADR-0017: extension sources.
 *
 * The registry reads the live extension flow, so every test seeds the flow before asking for a
 * reading, mirroring the one-construction-per-process lifecycle Hilt gives production code.
 */
class SourceRegistryTest {

    private val installedExtensions = MutableStateFlow(emptyList<Extension.Installed>())

    private val extensionManager: ExtensionManager = mockk {
        every { installedExtensionsFlow } returns installedExtensions
    }

    private fun registry() = SourceRegistry(extensionManager)

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
    fun `no extensions registers nothing`() = runTest {
        registry().currentRegistrations() shouldBe emptyList()
    }

    @Test
    fun `an extension source registers under the extension's own id`() = runTest {
        installedExtensions.value = listOf(
            installedExtension(
                "eu.kanade.tachiyomi.extension.single",
                httpSource(101L, "Extension Source", "https://ext.example.org"),
            ),
        )

        val registration = registry().currentRegistrations().single()

        registration.id shouldBe 101L
        registration.displayName shouldBe "Extension Source"
        registration.extensionPkgName shouldBe "eu.kanade.tachiyomi.extension.single"
        registration.origin shouldBe SourceOrigin("https://ext.example.org")
        registration.availability shouldBe SourceAvailability.AVAILABLE
    }

    @Test
    fun `an extension origin is canonicalised, so spellings cannot fork identity`() = runTest {
        installedExtensions.value = listOf(
            installedExtension(
                "eu.kanade.tachiyomi.extension.slash",
                httpSource(102L, "Slashed", "https://ext.example.org/"),
            ),
        )

        val registration = registry().currentRegistrations().single()

        registration.origin shouldBe SourceOrigin("https://ext.example.org")
    }

    @Test
    fun `two extensions claiming one id register once, first wins`() = runTest {
        installedExtensions.value = listOf(
            installedExtension(
                "eu.kanade.tachiyomi.extension.first",
                httpSource(101L, "First", "https://first.example.org"),
            ),
            installedExtension(
                "eu.kanade.tachiyomi.extension.second",
                httpSource(101L, "Second", "https://second.example.org"),
            ),
        )

        val registrations = registry().currentRegistrations()

        registrations shouldHaveSize 1
        registrations.single().displayName shouldBe "First"
    }

    @Test
    fun `a registered entry id resolves`() = runTest {
        installedExtensions.value = listOf(
            installedExtension(
                "eu.kanade.tachiyomi.extension.single",
                httpSource(101L, "Extension Source", "https://ext.example.org"),
            ),
        )

        val resolution = registry().resolveEntry(101L)

        assertTrue(resolution is SourceEntryResolution.Resolved)
        (resolution as SourceEntryResolution.Resolved).registration.id shouldBe 101L
    }

    @Test
    fun `an unknown id is absent`() = runTest {
        installedExtensions.value = listOf(
            installedExtension(
                "eu.kanade.tachiyomi.extension.single",
                httpSource(101L, "Extension Source", "https://ext.example.org"),
            ),
        )

        registry().resolveEntry(12345L) shouldBe SourceEntryResolution.Absent(12345L)
    }
}
