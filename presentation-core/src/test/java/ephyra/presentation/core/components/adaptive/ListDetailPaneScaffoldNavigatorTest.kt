package ephyra.presentation.core.components.adaptive

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ListDetailPaneScaffoldNavigatorTest {

    @Test
    fun `initial role is List by default`() {
        val navigator = ListDetailPaneScaffoldNavigator()
        assertEquals(ListDetailPaneRole.List, navigator.currentRole)
        assertFalse(navigator.isShowingDetail)
    }

    @Test
    fun `navigateTo changes role and updates isShowingDetail`() {
        val navigator = ListDetailPaneScaffoldNavigator()
        navigator.navigateTo(ListDetailPaneRole.Detail)
        assertEquals(ListDetailPaneRole.Detail, navigator.currentRole)
        assertTrue(navigator.isShowingDetail)

        navigator.navigateTo(ListDetailPaneRole.Extra)
        assertEquals(ListDetailPaneRole.Extra, navigator.currentRole)
        assertFalse(navigator.isShowingDetail)
    }

    @Test
    fun `navigateBack returns to List when on Detail or Extra`() {
        val navigator = ListDetailPaneScaffoldNavigator()
        assertFalse(navigator.navigateBack())

        navigator.navigateTo(ListDetailPaneRole.Detail)
        assertTrue(navigator.navigateBack())
        assertEquals(ListDetailPaneRole.List, navigator.currentRole)

        navigator.navigateTo(ListDetailPaneRole.Extra)
        assertTrue(navigator.navigateBack())
        assertEquals(ListDetailPaneRole.List, navigator.currentRole)
    }

    @Test
    fun `saver restores navigator state`() {
        val navigator = ListDetailPaneScaffoldNavigator(ListDetailPaneRole.Detail)
        val saved = with(ListDetailPaneScaffoldNavigator.Saver) {
            mockkSaverScope().save(navigator)
        }
        assertEquals("Detail", saved)

        val restored = ListDetailPaneScaffoldNavigator.Saver.restore(saved as String)
        assertEquals(ListDetailPaneRole.Detail, restored?.currentRole)
    }

    private fun mockkSaverScope() = object : androidx.compose.runtime.saveable.SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }
}
