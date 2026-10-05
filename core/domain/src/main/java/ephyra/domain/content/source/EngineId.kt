package ephyra.domain.content.source

/**
 * Identifies a content engine: the thing that can actually fetch content.
 *
 * **Why this is a value class and not an enum.** An enum would make adding Jellyfin require editing a
 * shared file — which is precisely the coupling `ADR-0015` removed for source types, reappearing in
 * a new place. Here an engine declares its own id as data, so the registry is additive: a new engine
 * is a new class and one binding, and nothing else in the tree learns that engines exist.
 *
 * **Why it is stable.** The value is persisted (a `ContentLocator` carries one, and locators are
 * stored). So these strings must not be renamed casually — a change here is a migration, not a
 * rename. That is the same constraint `SourceType.fromString` existed to soften; keeping ids opaque
 * and few keeps the surface small.
 */
@JvmInline
value class EngineId(val value: String) {

    init {
        require(value.isNotBlank()) { "An engine id cannot be blank" }
    }

    override fun toString(): String = value

    companion object {
        /** Sources provided by an extension APK, from a remote or private repository, or local. */
        val EXTENSION_APK = EngineId("extension-apk")

        /** Archives on the device — CBZ/CBR/EPUB folders. */
        val LOCAL_ARCHIVE = EngineId("local-archive")

        /** OPDS catalogues. */
        val OPDS = EngineId("opds")

        /** A network media repository. Reserved for Jellyfin; nothing is bound to it yet. */
        val REPOSITORY = EngineId("repository")
    }
}
