package ephyra.domain.source.interactor

import ephyra.domain.source.model.SourceWithCount
import ephyra.domain.source.repository.SourceRepository
import kotlinx.coroutines.flow.Flow

class GetSourcesWithNonLibraryManga(
    private val repository: SourceRepository,
) {

    fun subscribe(): Flow<List<SourceWithCount>> {
        return repository.getSourcesWithNonLibraryManga()
    }
}
