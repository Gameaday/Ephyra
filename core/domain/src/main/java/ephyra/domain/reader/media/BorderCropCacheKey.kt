package ephyra.domain.reader.media

/**
 * The cache-identity of the border-crop algorithm, and the single place it is bumped.
 *
 * **Why this is a domain contract rather than a constant that happens to live in the codec.**
 * Two halves of the crop pipeline need this value and neither may reach the other: `core:data`
 * owns the transformation that decodes, and `feature:reader` owns the Coil key deciding which
 * decoded bitmap is reused. The reader cannot import `core:data` -- that inverts the layering, and
 * `build.yml` fails the build for it. Duplicating the string instead reintroduces the defect the
 * reader-side fix was written to remove: two copies of a rule that must agree, in two modules, with
 * nothing at compile time to stop them drifting. When they drift, the algorithm is bumped in one
 * place, previously cached pages keep serving the pixels the *other* version produced, and nothing
 * anywhere reports an error. So the value lives here, in the one module both sides already depend
 * on, and each side references it rather than restating it.
 *
 * **Bump it when a change alters the resulting pixels**, and only then: it is a cache key, not a
 * build number. Both the transformation's own `cacheKey` and the reader's page key are derived from
 * this one constant, so a single edit invalidates every affected entry in both.
 */
object BorderCropCacheKey {
    /**
     * `v2` denotes the current detection algorithm. The number carries no meaning on its own; what
     * matters is that it changes whenever the produced pixels would.
     */
    const val VALUE: String = "ephyra-border-crop-v2"
}
