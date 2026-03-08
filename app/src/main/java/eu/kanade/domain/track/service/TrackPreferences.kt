package eu.kanade.domain.track.service

import eu.kanade.domain.track.model.AutoTrackState
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

class TrackPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun trackUsername(tracker: Tracker) = preferenceStore.getString(
        Preference.privateKey("pref_mangasync_username_${tracker.id}"),
        "",
    )

    fun trackPassword(tracker: Tracker) = preferenceStore.getString(
        Preference.privateKey("pref_mangasync_password_${tracker.id}"),
        "",
    )

    fun trackAuthExpired(tracker: Tracker) = preferenceStore.getBoolean(
        Preference.privateKey("pref_tracker_auth_expired_${tracker.id}"),
        false,
    )

    fun setCredentials(tracker: Tracker, username: String, password: String) {
        trackUsername(tracker).set(username)
        trackPassword(tracker).set(password)
        trackAuthExpired(tracker).set(false)
    }

    fun trackToken(tracker: Tracker) = preferenceStore.getString(Preference.privateKey("track_token_${tracker.id}"), "")

    /**
     * User-preferred tracker for authority matching.
     *
     * Value is a tracker ID (1=MAL, 2=AniList, 7=MangaUpdates) or 0 for "Auto"
     * (the system picks the best available tracker automatically).
     *
     * The matching engine validates at query time that the chosen tracker is still
     * available (logged in or supports public search). If not, it falls back to
     * the automatic selection.
     */
    fun preferredAuthorityTracker() = preferenceStore.getLong(
        "pref_preferred_authority_tracker",
        AUTHORITY_TRACKER_AUTO,
    )

    fun anilistScoreType() = preferenceStore.getString("anilist_score_type", Anilist.POINT_10)

    fun autoUpdateTrack() = preferenceStore.getBoolean("pref_auto_update_manga_sync_key", true)

    fun autoUpdateTrackOnMarkRead() = preferenceStore.getEnum(
        "pref_auto_update_manga_on_mark_read",
        AutoTrackState.ALWAYS,
    )

    companion object {
        /** Sentinel value: let the system pick the best available tracker automatically. */
        const val AUTHORITY_TRACKER_AUTO = 0L
    }
}
