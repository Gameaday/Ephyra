package ephyra.domain.source.interactor

import app.cash.turbine.test
import ephyra.domain.source.model.StubSource
import ephyra.domain.source.service.SourceManager
import ephyra.source.api.SourceGateway
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
        val gateway: SourceGateway = mockk(relaxed = true)
        fakeSourceManager.backingCatalogueFlow = flowOf(listOf(gateway))

        getCatalogueSources.subscribe().test {
            assertEquals(listOf(gateway), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `get delegates to sourceManager getCatalogueSources`() {
        val gateway: SourceGateway = mockk(relaxed = true)
        fakeSourceManager.catalogueSourceList = listOf(gateway)

        assertEquals(listOf(gateway), getCatalogueSources.get())
    }

    private class FakeSourceManager : SourceManager {
        var backingCatalogueFlow: Flow<List<SourceGateway>> = flowOf(emptyList())
        var catalogueSourceList: List<SourceGateway> = emptyList()

        override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true)
        override val catalogueSourceFlow: Flow<List<SourceGateway>> get() = backingCatalogueFlow
        override fun get(sourceKey: Long): SourceGateway? = null
        override fun getOrStub(sourceKey: Long): SourceGateway = mockk(relaxed = true)
        override fun getOnlineSources(): List<SourceGateway> = emptyList()
        override fun getCatalogueSources(): List<SourceGateway> = catalogueSourceList
        override fun getStubSources(): List<StubSource> = emptyList()
    }
}
