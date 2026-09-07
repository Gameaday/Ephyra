package ephyra.domain.extension.model

import ephyra.domain.source.model.StubSource
import eu.kanade.tachiyomi.source.Source

sealed class Extension {

    abstract val name: String
    abstract val pkgName: String
    abstract val versionName: String
    abstract val versionCode: Long
    abstract val libVersion: Double
    abstract val lang: String?
    abstract val isNsfw: Boolean

    data class Installed(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double,
        override val lang: String,
        override val isNsfw: Boolean,
        val pkgFactory: String?,
        val sources: List<Source>,
        val icon: Any?, // Changed from Drawable to Any? to be more generic in domain
        val hasUpdate: Boolean = false,
        val isObsolete: Boolean = false,
        val isShared: Boolean,
        val repoUrl: String? = null,
    ) : Extension()

    data class Available(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double,
        override val lang: String,
        override val isNsfw: Boolean,
        val sources: List<Source>,
        val apkName: String,
        val iconUrl: String,
        val repoUrl: String,
    ) : Extension() {

        data class Source(
            val id: Long,
            val lang: String,
            val name: String,
            val baseUrl: String,
        ) {
            fun toStubSource(): StubSource {
                return StubSource(
                    id = this.id,
                    lang = this.lang,
                    name = this.name,
                )
            }
        }
    }

    data class Untrusted(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double,
        val signatureHash: String,
        override val lang: String? = null,
        override val isNsfw: Boolean = false,
    ) : Extension()

    /**
     * An installed extension APK that was recognized but could not be loaded.
     * Every recognized extension must surface somewhere in the UI — an installed
     * extension must never be silently invisible.
     */
    data class Failed(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double,
        val reason: LoadFailureReason,
        val detail: String? = null,
        override val lang: String? = null,
        override val isNsfw: Boolean = false,
    ) : Extension()
}

/**
 * Why a recognized extension APK could not be loaded. Surfaced to the user in the
 * Extensions screen's "failed to load" section so a broken extension is never
 * indistinguishable from one the app simply cannot see.
 */
enum class LoadFailureReason {
    /** Package disappeared between scan and load. */
    PACKAGE_NOT_FOUND,

    /** `applicationInfo` or its `metaData` is missing. */
    NO_METADATA,

    /** APK has no `versionName`. */
    MISSING_VERSION_NAME,

    /** Declared/inferred extension-lib version is outside the supported range. */
    UNSUPPORTED_LIB_VERSION,

    /** APK is not signed. */
    UNSIGNED,

    /** NSFW content is disallowed by user preferences. */
    NSFW_NOT_ALLOWED,

    /** Dex could not be loaded through the delegate-last class loader. */
    CLASSLOADER_ERROR,

    /** Required source class metadata missing or classes failed to instantiate. */
    SOURCE_INSTANTIATION_FAILED,

    /** Any unexpected throwable during load. */
    UNEXPECTED_ERROR,
}
