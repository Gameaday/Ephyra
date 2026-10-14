package ephyra.domain.chapter.service

import ephyra.domain.manga.interactor.TitleNormalizer
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.util.Locale

/**
 * WS8 of the Mihon 0.21.1 adoption plan: chapter-number and title normalization are *data*
 * operations, so the device locale must not change their result. Upstream's 0.21.1 fix was a
 * chapter refresh that failed under a non-ASCII-digit app locale because `DecimalFormat` used
 * locale-default symbols; Ephyra parses with `toDouble()` (always ASCII, locale-independent) and
 * normalizes with an explicit `Locale.ROOT`, and these tests pin that by running the same inputs
 * under an Arabic (Arabic-Indic digit) and a Turkish (dotted/dotless I) default locale.
 *
 * `SAME_THREAD` because the test mutates the JVM default locale; it is restored in a `finally`.
 */
@Execution(ExecutionMode.SAME_THREAD)
class LocaleIndependenceTest {

    private val chapterCases = listOf(
        Triple("Mokushiroku Alice", "Mokushiroku Alice Vol.1 Ch.4: Misrepresentation", 4.0),
        Triple("Mokushiroku Alice", "Mokushiroku Alice Vol.1 Ch.4.4: Misrepresentation", 4.4),
        Triple("Bleach", "Bleach 567 Down With Snowwhite", 567.0),
        Triple("Bleach", "Bleach 567.4 Down With Snowwhite", 567.4),
        Triple("One Piece", "One Piece 1000", 1000.0),
    )

    private fun parseAll(): List<Double> =
        chapterCases.map { (title, name, _) -> ChapterRecognition.parseChapterNumber(title, name) }

    @Test
    fun `chapter parsing is locale independent`() {
        val expected = withLocale(Locale.ROOT) { parseAll() }
        expected shouldBe chapterCases.map { it.third }

        withLocale(Locale.forLanguageTag("ar-EG")) { parseAll() shouldBe expected }
        withLocale(Locale.forLanguageTag("tr")) { parseAll() shouldBe expected }
    }

    @Test
    fun `title normalization is locale independent`() {
        // "İstanbul" and "I I I" are the Turkish dotted/dotless-I cases that a locale-default
        // lowercase() folds differently from Locale.ROOT.
        val titles = listOf("Attack on Titan!", "Re:Zero", "İstanbul", "I I I", "Fate/stay night")

        val expectedEquality = withLocale(Locale.ROOT) { titles.associateWith(TitleNormalizer::forEquality) }
        val expectedMatching = withLocale(Locale.ROOT) { titles.associateWith(TitleNormalizer::forMatching) }

        listOf("ar-EG", "tr").forEach { tag ->
            withLocale(Locale.forLanguageTag(tag)) {
                titles.forEach { title ->
                    TitleNormalizer.forEquality(title) shouldBe expectedEquality.getValue(title)
                    TitleNormalizer.forMatching(title) shouldBe expectedMatching.getValue(title)
                }
            }
        }
    }

    private fun <T> withLocale(locale: Locale, block: () -> T): T {
        val previous = Locale.getDefault()
        return try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }
}
