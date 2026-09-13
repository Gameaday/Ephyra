package ephyra.domain.content.model

/**
 * Sealed abstraction representing a single page of content rendered in reading viewports.
 *
 * Media-agnostic page contract supporting:
 * - [ImagePage] for Manga, Comics, and Graphic Novels
 * - [TextPage] for Novels, Books, and EPUB text sections
 * - [StreamPage] for Video, Animation, and Audio segments
 */
sealed interface ContentPage {
    val index: Int

    data class ImagePage(
        override val index: Int,
        val imageUrl: String? = null,
        val imageBytes: ByteArray? = null,
        val headers: Map<String, String> = emptyMap(),
    ) : ContentPage {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ImagePage) return false
            if (index != other.index) return false
            if (imageUrl != other.imageUrl) return false
            if (imageBytes != null) {
                if (other.imageBytes == null || !imageBytes.contentEquals(other.imageBytes)) return false
            } else if (other.imageBytes != null) {
                return false
            }
            return headers == other.headers
        }

        override fun hashCode(): Int {
            var result = index
            result = 31 * result + (imageUrl?.hashCode() ?: 0)
            result = 31 * result + (imageBytes?.contentHashCode() ?: 0)
            result = 31 * result + headers.hashCode()
            return result
        }
    }

    data class TextPage(
        override val index: Int,
        val title: String? = null,
        val content: String,
        val format: TextFormat = TextFormat.HTML,
    ) : ContentPage {
        enum class TextFormat { PLAIN, HTML, MARKDOWN }
    }

    data class StreamPage(
        override val index: Int,
        val streamUrl: String,
        val quality: String? = null,
        val headers: Map<String, String> = emptyMap(),
    ) : ContentPage
}
