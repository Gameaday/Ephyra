package ephyra.core.common.notification

import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationIdsTest {

    /**
     * Notification IDs are app-global: posting a second notification with an ID already in
     * use silently replaces the first one. A duplicate previously existed between the
     * reader's save-image notification and the app update prompt.
     */
    @Test
    fun notificationIdsAreUnique() {
        val ids = NotificationIds::class.java.declaredFields
            .filter { it.type == Int::class.javaPrimitiveType }
            .associate { field -> field.name to field.getInt(null) }

        assertTrue("Expected notification IDs to be declared", ids.isNotEmpty())

        val duplicates = ids.entries
            .groupBy({ it.value }, { it.key })
            .filterValues { it.size > 1 }

        assertTrue("Duplicate notification IDs found: $duplicates", duplicates.isEmpty())
    }
}
