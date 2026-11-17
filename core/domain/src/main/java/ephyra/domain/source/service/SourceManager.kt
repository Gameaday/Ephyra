package ephyra.domain.source.service

import ephyra.domain.source.model.StubSource
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface SourceManager {

    val isInitialized: StateFlow<Boolean>

    /**
     * The catalogue sources as a hot flow. Named distinctly from [getCatalogueSources] on purpose:
     * the two once shared a JVM signature (`getCatalogueSources()`), which made MockK resolve the
     * wrong member non-deterministically and flake `SourceReconcilerTest`. A property and a function
     * with the same name are a JVM-level collision, not a Kotlin one.
     */
    val catalogueSourceFlow: Flow<List<CatalogueSource>>

    fun get(sourceKey: Long): Source?

    fun getOrStub(sourceKey: Long): Source

    fun getOnlineSources(): List<HttpSource>

    fun getCatalogueSources(): List<CatalogueSource>

    fun getStubSources(): List<StubSource>
}
