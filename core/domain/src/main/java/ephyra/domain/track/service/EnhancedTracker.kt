package ephyra.domain.track.service

import ephyra.domain.manga.model.Manga
import ephyra.domain.track.model.Track
import ephyra.domain.track.model.TrackSearch
import eu.kanade.tachiyomi.source.Source

/**
 * A tracker that will never prompt the user to manually bind an entry.
 */
interface EnhancedTracker {

    val id: Long

    fun accept(source: Source): Boolean

    fun getAcceptedSources(): List<String>

    fun loginNoop()

    suspend fun match(manga: Manga): TrackSearch?

    fun isTrackFrom(track: Track, manga: Manga, source: Source?): Boolean

    fun migrateTrack(track: Track, manga: Manga, newSource: Source): Track?
}
