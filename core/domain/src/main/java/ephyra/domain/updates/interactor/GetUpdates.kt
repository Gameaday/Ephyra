package ephyra.domain.updates.interactor

import ephyra.domain.updates.model.UpdatesWithRelations
import ephyra.domain.updates.repository.UpdatesRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant

class GetUpdates(
    private val repository: UpdatesRepository,
) {

    suspend fun await(read: Boolean, after: Long): List<UpdatesWithRelations> {
        return repository.awaitWithRead(read, after, limit = 500)
    }

    fun subscribe(
        instant: Instant,
        unread: Boolean?,
        started: Boolean?,
        bookmarked: Boolean?,
        hideExcludedScanlators: Boolean = false,
        // DEF-006: defaults to the *scoped* value, not the permissive one. A caller that omits
        // this argument previously got every update in the catalogue, which is the shape of the
        // reported defect one call site away. Opting out is now the explicit thing.
        libraryOnly: Boolean = true,
    ): Flow<List<UpdatesWithRelations>> {
        return repository.subscribeAll(
            instant.toEpochMilli(),
            limit = 500,
            unread = unread,
            started = started,
            bookmarked = bookmarked,
            hideExcludedScanlators = hideExcludedScanlators,
            libraryOnly = libraryOnly,
        )
    }

    fun subscribe(read: Boolean, after: Long): Flow<List<UpdatesWithRelations>> {
        return repository.subscribeWithRead(read, after, limit = 500)
    }
}
