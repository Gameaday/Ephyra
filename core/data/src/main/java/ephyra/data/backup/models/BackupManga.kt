package ephyra.data.backup.models

import ephyra.domain.content.model.ContentType
import ephyra.domain.manga.model.Manga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Suppress("DEPRECATION")
@Serializable
data class BackupManga(
    @ProtoNumber(1) var source: Long,
    @ProtoNumber(2) var url: String,
    @ProtoNumber(3) var title: String = "",
    @ProtoNumber(4) var artist: String? = null,
    @ProtoNumber(5) var author: String? = null,
    @ProtoNumber(6) var description: String? = null,
    @ProtoNumber(7) var genre: List<String> = emptyList(),
    @ProtoNumber(8) var status: Int = 0,
    @ProtoNumber(9) var thumbnailUrl: String? = null,
    @ProtoNumber(13) var dateAdded: Long = 0,
    @ProtoNumber(14) var viewer: Int = 0,
    @ProtoNumber(16) var chapters: List<BackupChapter> = emptyList(),
    @ProtoNumber(17) var categories: List<Long> = emptyList(),
    @ProtoNumber(18) var tracking: List<BackupTracking> = emptyList(),
    @ProtoNumber(100) var favorite: Boolean = true,
    @ProtoNumber(101) var chapterFlags: Int = 0,
    @ProtoNumber(103) var viewer_flags: Int? = null,
    @ProtoNumber(104) var history: List<BackupHistory> = emptyList(),
    @ProtoNumber(105) var updateStrategy: UpdateStrategy = UpdateStrategy.ALWAYS_UPDATE,
    @ProtoNumber(106) var lastModifiedAt: Long = 0,
    @ProtoNumber(107) var favoriteModifiedAt: Long? = null,
    @ProtoNumber(108) var excludedScanlators: List<String> = emptyList(),
    @ProtoNumber(109) var version: Long = 0,
    @ProtoNumber(110) var notes: String = "",
    @ProtoNumber(111) var initialized: Boolean = false,
    @ProtoNumber(112) var metadataSource: Long? = null,
    @ProtoNumber(113) var metadataUrl: String? = null,
    @ProtoNumber(114) var alternativeTitles: List<String> = emptyList(),
    @ProtoNumber(115) var canonicalId: String? = null,
    @ProtoNumber(116) var sourceStatus: Int = 0,
    @ProtoNumber(117) var deadSince: Long? = null,
    @ProtoNumber(118) var contentType: Int = 0,
    @ProtoNumber(119) var lockedFields: Long = 0L,
) {
    fun getMangaImpl(): Manga {
        return Manga.create().copy(
            url = this@BackupManga.url,
            title = this@BackupManga.title,
            artist = this@BackupManga.artist,
            author = this@BackupManga.author,
            description = this@BackupManga.description,
            genre = this@BackupManga.genre,
            status = this@BackupManga.status.toLong(),
            thumbnailUrl = this@BackupManga.thumbnailUrl,
            favorite = this@BackupManga.favorite,
            source = this@BackupManga.source,
            dateAdded = this@BackupManga.dateAdded,
            viewerFlags = (this@BackupManga.viewer_flags ?: this@BackupManga.viewer).toLong(),
            chapterFlags = this@BackupManga.chapterFlags.toLong(),
            updateStrategy = this@BackupManga.updateStrategy,
            lastModifiedAt = this@BackupManga.lastModifiedAt,
            favoriteModifiedAt = this@BackupManga.favoriteModifiedAt,
            version = this@BackupManga.version,
            notes = this@BackupManga.notes,
            initialized = this@BackupManga.initialized,
            metadataSource = this@BackupManga.metadataSource,
            metadataUrl = this@BackupManga.metadataUrl,
            alternativeTitles = this@BackupManga.alternativeTitles,
            canonicalId = this@BackupManga.canonicalId,
            sourceStatus = this@BackupManga.sourceStatus,
            deadSince = this@BackupManga.deadSince,
            contentType = ContentType.fromValue(this@BackupManga.contentType),
            lockedFields = this@BackupManga.lockedFields,
        )
    }
}
