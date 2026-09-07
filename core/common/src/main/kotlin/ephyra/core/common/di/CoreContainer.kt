package ephyra.core.common.di

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

@Deprecated("Use standard Hilt injection or Hilt EntryPoints instead. Kept only for legacy extension compatibility.")
object CoreContainer {
    lateinit var applicationContext: Context

    private val providers = ConcurrentHashMap<Class<*>, () -> Any>()

    @Volatile
    private var fallbackProvider: ((Class<*>) -> Any?)? = null

    val isInitialized: Boolean
        get() = ::applicationContext.isInitialized

    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    fun setFallbackProvider(provider: (Class<*>) -> Any?) {
        fallbackProvider = provider
    }

    fun <T : Any> register(clazz: Class<T>, provider: () -> T) {
        providers[clazz] = provider
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(clazz: Class<T>): T {
        val provider = providers[clazz]
        if (provider != null) {
            return provider() as T
        }

        val fallback = fallbackProvider?.invoke(clazz)
        if (fallback != null && clazz.isInstance(fallback)) {
            providers[clazz] = { fallback }
            return fallback as T
        }

        throw IllegalArgumentException(
            "No Hilt EntryPoint / dependency registered for requested class: ${clazz.name}. " +
                "Ensure that this class is registered in CoreContainer at application startup.",
        )
    }

    inline fun <reified T : Any> get(): T {
        return get(T::class.java)
    }

    fun <T : Any> get(clazz: KClass<T>): T {
        return get(clazz.java)
    }
}
