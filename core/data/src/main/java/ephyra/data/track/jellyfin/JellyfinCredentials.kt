/**
 * Shared read-only view of the Jellyfin connection credentials for the content-source stack.
 *
 * **Why this exists.** The tracker's credentials live in tracker preference internals
 * ([TrackPreferences] keyed by tracker id, plus the Jellyfin-specific server preferences set at
 * login). The Phase 3 content source (browse/search, see `ADR-0015` follow-up) needs the same
 * values, but reaching into tracker internals from the sourcing package would couple two pillars
 * to each other's storage format — a change to how the tracker stores its token would silently
 * break browsing. This interface is the single seam: the sourcing code depends on it, the
 * preference-backed implementation is the only place that knows where the bytes live, and the
 * existing tracker paths are untouched.
 *
 * All accessors are synchronous because the underlying values are read from local preferences;
 * network access happens in the callers.
 */
package ephyra.data.track.jellyfin

import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.track.service.TrackPreferences
import ephyra.domain.track.service.TrackerManager

interface JellyfinCredentials {
    /** Base URL of the configured Jellyfin server, e.g. `https://jellyfin.lan`. Blank when unset. */
    fun serverUrl(): String

    /** The authenticated user's id, required by every user-scoped Jellyfin endpoint. */
    fun userId(): String

    /** Human-readable server name, captured from SystemInfo at tracker login time. */
    fun serverName(): String

    /** The preferred library (Jellyfin "view") id, or blank to browse all libraries. */
    fun libraryId(): String

    /**
     * The access token issued by `AuthenticateByName`.
     *
     * The tracker stores it in the tracker password slot ([TrackPreferences.trackPassword]); the
     * id-based overload is what makes it readable here without constructing a [Tracker].
     */
    fun accessToken(): String

    /** True when the server, user, and token are all present — the minimum for any API call. */
    fun isConfigured(): Boolean
}

/**
 * Preference-backed [JellyfinCredentials].
 *
 * Reads the same keys the Jellyfin tracker writes during login, so a server configured through
 * the tracker settings is immediately usable as a content source with no second setup flow.
 */
class PreferenceJellyfinCredentials(
    private val trackPreferences: TrackPreferences,
    private val libraryPreferences: LibraryPreferences,
) : JellyfinCredentials {

    // `getSync()` rather than the suspend `get()`: these accessors are called from an OkHttp
    // interceptor on a network thread (mirroring `JellyfinInterceptor`'s token read) and from
    // synchronous source-availability checks, where a suspend read would be impossible.
    override fun serverUrl(): String =
        trackPreferences.jellyfinServerUrl().getSync().trim().removeSuffix("/")

    override fun userId(): String = trackPreferences.jellyfinUserId().getSync()

    override fun serverName(): String = trackPreferences.jellyfinServerName().getSync()

    override fun libraryId(): String = libraryPreferences.jellyfinLibraryId().getSync().trim()

    override fun accessToken(): String =
        trackPreferences.trackPassword(TrackerManager.JELLYFIN).getSync()

    override fun isConfigured(): Boolean =
        serverUrl().isNotBlank() && userId().isNotBlank() && accessToken().isNotBlank()
}
