package ephyra.domain.content.source.interactor

import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.PreferenceStore
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.source.ContentSourceOrchestrator
import ephyra.domain.content.source.SourceProfile
import ephyra.domain.content.source.SourceProfileCache
import ephyra.domain.content.source.SourceType
import ephyra.domain.extension.model.Extension
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.source.service.SourceManager
import ephyra.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GetAvailableSourcesTest {

    private val sourceManager: SourceManager = mockk()
    private val extensionManager: ExtensionManager = mockk()
    private val orchestrator: ContentSourceOrchestrator = mockk()
    private val profileCache: SourceProfileCache = mockk()
    private val preferenceStore: PreferenceStore = mockk()
    private val sourcePreferences: SourcePreferences = mockk()

    private val disabledSourcesPref: Preference<Set<String>> = mockk()
    private val profiledDomainsPref: Preference<Set<String>> = mockk()

    private val catalogueSourcesFlow = MutableStateFlow<List<CatalogueSource>>(emptyList())
    private val installedExtensionsFlow = MutableStateFlow<List<Extension.Installed>>(emptyList())
    private val profiledDomainsFlow = MutableStateFlow<Set<String>>(emptySet())

    private lateinit var getAvailableSources: GetAvailableSources

    private class TestCatalogueSource(
        override val id: Long,
        override val name: String,
        override val lang: String,
    ) : CatalogueSource {
        override val supportsLatest: Boolean = true
        override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(emptyList(), false)
        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
            MangasPage(emptyList(), false)
        override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(emptyList(), false)
        override fun getFilterList(): FilterList = FilterList()
        override suspend fun getMangaDetails(manga: SManga): SManga = manga
        override suspend fun getChapterList(manga: SManga): List<SChapter> = emptyList()
        override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
    }

    @BeforeEach
    fun setUp() {
        every { sourceManager.catalogueSources } returns catalogueSourcesFlow
        every { extensionManager.installedExtensionsFlow } returns installedExtensionsFlow
        every { preferenceStore.getStringSet("profiled_domains_list", emptySet()) } returns profiledDomainsPref
        every { profiledDomainsPref.changes() } returns profiledDomainsFlow
        every { sourcePreferences.disabledSources() } returns disabledSourcesPref
        coEvery { disabledSourcesPref.get() } returns emptySet()

        getAvailableSources = GetAvailableSources(
            sourceManager = sourceManager,
            extensionManager = extensionManager,
            orchestrator = orchestrator,
            profileCache = profileCache,
            preferenceStore = preferenceStore,
            sourcePreferences = sourcePreferences,
        )
    }

    @Test
    fun `groups multi-language extension sources into single UnifiedSource`() = runTest {
        val subSources = (1..60).map { idx ->
            TestCatalogueSource(id = 1000L + idx, name = "MangaDex ($idx)", lang = "lang_$idx")
        }

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
            sources = subSources,
            icon = null,
        )

        installedExtensionsFlow.value = listOf(extension)
        catalogueSourcesFlow.value = subSources
        every { extensionManager.getExtensionPackage(any()) } returns "eu.kanade.tachiyomi.extension.all.mangadex"

        val result = getAvailableSources().first()

        assertEquals(1, result.size)
        val unified = result.first()
        assertEquals("MangaDex", unified.name)
        assertEquals(SourceType.LEGACY_EXTENSION, unified.sourceType)
        assertEquals("eu.kanade.tachiyomi.extension.all.mangadex", unified.extensionId)
        assertTrue(unified.enabled)
    }

    @Test
    fun `groups multi-language extension sources even when extension package lookup is null`() = runTest {
        val subSources = (1..60).map { idx ->
            TestCatalogueSource(id = 2000L + idx, name = "MangaDex ($idx)", lang = "lang_$idx")
        }

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
            sources = subSources,
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
        assertEquals(SourceType.LEGACY_EXTENSION, unified.sourceType)
        assertEquals("eu.kanade.tachiyomi.extension.all.mangadex", unified.extensionId)
        assertTrue(unified.enabled)
    }

    @Test
    fun `includes heuristic profile alongside legacy extension`() = runTest {
        val subSources = listOf(
            TestCatalogueSource(id = 101L, name = "Extension Source", lang = "en"),
        )
        val extension = Extension.Installed(
            name = "Single Extension",
            pkgName = "eu.kanade.tachiyomi.extension.single",
            versionName = "1.0",
            versionCode = 1L,
            libVersion = 1.0,
            lang = "en",
            isNsfw = false,
            hasUpdate = false,
            isObsolete = false,
            isShared = false,
            pkgFactory = null,
            repoUrl = null,
            sources = subSources,
            icon = null,
        )

        val profile = SourceProfile(
            baseUrl = "https://heuristic.org",
            contentType = ContentType.MANGA,
            sourceType = SourceType.HEURISTIC,
            displayName = "Heuristic Source",
            enabled = true,
        )

        installedExtensionsFlow.value = listOf(extension)
        catalogueSourcesFlow.value = subSources
        every { extensionManager.getExtensionPackage(101L) } returns "eu.kanade.tachiyomi.extension.single"

        coEvery { profileCache.get("https://heuristic.org") } returns profile
        profiledDomainsFlow.value = setOf("https://heuristic.org")

        val result = getAvailableSources().first()

        assertEquals(2, result.size)
        val names = result.map { it.name }
        assertTrue(names.contains("Single Extension"))
        assertTrue(names.contains("Heuristic Source"))
    }
}
