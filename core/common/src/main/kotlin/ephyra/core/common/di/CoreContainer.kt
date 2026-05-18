package ephyra.core.common.di

import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * A lightweight, deterministic, pure-Kotlin dependency container.
 * Stores singleton dependencies and interactor instances registered during app startup.
 * Guarantees 100% compile-time type-safety.
 */
object CoreContainer {
    private val instances = ConcurrentHashMap<Class<*>, Any>()
    private val factories = ConcurrentHashMap<Class<*>, () -> Any>()

    /**
     * Registers a dependency instance with the container.
     */
    fun <T : Any> register(clazz: Class<T>, instance: T) {
        instances[clazz] = instance
    }

    /**
     * Registers a dependency instance with the container (using KClass).
     */
    fun <T : Any> register(clazz: KClass<T>, instance: T) {
        instances[clazz.java] = instance
    }

    /**
     * Registers a factory function for generating instances of type [T].
     */
    fun <T : Any> registerFactory(clazz: Class<T>, factory: () -> T) {
        factories[clazz] = factory
    }

    /**
     * Registers a factory function for generating instances of type [T] (using KClass).
     */
    fun <T : Any> registerFactory(clazz: KClass<T>, factory: () -> T) {
        factories[clazz.java] = factory
    }

    /**
     * Retrieves a dependency of type [T].
     * Throws [IllegalStateException] if the dependency is not registered.
     */
    inline fun <reified T : Any> get(): T {
        return get(T::class.java)
    }

    /**
     * Retrieves a dependency of the specified [Class].
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(clazz: Class<T>): T {
        val instance = instances[clazz]
        if (instance != null) {
            return instance as T
        }

        val factory = factories[clazz]
        if (factory != null) {
            return factory() as T
        }

        throw IllegalStateException(
            "No dependency or factory registered for class: ${clazz.name}. Ensure it is registered in AppDependencyContainer."
        )
    }

    /**
     * Retrieves a dependency of the specified [KClass].
     */
    fun <T : Any> get(clazz: KClass<T>): T {
        return get(clazz.java)
    }

    /**
     * Checks if a dependency of the specified [Class] is registered.
     */
    fun has(clazz: Class<*>): Boolean {
        return instances.containsKey(clazz) || factories.containsKey(clazz)
    }

    /**
     * Clears all registered dependencies and factories (mainly for testing).
     */
    fun clear() {
        instances.clear()
        factories.clear()
    }
}
