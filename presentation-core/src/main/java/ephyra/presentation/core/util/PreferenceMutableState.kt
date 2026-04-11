package ephyra.presentation.core.util

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import ephyra.core.common.preference.Preference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KProperty

class PreferenceMutableState<T>(
    private val preference: Preference<T>,
    scope: CoroutineScope,
) : MutableState<T> {

    private val state = mutableStateOf(runBlocking { preference.get() })

    init {
        preference.changes()
            .onEach { state.value = it }
            .launchIn(scope)
    }

    override var value: T
        get() = state.value
        set(value) {
            preference.set(value)
        }

    override fun component1(): T = value

    override fun component2(): (T) -> Unit = { value = it }

    operator fun getValue(thisObj: Any?, property: KProperty<*>): T = value

    operator fun setValue(thisObj: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }
}

fun <T> Preference<T>.asState(scope: CoroutineScope) = PreferenceMutableState(this, scope)
