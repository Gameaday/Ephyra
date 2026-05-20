package uy.kohesive.injekt

import ephyra.core.common.di.CoreContainer
import kotlin.reflect.KClass

/**
 * A legacy compatibility shim for extensions that still rely on the Injekt service locator.
 * Directs all [get] calls to the modern, deterministic [CoreContainer].
 */
object Injekt {

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> get(
        qualifier: String? = null,
        noinline parameters: (() -> Any)? = null,
    ): T {
        return CoreContainer.get(T::class.java)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(
        clazz: KClass<T>,
        qualifier: String? = null,
        parameters: (() -> Any)? = null,
    ): T {
        return CoreContainer.get(clazz.java)
    }
}
