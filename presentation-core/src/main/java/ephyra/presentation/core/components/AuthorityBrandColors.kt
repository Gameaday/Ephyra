package ephyra.presentation.core.components

import androidx.compose.ui.graphics.Color

/**
 * Brand colours for the authorities a series can be linked to (MyAnimeList, AniList, MangaUpdates,
 * Jellyfin).
 *
 * **These are deliberately not theme tokens.** A theme token is a colour the user can re-skin; a
 * brand colour is an external identity that must render the same in every palette, light or dark,
 * Monochrome or Monet. Routing them through `MaterialTheme.colorScheme` would let a theme repaint
 * AniList's blue, which is exactly wrong — the badge's whole job is to say *which* service this is.
 *
 * They live here, in `presentation-core`, because two features need them and neither may depend on
 * the other: `feature:library` renders them in its grid badges and `feature:manga` renders them in
 * the series header and the tracking sheet. Before this file, the same four-entry map was defined
 * **twice** — byte-identical copies in `LibraryBadges.kt` and `MangaInfoHeader.kt` — plus a third
 * copy of the Jellyfin value in `TrackInfoDialogHome.kt`. Three definitions of one fact is how a
 * brand colour silently changes in one place and not the others.
 *
 * A brand change (a service rebrands its logo colour) is a one-line edit here.
 */
object AuthorityBrandColors {

    /** MyAnimeList blue. */
    val MY_ANIME_LIST = Color(0xFF2E51A2)

    /** AniList blue. */
    val ANI_LIST = Color(0xFF02A9FF)

    /** MangaUpdates orange. */
    val MANGA_UPDATES = Color(0xFFFF6740)

    /** Jellyfin blue (primary). */
    val JELLYFIN = Color(0xFF00A4DC)

    /** Jellyfin purple, the first stop of its gradient. */
    val JELLYFIN_GRADIENT_START = Color(0xFFAA5CC3)

    /**
     * The solid brand colour for an authority, keyed by the same `canonicalId` prefixes the
     * duplicate maps used (`al`, `mal`, `mu`, `jf`), or null when the id is unknown.
     */
    fun forAuthority(canonicalId: String?): Color? =
        when (canonicalId?.substringBefore(':')?.lowercase()) {
            "al" -> ANI_LIST
            "mal" -> MY_ANIME_LIST
            "mu" -> MANGA_UPDATES
            "jf" -> JELLYFIN
            else -> null
        }

    /**
     * Gradient stops for authorities whose identity is multi-colour, or null when the authority has
     * a single brand colour (callers should fall back to [forAuthority]).
     */
    fun gradientFor(canonicalId: String?): List<Color>? =
        when (canonicalId?.substringBefore(':')?.lowercase()) {
            "jf" -> listOf(JELLYFIN_GRADIENT_START, JELLYFIN)
            else -> null
        }
}
