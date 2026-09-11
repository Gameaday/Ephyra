package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.UriType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Contract test for the host `source-api` ABI that extension APKs link against at runtime.
 *
 * Extensions are compiled against upstream tachiyomix 1.6 and **dynamically linked** against
 * the host's classes. Any member an extension references that is missing on the host crashes
 * with `IncompatibleClassChangeError` (surfaced by `ExtensionCallBoundary` as
 * "Source 'X' encountered an error") — invisible to the compiler, so it is pinned here.
 *
 * Evidence: the member-level parity audit of 2026-09-10 (doc/PHASE_B_SOURCE_API_PARITY.md),
 * against Mihon upstream `main` @ `1e054ea14d551f5f16c8dd892bfb5962be2426d2`. The only gap
 * ever found was `SManga`/`SChapter.memo` (Phase A); everything below was verified present.
 */
class SourceApiContractTest {

    private val continuationClass = kotlin.coroutines.Continuation::class.java

    private fun assertMember(type: Class<*>, name: String) {
        assertTrue(
            type.declaredMethods.any { it.name == name },
            "Extension ABI contract broken: `$type.$name` is missing. " +
                "Extensions compiled against tachiyomix 1.6 resolve this member at runtime.",
        )
    }

    private fun assertSuspendMember(type: Class<*>, name: String) {
        assertTrue(
            type.declaredMethods.any { it.name == name && it.parameterTypes.last() == continuationClass },
            "Extension ABI contract broken: suspend `$type.$name` is missing (no Continuation parameter).",
        )
    }

    @Test
    fun `Source suspend surface matches tachiyomix 1_6`() {
        val source = Source::class.java
        assertSuspendMember(source, "getPopularManga")
        assertSuspendMember(source, "getLatestUpdates")
        assertSuspendMember(source, "getSearchManga")
        assertSuspendMember(source, "getMangaUpdate")
        assertSuspendMember(source, "getPageList")
        assertSuspendMember(source, "getMangaDetails")
        assertSuspendMember(source, "getChapterList")
        // Deprecated Observable bridge kept for extensions on the fetch* API
        assertMember(source, "fetchMangaDetails")
        assertMember(source, "fetchChapterList")
        assertMember(source, "fetchPageList")
    }

    @Test
    fun `CatalogueSource declares fetch observables`() {
        val catalogue = CatalogueSource::class.java
        assertMember(catalogue, "fetchPopularManga")
        assertMember(catalogue, "fetchSearchManga")
        assertMember(catalogue, "fetchLatestUpdates")
    }

    @Test
    fun `HttpSource surface matches tachiyomix 1_6`() {
        val http = HttpSource::class.java
        assertMember(http, "getHomeUrl")
        assertMember(http, "getMangaUrl")
        assertMember(http, "getChapterUrl")
        assertMember(http, "prepareNewChapter")
        assertMember(http, "getImage")
        assertMember(http, "headersBuilder")
        assertMember(http, "generateId")
        assertMember(http, "popularMangaRequest")
        assertMember(http, "popularMangaParse")
        assertMember(http, "searchMangaRequest")
        assertMember(http, "searchMangaParse")
        assertMember(http, "latestUpdatesRequest")
        assertMember(http, "latestUpdatesParse")
        assertMember(http, "mangaDetailsRequest")
        assertMember(http, "mangaDetailsParse")
        assertMember(http, "chapterListRequest")
        assertMember(http, "chapterListParse")
        assertMember(http, "pageListRequest")
        assertMember(http, "pageListParse")
        assertMember(http, "imageUrlRequest")
        assertMember(http, "imageUrlParse")
        assertSuspendMember(http, "getImageUrl")
        // member-extension helpers used pervasively by extension sources
        assertTrue(
            http.declaredMethods.any {
                it.name == "setUrlWithoutDomain" && it.parameterTypes.first() == SManga::class.java
            },
            "HttpSource.setUrlWithoutDomain(SManga) receiver extension is missing",
        )
        assertTrue(
            http.declaredMethods.any {
                it.name == "setUrlWithoutDomain" && it.parameterTypes.first() == SChapter::class.java
            },
            "HttpSource.setUrlWithoutDomain(SChapter) receiver extension is missing",
        )
    }

    @Test
    fun `MangasPage keeps data-class destructuring ABI`() {
        // Upstream declares component1/component2/copy explicitly (deprecated); Ephyra's data
        // class generates identical JVM signatures. Pin them so the shorthand is never dropped.
        val page = MangasPage(emptyList(), false)
        assertEquals(emptyList<SManga>(), page.component1())
        assertEquals(false, page.component2())
        assertEquals(page, page.copy())
    }

    @Test
    fun `Page states and Filter TriState constants exist`() {
        val states = Page.State::class.sealedSubclasses.map { it.simpleName }
        assertTrue(
            listOf("Queue", "LoadPage", "DownloadImage", "Ready", "Error").all { it in states },
            "Page.State entries missing: found $states",
        )
        assertEquals(0, Filter.TriState.STATE_IGNORE)
        assertEquals(1, Filter.TriState.STATE_INCLUDE)
        assertEquals(2, Filter.TriState.STATE_EXCLUDE)
        assertEquals(listOf("ALWAYS_UPDATE", "ONLY_FETCH_ONCE"), UpdateStrategy.entries.map { it.name })
    }

    @Test
    fun `UriType entries exist for ResolvableSource implementors`() {
        val types = UriType::class.sealedSubclasses.map { it.simpleName }
        assertTrue(
            listOf("Manga", "Chapter", "Unknown").all { it in types },
            "UriType entries missing: $types",
        )
    }

    @Test
    fun `ConfigurableSource preference helpers resolve`() {
        // Top-level functions compile into the file facade class.
        val facade = Class.forName("eu.kanade.tachiyomi.source.ConfigurableSourceKt")
        assertMember(facade, "preferenceKey")
        assertMember(facade, "sourcePreferences")
        // and the interface itself keeps the 1.5 accessor
        assertMember(ConfigurableSource::class.java, "getSourcePreferences")
        assertMember(ConfigurableSource::class.java, "setupPreferenceScreen")
    }

    @Test
    fun `Jsoup helpers resolve in the package extensions import`() {
        val facade = Class.forName("eu.kanade.tachiyomi.util.JsoupExtensionsKt")
        assertMember(facade, "asJsoup")
        assertMember(facade, "selectText")
        assertMember(facade, "selectInt")
        assertMember(facade, "attrOrText")
    }
}
