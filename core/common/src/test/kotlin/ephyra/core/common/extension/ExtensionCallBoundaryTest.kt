package ephyra.core.common.extension

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class ExtensionCallBoundaryTest {

    @Test
    fun `runExtensionCall returns result on success`() = runBlocking {
        val result = runExtensionCall(sourceName = "Test Source") {
            "manga page data"
        }
        assertEquals("manga page data", result)
    }

    @Test
    fun `runExtensionCall throws SourceTimeoutException when execution exceeds timeout`() {
        val exception = assertThrows(SourceTimeoutException::class.java) {
            runBlocking {
                runExtensionCall(sourceName = "Hanging Source", timeoutMs = 50L) {
                    delay(500L)
                }
            }
        }
        assertTrue(exception.message!!.contains("Hanging Source"))
    }

    @Test
    fun `runExtensionCall rethrows CancellationException directly`() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                runExtensionCall(sourceName = "Cancelling Source") {
                    throw CancellationException("Cancelled by parent")
                }
            }
        }
    }

    @Test
    fun `runExtensionCall rethrows standard Exception directly`() {
        val exception = assertThrows(IOException::class.java) {
            runBlocking {
                runExtensionCall(sourceName = "Failing Source") {
                    throw IOException("Network connection reset")
                }
            }
        }
        assertEquals("Network connection reset", exception.message)
    }

    @Test
    fun `runExtensionCall wraps non-Exception Throwable in SourceExecutionException`() {
        val exception = assertThrows(SourceExecutionException::class.java) {
            runBlocking {
                runExtensionCall(sourceName = "Broken Bytecode Source") {
                    throw NoClassDefFoundError("eu/kanade/tachiyomi/OldLibrary")
                }
            }
        }
        assertTrue(exception.message!!.contains("Broken Bytecode Source"))
        assertTrue(exception.cause is NoClassDefFoundError)
    }
}
