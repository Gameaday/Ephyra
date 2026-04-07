package ephyra.domain.track.model

import java.io.Serializable

data class TrackSearch(
    val remote_id: Long,
    val title: String,
    val tracker_id: Long = 0L,
    val tracking_url: String = "",
    val summary: String = "",
    val authors: List<String> = emptyList(),
    val artists: List<String> = emptyList(),
    val cover_url: String = "",
    val publishing_type: String = "",
    val publishing_status: String = "",
    val start_date: String = "",
    val alternative_titles: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val total_chapters: Long = 0,
    var private: Boolean = false,
) : Serializable
