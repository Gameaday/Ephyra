package ephyra.data.track.kitsu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Guards the Kitsu date exchange. The implementation this replaced shared one non-thread-safe
 * `SimpleDateFormat`, read the API's UTC timestamps as local time and let `ParseException` escape
 * into list syncs; each of those regressions has a case below.
 */
class KitsuDateHelperTest {

    /** 2024-01-01T00:00:00Z */
    private val newYear2024 = 1_704_067_200_000L

    @Test
    fun `unset dates map to null and zero`() {
        assertNull(KitsuDateHelper.convert(0L))
        assertEquals(0L, KitsuDateHelper.parse(null))
        assertEquals(0L, KitsuDateHelper.parse(""))
        assertEquals(0L, KitsuDateHelper.parse("   "))
    }

    @Test
    fun `convert emits the canonical UTC wire format`() {
        assertEquals("2024-01-01T00:00:00.000Z", KitsuDateHelper.convert(newYear2024))
        assertEquals("1970-01-01T00:00:00.001Z", KitsuDateHelper.convert(1L))
    }

    @Test
    fun `parsing and formatting are pinned to UTC, not the device time zone`() {
        val deviceZone = TimeZone.getDefault()
        try {
            // UTC+14: the old literal-'Z' parse landed 14 hours early here.
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))

            assertEquals(newYear2024, KitsuDateHelper.parse("2024-01-01T00:00:00.000Z"))
            assertEquals("2024-01-01T00:00:00.000Z", KitsuDateHelper.convert(newYear2024))
        } finally {
            TimeZone.setDefault(deviceZone)
        }
    }

    @Test
    fun `parse tolerates the timestamp variants Kitsu sends`() {
        assertEquals(newYear2024, KitsuDateHelper.parse("2024-01-01T00:00:00.000Z"))
        assertEquals(newYear2024, KitsuDateHelper.parse("2024-01-01T00:00:00Z"))
        assertEquals(newYear2024, KitsuDateHelper.parse("2024-01-01T00:00:00+00:00"))
        assertEquals(newYear2024, KitsuDateHelper.parse("  2024-01-01T00:00:00.000Z  "))
    }

    @Test
    fun `malformed values are unset instead of throwing`() {
        assertEquals(0L, KitsuDateHelper.parse("yesterday"))
        assertEquals(0L, KitsuDateHelper.parse("2024-13-45T99:99:99.999Z"))
    }

    @Test
    fun `round trips epoch millis`() {
        listOf(1L, 1_000L, newYear2024, 1_719_800_000_000L).forEach { epoch ->
            assertEquals(epoch, KitsuDateHelper.parse(KitsuDateHelper.convert(epoch)))
        }
    }

    @Test
    fun `concurrent formatting and parsing stays consistent`() {
        val pool = Executors.newFixedThreadPool(8)
        try {
            val results = (1..500).map { index ->
                pool.submit<Boolean> {
                    val epoch = newYear2024 + index * 1_000L
                    KitsuDateHelper.parse(KitsuDateHelper.convert(epoch)) == epoch
                }
            }
            results.forEach { assertTrue("Shared formatter produced a wrong value", it.get(30, TimeUnit.SECONDS)) }
        } finally {
            pool.shutdownNow()
        }
    }
}
