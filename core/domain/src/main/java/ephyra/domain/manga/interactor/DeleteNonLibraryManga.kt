package ephyra.domain.manga.interactor

import ephyra.domain.manga.repository.MangaRepository

class DeleteNonLibraryManga(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(sourceIds: List<Long>, keepReadManga: Long) {
        mangaRepository.deleteNonLibraryManga(sourceIds, keepReadManga)
    }
}
