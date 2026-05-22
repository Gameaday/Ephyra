package ephyra.domain.history.interactor

import ephyra.domain.history.repository.HistoryRepository

class RemoveResettedHistory(
    private val historyRepository: HistoryRepository,
) {

    suspend fun await() {
        historyRepository.removeResettedHistory()
    }
}
