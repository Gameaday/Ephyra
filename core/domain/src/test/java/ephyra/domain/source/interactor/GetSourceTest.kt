package ephyra.domain.source.interactor

import ephyra.domain.source.service.SourceManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GetSourceTest {

    private val sourceManager: SourceManager = mockk()
    private val getSource = GetSource(sourceManager)

    @Test
    fun `get delegates to sourceManager get`() {
        val source: Source = mockk()
        every { sourceManager.get(1L) } returns source

        assertEquals(source, getSource.get(1L))
        every { sourceManager.get(2L) } returns null
        assertNull(getSource.get(2L))
    }

    @Test
    fun `getOrStub delegates to sourceManager getOrStub`() {
        val source: Source = mockk()
        every { sourceManager.getOrStub(1L) } returns source

        assertEquals(source, getSource.getOrStub(1L))
    }

    @Test
    fun `getCatalogueSource casts when source is CatalogueSource`() {
        val catalogueSource: CatalogueSource = mockk()
        val plainSource: Source = mockk()

        every { sourceManager.get(1L) } returns catalogueSource
        every { sourceManager.get(2L) } returns plainSource

        assertEquals(catalogueSource, getSource.getCatalogueSource(1L))
        assertNull(getSource.getCatalogueSource(2L))
    }

    @Test
    fun `getHttpSource casts when source is HttpSource`() {
        val httpSource: HttpSource = mockk()
        val plainSource: Source = mockk()

        every { sourceManager.get(1L) } returns httpSource
        every { sourceManager.get(2L) } returns plainSource

        assertEquals(httpSource, getSource.getHttpSource(1L))
        assertNull(getSource.getHttpSource(2L))
    }
}
