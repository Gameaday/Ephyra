package ephyra.domain.source.interactor

import app.cash.turbine.test
import ephyra.domain.source.model.StubSource
import ephyra.domain.source.service.SourceManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GetCatalogueSourcesTest {

    private val fakeSourceManager = FakeSourceManager()
    private val getCatalogueSources = GetCatalogueSources(fakeSourceManager)

    @Test
    fun `subscribe delegates to sourceManager catalogueSources flow`() = runTest {
        val catalogueSource: CatalogueSource = mockk(relaxed = true)
        fakeSourceManager.catalogueSourceFlow = flowOf(listOf(catalogueSource))

        getCatalogueSources.subscribe().test {
            assertEquals(listOf(catalogueSource), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `get delegates to sourceManager getCatalogueSources`() {
        val catalogueSource: CatalogueSource = mockk(relaxed = true)
        fakeSourceManager.catalogueSourceList = listOf(catalogueSource)

        assertEquals(listOf(catalogueSource), getCatalogueSources.get())
    }

    private class FakeSourceManager : SourceManager {
        var catalogueSourceFlow: Flow<List<CatalogueSource>> = flowOf(emptyList())
        var catalogueSourceList: List<CatalogueSource> = emptyList()

        override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true)
        override val catalogueSources: Flow<List<CatalogueSource>> get() = catalogueSourceFlow
        override fun get(sourceKey: Long): Source? = null
        override fun getOrStub(sourceKey: Long): Source = mockk(relaxed = true)
        override fun getOnlineSources(): List<HttpSource> = emptyList()
        override fun getCatalogueSources(): List<CatalogueSource> = catalogueSourceList
        override fun getStubSources(): List<StubSource> = emptyList()
    }
}
