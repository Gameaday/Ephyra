package ephyra.core.common.di

import android.content.Context
import kotlin.reflect.KClass

@Deprecated("Use standard Hilt injection or Hilt EntryPoints instead. Kept only for legacy extension compatibility.")
object CoreContainer {
    lateinit var applicationContext: Context

    private val providers = mutableMapOf<Class<*>, () -> Any>()

    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    fun <T : Any> register(clazz: Class<T>, provider: () -> T) {
        providers[clazz] = provider
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(clazz: Class<T>): T {
        val provider = providers[clazz] ?: throw IllegalArgumentException(
            "No Hilt EntryPoint / dependency registered for requested class: ${clazz.name}. " +
                "Ensure that this class is registered in CoreContainer at application startup.",
        )
        return provider() as T
    }

    inline fun <reified T : Any> get(): T {
        return get(T::class.java)
    }

    fun <T : Any> get(clazz: KClass<T>): T {
        return get(clazz.java)
    }
}
