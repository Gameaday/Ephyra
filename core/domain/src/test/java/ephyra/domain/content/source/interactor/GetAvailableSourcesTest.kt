package ephyra.domain.content.source.interactor

import ephyra.core.common.preference.Preference
import ephyra.domain.content.source.SourceType
import ephyra.domain.extension.model.Extension
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.source.model.StubSource
import ephyra.domain.source.service.SourceManager
import ephyra.domain.source.service.SourcePreferences
import ephyra.source.api.SourceCapability
import ephyra.source.api.SourceGateway
import ephyra.source.api.SourceKind
import eu.kanade.tachiyomi.source.Source
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GetAvailableSourcesTest {

    private val catalogueSourcesFlow = MutableStateFlow<List<SourceGateway>>(emptyList())
    private val installedExtensionsFlow = MutableStateFlow<List<Extension.Installed>>(emptyList())

    // Helper to convert SourceGateway to legacy Source for Extension.Installed
    private fun toLegacySource(gateway: SourceGateway): Source {
        val idString = gateway.descriptor.id.value
        val idLong = idString.toLongOrNull() ?: -1L
        return mockk(relaxed = true) {
            every { id } returns idLong
            every { name } returns gateway.descriptor.displayName
        }
    }

    // A hand-written fake instead of mockk: `SourceManager` exposes both a
    // `catalogueSources: Flow<List<SourceGateway>>` property and a
    // `getCatalogueSources(): List<SourceGateway>` overload, and mockk's call recorder
    // resolves the property getter to the `List`-returning overload — throwing a
    // "Class cast exception happened / use `hint`" MockKException while recording the
    // `every { ... }` block (intermittent under its auto-hinter). A fake sidesteps the
    // recorder entirely.
    private val sourceManager: SourceManager = FakeSourceManager(catalogueSourcesFlow)
    private val extensionManager: ExtensionManager = mockk(relaxed = true)
    private val sourcePreferences: SourcePreferences = mockk(relaxed = true)

    private val disabledSourcesPref: Preference<Set<String>> = mockk(relaxed = true)

    private lateinit var getAvailableSources: GetAvailableSources

    private class TestSourceGateway(
        private val testId: Long,
        private val testName: String,
    ) : SourceGateway {
        override val descriptor = ephyra.source.api.SourceDescriptor(
            id = ephyra.source.api.SourceId(testId.toString()),
            displayName = testName,
            kind = SourceKind.NATIVE,
            revision = 1L,
            capabilities = setOf(SourceCapability.CATALOG),
            contentTypes = setOf(ephyra.domain.content.model.ContentType.MANGA),
        )

        override suspend fun search(
            request: ephyra.source.api.SourceSearchRequest,
        ): ephyra.source.api.SourceResult<
            ephyra.source.api.SourcePage<ephyra.source.api.SourceContentItem>,
            > = ephyra.source.api.SourceResult.Empty

        override suspend fun getPopular(
            request: ephyra.source.api.SourceCatalogueRequest,
        ): ephyra.source.api.SourceResult<
            ephyra.source.api.SourcePage<ephyra.source.api.SourceContentItem>,
            > = ephyra.source.api.SourceResult.Empty

        override suspend fun getLatest(
            request: ephyra.source.api.SourceCatalogueRequest,
        ): ephyra.source.api.SourceResult<
            ephyra.source.api.SourcePage<ephyra.source.api.SourceContentItem>,
            > = ephyra.source.api.SourceResult.Empty

        override suspend fun getDetails(
            reference: ephyra.source.api.ContentReference,
        ): ephyra.source.api.SourceResult<ephyra.source.api.SourceContentItem> =
            ephyra.source.api.SourceResult.Empty

        override suspend fun getUnits(
            reference: ephyra.source.api.ContentReference,
        ): ephyra.source.api.SourceResult<
            ephyra.source.api.SourcePage<ephyra.source.api.SourceContentUnit>,
            > = ephyra.source.api.SourceResult.Empty

        override suspend fun getResources(
            reference: ephyra.source.api.UnitReference,
        ): ephyra.source.api.SourceResult<List<ephyra.source.api.SourceResource>> =
            ephyra.source.api.SourceResult.Empty
    }

    @BeforeEach
    fun setUp() {
        every { extensionManager.installedExtensionsFlow } returns installedExtensionsFlow
        every { sourcePreferences.disabledSources() } returns disabledSourcesPref
        coEvery { disabledSourcesPref.get() } returns emptySet()

        getAvailableSources = GetAvailableSources(
            sourceManager = sourceManager,
            extensionManager = extensionManager,
            sourcePreferences = sourcePreferences,
        )
    }

    @Test
    fun `groups multi-language extension sources into single UnifiedSource`() = runTest {
        val subSources = (1..60).map { idx ->
            TestSourceGateway(testId = 1000L + idx, testName = "MangaDex ($idx)")
        }
        val legacySubSources = subSources.map { toLegacySource(it) }

        val extension = Extension.Installed(
            name = "MangaDex",
            pkgName = "eu.kanade.tachiyomi.extension.all.mangadex",
            versionName = "1.4.246",
            versionCode = 1L,
            libVersion = 1.0,
            lang = "all",
            isNsfw = false,
            hasUpdate = false,
            isObsolete = false,
            isShared = false,
            pkgFactory = null,
            repoUrl = null,
            sources = legacySubSources,
            icon = null,
        )

        installedExtensionsFlow.value = listOf(extension)
        catalogueSourcesFlow.value = subSources
        every { extensionManager.getExtensionPackage(any()) } returns "eu.kanade.tachiyomi.extension.all.mangadex"

        val result = getAvailableSources().first()

        assertEquals(1, result.size)
        val unified = result.first()
        assertEquals("MangaDex", unified.name)
        assertEquals(SourceType.REMOTE_EXTENSION, unified.sourceType)
        assertEquals("eu.kanade.tachiyomi.extension.all.mangadex", unified.extensionId)
        assertTrue(unified.enabled)
    }

    @Test
    fun `groups multi-language extension sources even when extension package lookup is null`() = runTest {
        val subSources = (1..60).map { idx ->
            TestSourceGateway(testId = 2000L + idx, testName = "MangaDex ($idx)")
        }
        val legacySubSources = subSources.map { toLegacySource(it) }

        val extension = Extension.Installed(
            name = "MangaDex",
            pkgName = "eu.kanade.tachiyomi.extension.all.mangadex",
            versionName = "1.4.246",
            versionCode = 1L,
            libVersion = 1.0,
            lang = "all",
            isNsfw = false,
            hasUpdate = false,
            isObsolete = false,
            isShared = false,
            pkgFactory = null,
            repoUrl = null,
            sources = legacySubSources,
            icon = null,
        )

        installedExtensionsFlow.value = listOf(extension)
        catalogueSourcesFlow.value = subSources
        // Simulates async cold start where sourceIdToPkgFlow hasn't mapped yet
        every { extensionManager.getExtensionPackage(any()) } returns null

        val result = getAvailableSources().first()

        assertEquals(1, result.size)
        val unified = result.first()
        assertEquals("MangaDex", unified.name)
        assertEquals(SourceType.REMOTE_EXTENSION, unified.sourceType)
        assertEquals("eu.kanade.tachiyomi.extension.all.mangadex", unified.extensionId)
        assertTrue(unified.enabled)
    }

    /**
     * Minimal deterministic [SourceManager] — see the note on [sourceManager] for why this
     * is a hand-written fake rather than a `mockk(relaxed = true)`.
     */
    private class FakeSourceManager(
        private val catalogueSourcesFlow: MutableStateFlow<List<SourceGateway>>,
    ) : SourceManager {
        override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true)
        override val catalogueSourceFlow: Flow<List<SourceGateway>> = catalogueSourcesFlow

        override fun get(sourceKey: Long): SourceGateway? = null
        override fun getOrStub(
            sourceKey: Long,
        ): SourceGateway = TestSourceGateway(testId = sourceKey, testName = "Stub $sourceKey")
        override fun getOnlineSources(): List<SourceGateway> = emptyList()
        override fun getCatalogueSources(): List<SourceGateway> = catalogueSourcesFlow.value
        override fun getStubSources(): List<StubSource> = emptyList()
    }
}
