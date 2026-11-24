package ephyra.domain.source.interactor

import ephyra.domain.source.service.SourceManager
import ephyra.source.api.SourceCapability
import ephyra.source.api.SourceGateway
import ephyra.source.api.SourceKind
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GetSourceTest {

    private val sourceManager: SourceManager = mockk()
    private val getSource = GetSource(sourceManager)

    @Test
    fun `get delegates to sourceManager get`() {
        val gateway: SourceGateway = mockk()
        every { sourceManager.get(1L) } returns gateway

        assertEquals(gateway, getSource.get(1L))
        every { sourceManager.get(2L) } returns null
        assertNull(getSource.get(2L))
    }

    @Test
    fun `getOrStub delegates to sourceManager getOrStub`() {
        val gateway: SourceGateway = mockk()
        every { sourceManager.getOrStub(1L) } returns gateway

        assertEquals(gateway, getSource.getOrStub(1L))
    }

    @Test
    fun `getCatalogueSource returns gateway with CATALOG capability`() {
        val catalogueGateway: SourceGateway = mockk {
            every { descriptor.capabilities } returns setOf(SourceCapability.CATALOG)
        }
        val plainGateway: SourceGateway = mockk {
            every { descriptor.capabilities } returns emptySet()
        }

        every { sourceManager.get(1L) } returns catalogueGateway
        every { sourceManager.get(2L) } returns plainGateway

        assertEquals(catalogueGateway, getSource.getCatalogueSource(1L))
        assertNull(getSource.getCatalogueSource(2L))
    }

    @Test
    fun `getHttpSource returns gateway with NATIVE or LEGACY_COMPATIBILITY kind`() {
        val nativeGateway: SourceGateway = mockk {
            every { descriptor.kind } returns SourceKind.NATIVE
        }
        val legacyGateway: SourceGateway = mockk {
            every { descriptor.kind } returns SourceKind.LEGACY_COMPATIBILITY
        }
        val plainGateway: SourceGateway = mockk {
            every { descriptor.kind } returns SourceKind.REPOSITORY
        }

        every { sourceManager.get(1L) } returns nativeGateway
        every { sourceManager.get(2L) } returns legacyGateway
        every { sourceManager.get(3L) } returns plainGateway

        assertEquals(nativeGateway, getSource.getHttpSource(1L))
        assertEquals(legacyGateway, getSource.getHttpSource(2L))
        assertNull(getSource.getHttpSource(3L))
    }
}
