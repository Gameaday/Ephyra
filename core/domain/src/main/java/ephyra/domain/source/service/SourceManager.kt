package ephyra.domain.source.service

import ephyra.domain.source.model.StubSource
import ephyra.source.api.SourceGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface SourceManager {

    val isInitialized: StateFlow<Boolean>

    /**
     * The catalogue sources as a hot flow.
     */
    val catalogueSourceFlow: Flow<List<SourceGateway>>

    fun get(sourceKey: Long): SourceGateway?

    fun getOrStub(sourceKey: Long): SourceGateway

    fun getOnlineSources(): List<SourceGateway>

    fun getCatalogueSources(): List<SourceGateway>

    fun getStubSources(): List<StubSource>
}
