package ephyra.core.common.util.system

/**
 * Lossless image format for derived images (splits, merges, cover saves).
 */
enum class ImageFormat(val extension: String, val mime: String) {
    WEBP("webp", "image/webp"),
    PNG("png", "image/png"),
}
