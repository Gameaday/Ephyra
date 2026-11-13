package ephyra.data.coil

import coil3.key.Keyer
import coil3.request.Options
import ephyra.domain.manga.model.MangaCover
import ephyra.domain.manga.model.Manga as DomainManga

/**
 * Cover cache keys, computed without filesystem I/O (app health audit P2, 2026-11-13).
 *
 * The key must change whenever the displayed bytes change, and that signal is
 * `coverLastModified`: every path that swaps a cover's bytes bumps it — setting a custom
 * cover (`editCover`), deleting one (`MangaCoverViewModel.deleteCustomCover`), removing
 * covers (`removeCovers`), refreshing a remote cover (`prepUpdateCover` /
 * `UpdateManga.awaitUpdateFromSource`), and migrating one (`MigrateMangaUseCase`, which now
 * bumps the revision after copying a custom cover). The keyers therefore never probe the
 * custom-cover file. The previous `exists()` check ran a synchronous filesystem stat on
 * every key computation — including memory-cache lookups served hundreds of times per grid
 * scroll — for a signal the revision already carries. The fetcher stays the authority on
 * custom covers: it re-checks the file at fetch time, on an IO dispatcher.
 *
 * Favorites key by manga identity; browse content keys by URL, so duplicate browse entries
 * share one cached cover. Keying favorites by identity also keeps two series that share a
 * cover URL from invalidating each other's revisions.
 */
class MangaKeyer : Keyer<DomainManga> {
    override fun key(data: DomainManga, options: Options): String {
        return if (data.favorite) {
            "${data.id};${data.coverLastModified}"
        } else {
            "${data.thumbnailUrl};${data.coverLastModified}"
        }
    }
}

class MangaCoverKeyer : Keyer<MangaCover> {
    override fun key(data: MangaCover, options: Options): String {
        return if (data.isMangaFavorite) {
            "${data.mangaId};${data.lastModified}"
        } else {
            "${data.url};${data.lastModified}"
        }
    }
}
