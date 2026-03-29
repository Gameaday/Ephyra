package ephyra.data.updates

import ephyra.data.room.views.UpdatesView
import ephyra.domain.manga.model.MangaCover
import ephyra.domain.updates.model.UpdatesWithRelations

object UpdatesMapper {
    fun mapUpdatesWithRelations(view: UpdatesView): UpdatesWithRelations = UpdatesWithRelations(
        mangaId = view.mangaId,
        mangaTitle = view.mangaTitle,
        chapterId = view.chapterId,
        chapterName = view.chapterName,
        scanlator = view.scanlator,
        chapterUrl = view.chapterUrl,
        read = view.read,
        bookmark = view.bookmark,
        lastPageRead = view.last_page_read.toLong(),
        sourceId = view.source,
        dateFetch = view.datefetch,
        coverData = MangaCover(
            mangaId = view.mangaId,
            sourceId = view.source,
            isMangaFavorite = view.favorite,
            url = view.thumbnailUrl,
            lastModified = view.coverLastModified,
        ),
    )
}
