package eu.kanade.tachiyomi

import ephyra.core.common.util.system.ImageUtil

/**
 * Host application info exposed to extensions.
 * Follows the canonical Tachiyomi/Mihon extension-lib ABI contract.
 */
@Suppress("UNUSED")
object AppInfo {
    @Volatile
    private var versionCode: Int = 1

    @Volatile
    private var versionName: String = "1.0.0"

    /**
     * Initializes the host application info.
     * Called by the host application on startup.
     */
    fun init(versionCode: Int, versionName: String) {
        this.versionCode = versionCode
        this.versionName = versionName
    }

    /**
     * Version code of the host application. May be useful for sharing as User-Agent information.
     * Note that this value differs between forks so logic should not rely on it.
     *
     * @since extension-lib 1.3
     */
    @JvmStatic
    fun getVersionCode(): Int = versionCode

    /**
     * Version name of the host application. May be useful for sharing as User-Agent information.
     * Note that this value differs between forks so logic should not rely on it.
     *
     * @since extension-lib 1.3
     */
    @JvmStatic
    fun getVersionName(): String = versionName

    /**
     * A list of supported image MIME types by the reader.
     * e.g. ["image/jpeg", "image/png", ...]
     *
     * @since extension-lib 1.5
     */
    @JvmStatic
    fun getSupportedImageMimeTypes(): List<String> = ImageUtil.ImageType.entries.map { it.mime }
}
