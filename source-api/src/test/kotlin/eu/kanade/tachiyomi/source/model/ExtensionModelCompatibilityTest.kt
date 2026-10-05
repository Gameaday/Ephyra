package eu.kanade.tachiyomi.source.model

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The 1.7 model fields exist so an extension can assign them.
 *
 * The failure these prevent is `NoSuchFieldError`, which arrives *after* the source has loaded and
 * while the user is browsing it. Asserting the property is assignable is the whole test — an
 * interface property that compiles here is one the JVM signature exists for at runtime.
 */
class ExtensionModelCompatibilityTest {

    @Test
    fun `a 1_7-era extension can assign the manga fields upstream declares`() {
        val manga = SManga.create().apply {
            genres = listOf("Action", "Fantasy")
            banner = "https://cdn.example.com/banner.jpg"
            altTitles = listOf("Official Name")
            language = "ja"
            score = 87
            readingMode = SManga.ReadingMode.RIGHT_TO_LEFT
            contentRating = SManga.ContentRating.ADULT
        }

        assertEquals(listOf("Action", "Fantasy"), manga.genres)
        assertEquals("https://cdn.example.com/banner.jpg", manga.banner)
        assertEquals(listOf("Official Name"), manga.altTitles)
        assertEquals("ja", manga.language)
        assertEquals(87, manga.score)
        assertEquals(SManga.ReadingMode.RIGHT_TO_LEFT, manga.readingMode)
        assertEquals(SManga.ContentRating.ADULT, manga.contentRating)
    }

    @Test
    fun `a 1_7-era extension can assign the chapter fields upstream declares`() {
        val chapter = SChapter.create().apply {
            number = "12.5a"
            volume = "3"
            scanlators = listOf("Group A")
            language = "en"
            locked = true
            note = "Available Friday"
        }

        assertEquals("12.5a", chapter.number)
        assertEquals("3", chapter.volume)
        assertEquals(listOf("Group A"), chapter.scanlators)
        assertEquals("en", chapter.language)
        assertTrue(chapter.locked)
        assertEquals("Available Friday", chapter.note)
    }

    /**
     * Upstream does not mirror the deprecated field into the current one, so a reader must consult
     * both. A source compiled against 1.4 fills only `genre`; one compiled against 1.7 fills only
     * `genres`. Reading just one makes the other's genres silently vanish.
     */
    @Test
    fun `genres are read from whichever field the source filled`() {
        val modern = SManga.create().apply { genres = listOf("Action", "Fantasy") }
        assertEquals(listOf("Action", "Fantasy"), modern.effectiveGenres())

        @Suppress("DEPRECATION")
        val legacy = SManga.create().apply { genre = "Action, Fantasy" }
        assertEquals(listOf("Action", "Fantasy"), legacy.effectiveGenres())
    }

    /** `number` is a String upstream precisely so `"12.5a"` survives; a Float cannot hold it. */
    @Test
    fun `a chapter number with a suffix survives the string field`() {
        val chapter = SChapter.create().apply { number = "12.5a" }
        assertEquals("12.5a", chapter.effectiveNumber())

        @Suppress("DEPRECATION")
        val legacy = SChapter.create().apply { chapter_number = 12.5f }
        assertEquals("12.5", legacy.effectiveNumber())
    }

    /** Unset must not read as a number, or sorting and filtering would treat it as one. */
    @Test
    fun `an unset number reads as absent rather than as zero`() {
        val chapter = SChapter.create()
        assertNull(chapter.effectiveNumber())
        assertNull(chapter.number)
        assertFalse(chapter.locked)
    }

    /** Content rating defaults to SAFE, because a blur filter must not default to "unknown". */
    @Test
    fun `content rating defaults to SAFE`() {
        assertEquals(SManga.ContentRating.SAFE, SManga.create().contentRating)
    }

    /** Copying must carry the new fields, or a round trip silently drops them. */
    @Test
    fun `copy carries every field including the new ones`() {
        val original = SManga.create().apply {
            url = "/manga/1"
            title = "Title"
            genres = listOf("Action")
            altTitles = listOf("Alt")
            banner = "https://cdn.example.com/b.jpg"
            score = 42
            contentRating = SManga.ContentRating.SUGGESTIVE
            readingMode = SManga.ReadingMode.LONG_STRIP
        }
        val copy = original.copy()
        assertEquals(original.genres, copy.genres)
        assertEquals(original.altTitles, copy.altTitles)
        assertEquals(original.banner, copy.banner)
        assertEquals(original.score, copy.score)
        assertEquals(original.contentRating, copy.contentRating)
        assertEquals(original.readingMode, copy.readingMode)
    }

    /**
     * The deferred update shape upstream declares.
     *
     * An extension compiled against a `tachiyomix` with these constructors calls them; with only the
     * eager form it cannot link, and the failure surfaces as a source that will not load rather than as
     * a missing method.
     */
    @Test
    fun `the eager update constructor still reads back as both parts`() = runBlocking {
        val manga = SManga.create().apply { url = "/manga/1" }
        val update = SMangaUpdate(manga, listOf(SChapter.create()))
        assertEquals(manga, update.manga())
        assertEquals(1, update.chapters().size)
    }

    /**
     * The case the shape exists for: details and chapters from separate endpoints.
     *
     * The point is that the chapters lambda is *not* run until it is awaited — that is what lets the
     * caller show details before the chapter list arrives.
     */
    @Test
    fun `a deferred chapters fetch does not run until it is awaited`() = runBlocking {
        var fetched = false
        val manga = SManga.create().apply { url = "/manga/1" }
        val update = SMangaUpdate(manga) {
            fetched = true
            listOf(SChapter.create())
        }

        assertFalse(fetched, "constructing the update must not perform the chapter fetch")
        assertEquals(manga, update.manga())

        assertFalse(fetched, "reading only the manga must not perform the chapter fetch")
        update.chapters()
        assertTrue(fetched, "awaiting the chapters must run the fetch")
    }

    @Test
    fun `an entirely deferred update resolves both parts on await`() = runBlocking {
        val manga = SManga.create().apply { url = "/manga/1" }
        val update = SMangaUpdate({ manga }, { listOf(SChapter.create()) })
        assertEquals(manga, update.manga())
        assertEquals(1, update.chapters().size)
    }

    /**
     * The blocking accessors must exist at the *JVM* level, not merely in source.
     *
     * Upstream declares these `DeprecationLevel.HIDDEN` with `@JvmName("getManga")`. HIDDEN strips
     * them from Kotlin source while leaving the method in the compiled API — the mechanism by which
     * an already-compiled extension keeps working after it migrates to the suspend properties.
     *
     * Removing them changed the primary constructor's property return type from `SManga` to
     * `suspend () -> SManga`, so `getManga()` changed signature with it. Every extension failed with
     * `NoSuchMethodError: No virtual method getManga(...)` as soon as it tried to update chapters.
     *
     * This asserts by reflection, because a source-level reference would not catch a missing
     * `@JvmName` — the method would compile as `getMangaLegacy` and the runtime call would still
     * fail.
     */
    @Test
    fun `the legacy blocking accessors exist under the names compiled extensions call`() {
        listOf(
            "getManga" to SManga::class.java,
            "getChapters" to List::class.java,
        ).forEach { (name, returnType) ->
            val method = declaredLegacy(name, returnType)
            assertNotNull(
                method,
                "SMangaUpdate is missing `$name() : ${returnType.simpleName}`, which compiled " +
                    "extensions call. Its absence is a NoSuchMethodError at runtime, not a compile " +
                    "error here.",
            )
            // `synthetic` is expected, not a problem: Kotlin marks a `@JvmName`-renamed HIDDEN member
            // synthetic because it cannot be written in source. Synthetic methods are ordinary
            // methods in the bytecode and are called normally, so asserting otherwise would be
            // asserting an implementation detail that is correct as it stands.
            assertEquals(
                0,
                method!!.parameterCount,
                "`$name` takes no parameters; an already-compiled caller passes none.",
            )
        }
    }

    /**
     * Selects by name **and** return type.
     *
     * Both `getManga` names exist: the suspend property generates `getManga()Lkotlin/jvm/functions/
     * Function1;` and the legacy accessor generates `getManga()L…/SManga;`. The JVM distinguishes
     * them by descriptor, so a compiled caller is fine — and upstream has exactly this pair. But
     * `getDeclaredMethod(name)` resolves by name alone and returns an arbitrary one of the two, so a
     * name-only lookup would silently assert against the wrong method.
     */
    private fun declaredLegacy(name: String, returnType: Class<*>): java.lang.reflect.Method? =
        SMangaUpdate::class.java.declaredMethods
            .firstOrNull { it.name == name && it.returnType == returnType }
            ?.apply { isAccessible = true }

    /** The legacy accessor must return the value, not upstream's `"Stub!"`. */
    @Test
    fun `the legacy accessor returns the value rather than throwing`() {
        val manga = SManga.create().apply { url = "/manga/1" }
        val update = SMangaUpdate(manga, listOf(SChapter.create()))
        // Called reflectively because `DeprecationLevel.HIDDEN` makes these invisible to Kotlin
        // source — including this test — which is precisely how an extension compiled against an
        // older ABI still reaches them. Calling them by name would not compile, which is why a
        // source-level test cannot cover this at all.
        assertEquals(manga, invokeLegacy(update, "getManga"))
        assertEquals(1, (invokeLegacy(update, "getChapters") as List<*>).size)
    }

    @Test
    fun `the legacy accessor resolves a deferred update too`() {
        val manga = SManga.create().apply { url = "/manga/1" }
        val update = SMangaUpdate(manga) { listOf(SChapter.create()) }
        assertEquals(manga, invokeLegacy(update, "getManga"))
        assertEquals(1, (invokeLegacy(update, "getChapters") as List<*>).size)
    }

    private fun invokeLegacy(target: SMangaUpdate, method: String): Any? =
        SMangaUpdate::class.java.declaredMethods
            .first { it.name == method && !it.returnType.name.startsWith("kotlin.jvm.functions") }
            .apply { isAccessible = true }
            .invoke(target)
}
