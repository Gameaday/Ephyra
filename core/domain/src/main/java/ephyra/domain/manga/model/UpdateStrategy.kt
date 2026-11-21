package ephyra.domain.manga.model

/**
 * Whether a library entry is fetched on every library update.
 *
 * Owned by the domain rather than the extension ABI so `Manga` does not depend on
 * `source-api`. Declaration order matches `eu.kanade.tachiyomi.source.model.UpdateStrategy`
 * exactly, because Room persists it as an ordinal and backups carry it by name.
 */
enum class UpdateStrategy {
    ALWAYS_UPDATE,
    ONLY_FETCH_ONCE,
}

/** The ABI value for [this], for handing a manga back to a source. */
internal fun UpdateStrategy.toSourceModel(): eu.kanade.tachiyomi.source.model.UpdateStrategy =
    when (this) {
        UpdateStrategy.ALWAYS_UPDATE -> eu.kanade.tachiyomi.source.model.UpdateStrategy.ALWAYS_UPDATE
        UpdateStrategy.ONLY_FETCH_ONCE -> eu.kanade.tachiyomi.source.model.UpdateStrategy.ONLY_FETCH_ONCE
    }

/** The domain value for [this], as read from a source. */
internal fun eu.kanade.tachiyomi.source.model.UpdateStrategy.toDomainModel(): UpdateStrategy =
    when (this) {
        eu.kanade.tachiyomi.source.model.UpdateStrategy.ALWAYS_UPDATE -> UpdateStrategy.ALWAYS_UPDATE
        eu.kanade.tachiyomi.source.model.UpdateStrategy.ONLY_FETCH_ONCE -> UpdateStrategy.ONLY_FETCH_ONCE
    }
