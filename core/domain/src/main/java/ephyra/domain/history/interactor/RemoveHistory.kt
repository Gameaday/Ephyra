package ephyra.domain.history.interactor

import ephyra.domain.history.repository.HistoryRepository

class RemoveHistory(
    private val repository: HistoryRepository,
) {

    suspend fun awaitAll(): Boolean {
        return repository.deleteAllHistory()
    }

    /**
     * Resets one read, identified by its history row id.
     *
     * Named distinctly from [awaitByMangaId] because both take a `Long`: overloading two `Long`
     * parameters that mean different things is how a manga id gets passed where a history row id was
     * expected, which would reset the wrong read and type-check perfectly.
     */
    suspend fun await(historyId: Long) {
        repository.resetHistory(historyId)
    }

    /** Resets every read of one series, by its manga id. */
    suspend fun awaitByMangaId(mangaId: Long) {
        repository.resetHistoryByMangaId(mangaId)
    }
}
