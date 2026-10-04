package ephyra.data.coil

import coil3.key.Keyer
import coil3.request.Options
import ephyra.data.cache.CoverCache
import ephyra.domain.manga.model.MangaCover
import ephyra.domain.manga.model.Manga as DomainManga

class MangaKeyer(
    private val coverCache: CoverCache,
) : Keyer<DomainManga> {
    override fun key(data: DomainManga, options: Options): String {
        return if (coverCache.hasCustomCover(data.id)) {
            "${data.id};${data.coverLastModified}"
        } else {
            "${data.thumbnailUrl};${data.coverLastModified}"
        }
    }
}

class MangaCoverKeyer(
    private val coverCache: CoverCache,
) : Keyer<MangaCover> {
    override fun key(data: MangaCover, options: Options): String {
        return if (coverCache.hasCustomCover(data.mangaId)) {
            "${data.mangaId};${data.lastModified}"
        } else {
            "${data.url};${data.lastModified}"
        }
    }
}
