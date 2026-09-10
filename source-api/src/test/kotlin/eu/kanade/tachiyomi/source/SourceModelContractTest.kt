package eu.kanade.tachiyomi.source.model

import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Contract test for the host `source-api` surface that extension APKs link against at runtime.
 *
 * Extensions compiled against upstream **tachiyomix 1.6** resolve `SManga.memo` /
 * `SChapter.memo` (getter and setter) on the host classes. If a member is missing, the
 * extension crashes with an `IncompatibleClassChangeError` wrapped by `ExtensionCallBoundary`
 * as "Source 'X' encountered an error" — the build compiles fine, so this is only detectable
 * here (see doc/MIGRATION_PLAN.md, Phase 13/14).
 */
class SourceModelContractTest {

    private fun assertAccessorExists(type: Class<*>, name: String, paramType: Class<*>?) {
        val message = "Extension ABI contract broken: `$type.$name` is missing. " +
            "Extensions compiled against tachiyomix 1.6 link against this member at runtime."
        assertTrue(type.declaredMethods.any { it.name == name }, message)
        if (paramType != null) {
            assertTrue(
                type.declaredMethods.any {
                    it.name == name && it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == paramType
                },
                message,
            )
        }
    }

    @Test
    fun `SManga exposes memo accessors compatible with tachiyomix 1_6`() {
        assertAccessorExists(SManga::class.java, "getMemo", null)
        assertAccessorExists(SManga::class.java, "setMemo", JsonObject::class.java)
        val manga = SManga.create()
        assertEquals(JsonObject(emptyMap()), manga.memo, "memo must default to an empty JsonObject")
        val memo = JsonObject(mapOf("source.custom" to JsonObject(emptyMap())))
        manga.memo = memo
        assertEquals(memo, manga.memo)
    }

    @Test
    fun `SChapter exposes memo accessors compatible with tachiyomix 1_6`() {
        assertAccessorExists(SChapter::class.java, "getMemo", null)
        assertAccessorExists(SChapter::class.java, "setMemo", JsonObject::class.java)
        val chapter = SChapter.create()
        assertEquals(JsonObject(emptyMap()), chapter.memo, "memo must default to an empty JsonObject")
        val memo = JsonObject(mapOf("source.custom" to JsonObject(emptyMap())))
        chapter.memo = memo
        assertEquals(memo, chapter.memo)
    }

    @Test
    fun `SManga copy preserves memo`() {
        val manga = SManga.create().apply {
            url = "/manga/test"
            title = "Test"
        }
        val memo = JsonObject(mapOf("source.custom" to JsonObject(emptyMap())))
        manga.memo = memo
        assertEquals(memo, manga.copy().memo)
    }

    @Test
    fun `SChapter copyFrom preserves memo`() {
        val source = SChapter.create().apply {
            url = "/chapter/1"
            name = "Chapter 1"
            memo = JsonObject(mapOf("source.custom" to JsonObject(emptyMap())))
        }
        val target = SChapter.create()
        target.copyFrom(source)
        assertEquals(source.memo, target.memo)
    }
}
