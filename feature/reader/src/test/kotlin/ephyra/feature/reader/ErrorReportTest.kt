package ephyra.feature.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What a copied failure report contains.
 *
 * Pure, so it is checked without a device — the composition is the part that matters and the log
 * reading is the part that cannot fail meaningfully.
 */
class ErrorReportTest {

    private val message = "Image URL is not a usable http(s) address (…): https://…"

    @Test
    fun `the failure comes first, so the report reads without the log`() {
        val report = buildErrorReport(message, "D/HttpPageLoader: something")
        assertTrue(report.startsWith(message), "got: $report")
    }

    @Test
    fun `the log follows under a heading`() {
        val report = buildErrorReport(message, "D/HttpPageLoader: something")
        assertTrue(report.contains("D/HttpPageLoader: something"))
        assertTrue(report.indexOf(message) < report.indexOf("D/HttpPageLoader"))
    }

    @Test
    fun `an unavailable log leaves a report that is still the failure`() {
        // `logcat` can be unavailable — a stripped build, a permission, a device that refuses. The
        // message must survive that, and no dangling heading may be left behind claiming evidence
        // that is not there.
        val report = buildErrorReport(message, "")
        assertEquals(message, report)
        assertFalse(report.contains("recent log"), "got: $report")
    }

    @Test
    fun `a blank message still yields the log rather than nothing`() {
        val report = buildErrorReport("", "D/HttpPageLoader: something")
        assertTrue(report.contains("D/HttpPageLoader: something"))
    }

    @Test
    fun `a long log is not truncated - the point of copying it is the evidence`() {
        val log = (1..2000).joinToString("\n") { "line $it" }
        assertTrue(buildErrorReport(message, log).contains("line 2000"))
    }
}
