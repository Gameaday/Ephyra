package ephyra.domain.updates.interactor

import ephyra.domain.updates.model.UpdatesWithRelations
import ephyra.domain.updates.repository.UpdatesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Pins the `DEF-006` guarantee: updates are library-scoped unless a consumer explicitly opts out.
 *
 * The reported symptom is absent from the shipping app -- `UpdatesViewModel` passes
 * `libraryOnly = true` at its only call site -- so nothing was broken. What was missing is the
 * guarantee. The parameter defaulted to `false`, so any future consumer that omitted the argument
 * would silently receive the whole catalogue rather than the library, which is the reported defect
 * one call site away and impossible to notice in review.
 *
 * So the test is about the *default*, not about the current caller. A caller that never mentions
 * the argument must still get scoped updates, and the only way to see everything has to be a
 * deliberate `libraryOnly = false`.
 */
class GetUpdatesScopeTest {

    /** Records the scope it was asked for, so the default can be observed. */
    private class RecordingRepository : UpdatesRepository {
        var requestedLibraryOnly: Boolean? = null
            private set

        override suspend fun awaitWithRead(
            read: Boolean,
            after: Long,
            limit: Long,
        ): List<UpdatesWithRelations> = emptyList()

        override fun subscribeAll(
            after: Long,
            limit: Long,
            unread: Boolean?,
            started: Boolean?,
            bookmarked: Boolean?,
            hideExcludedScanlators: Boolean,
            libraryOnly: Boolean,
        ): Flow<List<UpdatesWithRelations>> {
            requestedLibraryOnly = libraryOnly
            return emptyFlow()
        }

        override fun subscribeWithRead(
            read: Boolean,
            after: Long,
            limit: Long,
        ): Flow<List<UpdatesWithRelations>> = emptyFlow()
    }

    private fun subscribeOmittingScope(repository: UpdatesRepository): GetUpdates =
        GetUpdates(repository).also {
            it.subscribe(
                instant = Instant.EPOCH,
                unread = null,
                started = null,
                bookmarked = null,
            )
        }

    @Test
    fun `a caller that says nothing about scope still gets library-scoped updates`() {
        val repository = RecordingRepository()

        subscribeOmittingScope(repository)

        assertTrue(
            repository.requestedLibraryOnly == true,
            "Omitting `libraryOnly` must scope to the library, but forwarded " +
                "${repository.requestedLibraryOnly}. The permissive default is the shape of the " +
                "reported defect: a new consumer silently opts out of the requirement.",
        )
    }

    @Test
    fun `opting out is still possible, and only by asking`() {
        val repository = RecordingRepository()

        GetUpdates(repository).subscribe(
            instant = Instant.EPOCH,
            unread = null,
            started = null,
            bookmarked = null,
            libraryOnly = false,
        )

        assertFalse(
            repository.requestedLibraryOnly == true,
            "An explicit opt-out must still work; scoping by default is not the same as " +
                "removing the choice.",
        )
    }
}
