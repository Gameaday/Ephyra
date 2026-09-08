package tachiyomi.core.common.util.lang

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import rx.Observable
import java.io.IOException

class RxCoroutineBridgeTest {

    @Test
    fun `awaitSingle returns single emitted item`() = runBlocking {
        val observable = Observable.just("Hello Rx")
        val result = observable.awaitSingle()
        assertEquals("Hello Rx", result)
    }

    @Test
    fun `awaitSingle throws exception on observable error`() {
        val observable = Observable.error<String>(IOException("Network failure"))
        assertThrows(IOException::class.java) {
            runBlocking {
                observable.awaitSingle()
            }
        }
    }

    @Test
    fun `awaitSingle throws exception when observable emits multiple items`() {
        val observable = Observable.just("Item 1", "Item 2")
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                observable.awaitSingle()
            }
        }
    }
}
