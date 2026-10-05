package ephyra.domain.reader.media

import ephyra.core.common.util.system.ImageUtil

/**
 * Maps the platform image sniffer's type onto the reader's format vocabulary.
 *
 * Lives here rather than beside `ImageUtil` because `core:common` cannot see `core:domain`, and the
 * mapping's purpose is entirely the reader's: `PageImageFormat` is what the render policy speaks.
 *
 * [ImageUtil.ImageType.HEIF] and [ImageUtil.ImageType.AVIF] have no dedicated case in
 * [PageImageFormat] and map to [PageImageFormat.UNSUPPORTED], which takes the whole-image path --
 * the same outcome the old inline check produced by omission.
 */
fun ImageUtil.ImageType.toPageImageFormat(): PageImageFormat = when (this) {
    ImageUtil.ImageType.JPEG -> PageImageFormat.JPEG
    ImageUtil.ImageType.PNG -> PageImageFormat.PNG
    ImageUtil.ImageType.WEBP -> PageImageFormat.WEBP
    ImageUtil.ImageType.GIF -> PageImageFormat.GIF
    ImageUtil.ImageType.JXL -> PageImageFormat.JXL
    ImageUtil.ImageType.AVIF, ImageUtil.ImageType.HEIF -> PageImageFormat.UNSUPPORTED
}
