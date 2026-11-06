package ephyra.domain.content.source

/**
 * The type of content source.
 *
 * `JS_SCRAPER` and `HEURISTIC` are both retired. `JS_SCRAPER` went with the JS runtime
 * (`ADR-0013`); `HEURISTIC` went because auto-discovering a site's DOM structure never worked well
 * enough to ship (`ADR-0015`). `fromString` maps both retired names onto [REMOTE_EXTENSION] so a
 * value persisted by an older build resolves to a real type instead of silently becoming the
 * default.
 *
 * The profile machinery that used to house this enum is gone (`ADR-0017`); only the persisted
 * type name survives, so the enum lives here on its own.
 */
enum class SourceType {
    /** A source provided by an extension APK, from a remote or private repository, or local. */
    REMOTE_EXTENSION,

    /** Local/network media repository (Jellyfin-style). Not yet implemented. */
    REPOSITORY,

    ;

    companion object {
        @Deprecated("Use REMOTE_EXTENSION instead", ReplaceWith("SourceType.REMOTE_EXTENSION"))
        val LEGACY_EXTENSION: SourceType get() = REMOTE_EXTENSION

        fun fromString(value: String?): SourceType = when (value?.trim()?.uppercase()) {
            // HEURISTIC is folded into REMOTE_EXTENSION rather than dropped: an install that profiled
            // a site would otherwise fall through to the default and change behaviour with no error.
            "REMOTE_EXTENSION", "LEGACY_EXTENSION", "JS_SCRAPER", "HEURISTIC" -> REMOTE_EXTENSION
            "REPOSITORY" -> REPOSITORY
            else -> REMOTE_EXTENSION
        }
    }
}
