package uy.kohesive.injekt.api

import ephyra.core.common.di.CoreContainer
import java.lang.reflect.Type
import kotlin.reflect.KClass

open class InjektScope {

    inline fun <reified T : Any> get(): T = get(T::class.java)

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(clazz: Class<T>): T = CoreContainer.get(clazz)

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(clazz: KClass<T>): T = CoreContainer.get(clazz.java)

    @Suppress("UNCHECKED_CAST")
    fun <R : Any> getInstance(forType: Type): R = CoreContainer.get(forType.erasedType()) as R

    @Suppress("UNCHECKED_CAST")
    fun <R : Any> getInstanceOrNull(forType: Type): R? = try {
        getInstance(forType)
    } catch (_: Throwable) {
        null
    }

    fun <R : Any> getInstanceOrElse(forType: Type, default: R): R =
        getInstanceOrNull(forType) ?: default

    fun <R : Any> getInstanceOrElse(forType: Type, default: () -> R): R =
        getInstanceOrNull(forType) ?: default()

    fun <R : Any, K : Any> getKeyedInstance(forType: Type, key: K): R = getInstance(forType)
    fun <R : Any, K : Any> getKeyedInstanceOrNull(forType: Type, key: K): R? = getInstanceOrNull(forType)
    fun <R : Any, K : Any> getKeyedInstanceOrElse(forType: Type, key: K, default: R): R =
        getInstanceOrElse(forType, default)
    fun <R : Any, K : Any> getKeyedInstanceOrElse(forType: Type, key: K, default: () -> R): R =
        getInstanceOrElse(forType, default)

    @Suppress("UNCHECKED_CAST")
    fun <R : Any> getLogger(expectedLoggerType: Type, byName: String): R = getInstance(expectedLoggerType)

    @Suppress("UNCHECKED_CAST")
    fun <R : Any, T : Any> getLogger(expectedLoggerType: Type, forClass: Class<T>): R = getInstance(expectedLoggerType)

    fun <T : Any> addSingleton(forType: TypeReference<T>, singleInstance: T) {
        CoreContainer.register(forType.type.erasedType()) { singleInstance }
    }

    fun <R : Any> addSingletonFactory(forType: TypeReference<R>, factoryCalledOnce: () -> R) {
        CoreContainer.register(forType.type.erasedType(), factoryCalledOnce)
    }

    fun <R : Any> addFactory(forType: TypeReference<R>, factoryCalledEveryTime: () -> R) {
        CoreContainer.register(forType.type.erasedType(), factoryCalledEveryTime)
    }

    fun <R : Any> addPerThreadFactory(forType: TypeReference<R>, factoryCalledOncePerThread: () -> R) {
        CoreContainer.register(forType.type.erasedType(), factoryCalledOncePerThread)
    }

    @Suppress("UNCHECKED_CAST")
    fun <R : Any, K : Any> addPerKeyFactory(forType: TypeReference<R>, factoryCalledPerKey: (K) -> R) {
        CoreContainer.register(forType.type.erasedType()) { factoryCalledPerKey(Unit as K) }
    }

    @Suppress("UNCHECKED_CAST")
    fun <R : Any, K : Any> addPerThreadPerKeyFactory(
        forType: TypeReference<R>,
        factoryCalledPerKeyPerThread: (K) -> R,
    ) {
        CoreContainer.register(forType.type.erasedType()) { factoryCalledPerKeyPerThread(Unit as K) }
    }

    fun <R : Any> addLoggerFactory(
        forLoggerType: TypeReference<R>,
        factoryByName: (String) -> R,
        factoryByClass: (Class<Any>) -> R,
    ) {
    }

    fun <T : Any> hasFactory(forType: TypeReference<T>): Boolean = true

    inline fun <reified T : Any> addSingleton(singleInstance: T) {
        addSingleton(fullType<T>(), singleInstance)
    }

    inline fun <reified R : Any> addSingletonFactory(noinline factoryCalledOnce: () -> R) {
        addSingletonFactory(fullType<R>(), factoryCalledOnce)
    }

    inline fun <reified R : Any> addFactory(noinline factoryCalledEveryTime: () -> R) {
        addFactory(fullType<R>(), factoryCalledEveryTime)
    }
}

inline fun <reified T : Any> InjektScope.injectLazy(): Lazy<T> = lazy { get(T::class.java) }
inline fun <reified T : Any> InjektScope.injectValue(): Lazy<T> = lazyOf(get(T::class.java))
