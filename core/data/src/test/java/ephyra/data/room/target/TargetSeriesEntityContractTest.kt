package ephyra.data.room.target

import org.junit.Test
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class TargetSeriesEntityContractTest {

    @Test
    fun `target series entity has opaque identity and no legacy source or library fields`() {
        val properties = TargetSeriesEntity::class.java.declaredFields.map { it.name }.toSet()

        assertTrue(
            properties.containsAll(
                setOf("localId", "contentType", "title", "author", "artist", "description", "status"),
            ),
        )
        assertFalse(properties.contains("source"))
        assertFalse(properties.contains("favorite"))
        assertFalse(properties.contains("viewerFlags"))
        assertFalse(properties.contains("chapterFlags"))
        assertFalse(properties.contains("url"))
    }

    @Test
    fun `library membership is represented separately`() {
        val seriesProperties = TargetSeriesEntity::class.java.declaredFields.map { it.name }.toSet()
        val libraryProperties = TargetLibraryEntryEntity::class.java.declaredFields.map { it.name }.toSet()

        assertFalse(seriesProperties.any { libraryProperties.contains(it) && it != "contentType" })
        assertTrue(libraryProperties.containsAll(setOf("seriesId", "addedAt", "updatePolicy", "updateEnabled")))
    }
}
