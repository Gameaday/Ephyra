package ephyra.domain.manga.interactor

data class RecommendationShareOptions(
    val includeUrl: Boolean = true,
    val includeNotes: Boolean = true,
    val includeProgress: Boolean = true,
    val includeScore: Boolean = true,
    val includeGenres: Boolean = true,
    val customNote: String? = null,
)

class FormatMangaRecommendation {

    operator fun invoke(
        title: String,
        author: String? = null,
        url: String? = null,
        notes: String? = null,
        readChapters: Int? = null,
        totalChapters: Int? = null,
        score: Double? = null,
        genres: List<String> = emptyList(),
        options: RecommendationShareOptions = RecommendationShareOptions(),
    ): String {
        return buildString {
            append("📖 ")
            append(title)
            if (!author.isNullOrBlank()) {
                append(" by ")
                append(author)
            }
            append("\n")

            if (options.includeScore && score != null && score > 0.0) {
                append("⭐ Score: ")
                append(if (score % 1.0 == 0.0) score.toInt().toString() else score.toString())
                append("/10\n")
            }

            if (options.includeProgress && readChapters != null) {
                append("📊 Progress: ")
                append(readChapters)
                if (totalChapters != null && totalChapters > 0) {
                    append(" / ")
                    append(totalChapters)
                    append(" chapters")
                    if (readChapters >= totalChapters) {
                        append(" (Completed)")
                    }
                } else {
                    append(" chapters read")
                }
                append("\n")
            }

            if (options.includeGenres && genres.isNotEmpty()) {
                append("🏷️ ")
                append(genres.take(5).joinToString(", "))
                append("\n")
            }

            val effectiveNote = if (!options.customNote.isNullOrBlank()) {
                options.customNote.trim()
            } else if (options.includeNotes && !notes.isNullOrBlank()) {
                notes.trim()
            } else {
                null
            }

            if (!effectiveNote.isNullOrBlank()) {
                append("\n💬 \"")
                append(effectiveNote)
                append("\"\n")
            }

            if (options.includeUrl && !url.isNullOrBlank()) {
                append("\n🔗 ")
                append(url)
            }
        }.trim()
    }
}
