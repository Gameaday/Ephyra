package ephyra.app

/**
 * Used by extensions and host app.
 * Delegates to canonical Tachiyomi extension-lib ABI [eu.kanade.tachiyomi.AppInfo].
 */
@Suppress("UNUSED")
object AppInfo {
    /**
     * Version code of the host application. May be useful for sharing as User-Agent information.
     * Note that this value differs between forks so logic should not rely on it.
     *
     * @since extension-lib 1.3
     */
    @JvmStatic
    fun getVersionCode(): Int = eu.kanade.tachiyomi.AppInfo.getVersionCode()

    /**
     * Version name of the host application. May be useful for sharing as User-Agent information.
     * Note that this value differs between forks so logic should not rely on it.
     *
     * @since extension-lib 1.3
     */
    @JvmStatic
    fun getVersionName(): String = eu.kanade.tachiyomi.AppInfo.getVersionName()

    /**
     * A list of supported image MIME types by the reader.
     * e.g. ["image/jpeg", "image/png", ...]
     *
     * @since extension-lib 1.5
     */
    @JvmStatic
    fun getSupportedImageMimeTypes(): List<String> = eu.kanade.tachiyomi.AppInfo.getSupportedImageMimeTypes()
}
