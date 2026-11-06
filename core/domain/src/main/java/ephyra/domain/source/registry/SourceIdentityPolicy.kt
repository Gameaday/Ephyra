package ephyra.domain.source.registry

/**
 * The one rule for turning a source origin into the `Long` id every component keys on.
 *
 * `SRC-011` (`ADR-0012`): before this existed, `DynamicHttpSource` and `GetAvailableSources` each
 * hashed whatever spelling of a URL they happened to see, so one trailing slash was enough to
 * orphan every entry written against the old id. Identity is now derived once, here, from the
 * canonical origin — different spellings of the same source produce the same id by construction.
 *
 * [String.hashCode] is fixed by the Java and Kotlin specifications, which is what makes the
 * migration property below true rather than accidental.
 */
object SourceIdentityPolicy {

    /**
     * The identity of a source addressed by [rawOrigin].
     *
     * **Migration property (deliberate):** a spelling that is already canonical keeps the id
     * every pre-`SRC-011` component computed from it, because the canonical origin equals the raw
     * spelling, so upgrading orphans nothing. A *non*-canonical stored spelling gets a new id,
     * and the difference is exactly what [SourceRegistry.resolveEntry] reports as
     * [SourceEntryResolution.SpellingChanged] instead of an unexplained absence.
     *
     * An origin that cannot be canonicalized falls back to the legacy raw hash rather than
     * inventing a new identity: a stored value this rule cannot name must stay reachable by the
     * id it already has.
     */
    fun identityOf(rawOrigin: String): Long =
        SourceOriginPolicy.canonicalize(rawOrigin)?.let { it.value.hashCode().toLong() }
            ?: rawOrigin.hashCode().toLong()

    /**
     * The pre-`SRC-011` identity rule: hash whatever spelling was stored.
     *
     * **Not an identity rule anymore.** Used only to detect the past: a registration carries the
     * id its spelling would have had under the old rule ([SourceRegistration.legacyId]) so an
     * entry written before `SRC-011` resolves as a spelling change with a named owner instead of
     * as an orphan. Never use this to mint identity.
     */
    fun legacyIdentityOf(raw: String): Long = raw.hashCode().toLong()
}
