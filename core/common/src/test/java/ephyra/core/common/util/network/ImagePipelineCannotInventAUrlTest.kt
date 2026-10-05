package ephyra.core.common.util.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Can our own pipeline *produce* the string that was reported, or only carry it?
 *
 * **Why this file exists.** The device reports a page address that is not a URL at all:
 *
 * ```
 * https://cmdxd98sb0x3yprd.mangadex.network
 * ,https://api.mangadex.org/at-home/server /5c525e2a-ffb5-49bd-91f0-26f8dca73a10
 * ,1790975751218
 * ```
 *
 * Three comma-separated parts: a server, an API URL, and a Unix-millis timestamp. Work on this has
 * repeatedly proposed a mechanism in *our* code that would create such a string — most recently the
 * control-character repair, which was proposed and then falsified, and the write-back to
 * `page.imageUrl`, which was proposed on the strength of the retry returning a byte-identical value.
 * Each was plausible; none survived a probe. So this file settles the direction in one place instead
 * of by argument each time.
 *
 * **The claim under test.** No operation in [ImageUrlPolicy] can introduce a comma, a timestamp, or a
 * second URL. The transformations are: trim, remove control characters, decode `&amp;`, and — for a
 * relative value — join onto a base with a single `/`. None can synthesise a third field.
 *
 * So a value arriving in this shape was **handed to us already formed**, and the defect is upstream of
 * this function. That is a stronger and more useful statement than "we think so", and it is the
 * difference between looking at our code and looking at the source that produced it.
 */
class ImagePipelineCannotInventAUrlTest {

    private val base = "https://mangadex.org"
    private val reported = "https://cmdxd98sb0x3yprd.mangadex.network," +
        "https://api.mangadex.org/at-home/server /5c525e2a-ffb5-49bd-91f0-26f8dca73a10," +
        "1790975751218"

    @Test
    fun `the reported value is carried through unchanged rather than rebuilt`() {
        // If any repair normalised, joined, or re-encoded this, the output would differ. It does not:
        // the value arrives whole and leaves whole, which is the definition of "carried, not made".
        assertEquals(reported, ImageUrlPolicy.resolve(reported, base))
    }

    @Test
    fun `no repair introduces a comma that was not already there`() {
        val withoutComma = reported.replace(",", "")
        val resolved = ImageUrlPolicy.resolve(withoutComma, base)

        assertTrue(
            !resolved.contains(','),
            "resolution added a comma to a value that had none: \"$resolved\"",
        )
    }

    @Test
    fun `no repair introduces a timestamp-shaped suffix`() {
        val bare = "https://cdn.example.com/i.jpg"
        val resolved = ImageUrlPolicy.resolve(bare, base)

        assertEquals(bare, resolved)
        assertTrue(
            !Regex("\\d{13}").containsMatchIn(resolved),
            "resolution appended something timestamp-shaped: \"$resolved\"",
        )
    }

    @Test
    fun `joining a relative value onto a base adds a slash and nothing else`() {
        // The one transformation that adds structure, pinned exactly. It prepends the base and one
        // separator; it cannot append a field.
        assertEquals("https://mangadex.org/data/1.jpg", ImageUrlPolicy.resolve("/data/1.jpg", base))
    }

    @Test
    fun `the reported value is refused, so no request is ever built from it`() {
        val thrown = assertThrows(MalformedImageUrlException::class.java) {
            ImageUrlPolicy.requireUsable(reported)
        }

        assertEquals(reported, thrown.url)
        assertNotNull(thrown.reason)
    }
}
