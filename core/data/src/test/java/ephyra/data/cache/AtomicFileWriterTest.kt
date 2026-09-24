package ephyra.data.cache

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.nio.file.Path

class AtomicFileWriterTest {

    @Test
    fun `publishes a complete replacement`(@TempDir directory: Path) {
        val target = File(directory.toFile(), "cover.bin")
        target.writeText("old")

        writeFileAtomically(target) { temporary ->
            temporary.writeText("new")
        }

        assertEquals("new", target.readText())
        assertFalse(directory.toFile().listFiles().orEmpty().any { it.name.endsWith(".tmp") })
    }

    @Test
    fun `keeps the previous complete file when replacement fails`(@TempDir directory: Path) {
        val target = File(directory.toFile(), "cover.bin")
        target.writeText("old")

        assertThrows(IOException::class.java) {
            writeFileAtomically(target) { temporary ->
                temporary.writeText("partial")
                throw IOException("simulated write failure")
            }
        }

        assertEquals("old", target.readText())
        assertFalse(directory.toFile().listFiles().orEmpty().any { it.name.endsWith(".tmp") })
    }
}
