package ephyra.domain.source.repository

import ephyra.source.api.SourceDescriptor
import ephyra.source.api.SourceLifecycleRecord
import ephyra.source.api.SourceLifecycleTransition

/** Narrow persistence port for target source definitions and explicit user lifecycle state. */
interface SourceLifecycleRepository {
    suspend fun getAll(): List<SourceLifecycleRecord>
    suspend fun discover(descriptor: SourceDescriptor, atMillis: Long): SourceLifecycleTransition
}
