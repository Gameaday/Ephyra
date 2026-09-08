@file:Suppress("NOTHING_TO_INLINE", "DEPRECATION")

package uy.kohesive.injekt.api

import ephyra.core.common.di.CoreContainer
import java.lang.reflect.Type
import kotlin.reflect.KClass

open class InjektScope : InjektRegistrar {

    inline fun <reified T : Any> get(): T = get(T::class.java)

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(clazz: Class<T>): T = CoreContainer.get(clazz)

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(clazz: KClass<T>): T = CoreContainer.get(clazz.java)

    @Suppress("UNCHECKED_CAST")
    override fun <R : Any> getInstance(forType: Type): R = CoreContainer.get(forType.erasedType()) as R

    @Suppress("UNCHECKED_CAST")
    override fun <R : Any> getInstanceOrNull(forType: Type): R? = try {
        getInstance(forType)
    } catch (_: Throwable) {
        null
    }

    override fun <R : Any> getInstanceOrElse(forType: Type, default: R): R =
        getInstanceOrNull(forType) ?: default

    override fun <R : Any> getInstanceOrElse(forType: Type, default: () -> R): R =
        getInstanceOrNull(forType) ?: default()

    override fun <R : Any, K : Any> getKeyedInstance(forType: Type, key: K): R = getInstance(forType)
    override fun <R : Any, K : Any> getKeyedInstanceOrNull(forType: Type, key: K): R? = getInstanceOrNull(forType)
    override fun <R : Any, K : Any> getKeyedInstanceOrElse(forType: Type, key: K, default: R): R =
        getInstanceOrElse(forType, default)
    override fun <R : Any, K : Any> getKeyedInstanceOrElse(forType: Type, key: K, default: () -> R): R =
        getInstanceOrElse(forType, default)

    @Suppress("UNCHECKED_CAST")
    override fun <R : Any> getLogger(expectedLoggerType: Type, byName: String): R = getInstance(expectedLoggerType)

    @Suppress("UNCHECKED_CAST")
    override fun <R : Any, T : Any> getLogger(
        expectedLoggerType: Type,
        forClass: Class<T>,
    ): R = getInstance(expectedLoggerType)

    override fun <T : Any> addSingleton(forType: TypeReference<T>, singleInstance: T) {
        CoreContainer.register(forType.type.erasedType()) { singleInstance }
    }

    override fun <R : Any> addSingletonFactory(forType: TypeReference<R>, factoryCalledOnce: () -> R) {
        CoreContainer.register(forType.type.erasedType(), factoryCalledOnce)
    }

    override fun <R : Any> addFactory(forType: TypeReference<R>, factoryCalledEveryTime: () -> R) {
        CoreContainer.register(forType.type.erasedType(), factoryCalledEveryTime)
    }

    override fun <R : Any> addPerThreadFactory(forType: TypeReference<R>, factoryCalledOncePerThread: () -> R) {
        CoreContainer.register(forType.type.erasedType(), factoryCalledOncePerThread)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R : Any, K : Any> addPerKeyFactory(forType: TypeReference<R>, factoryCalledPerKey: (K) -> R) {
        CoreContainer.register(forType.type.erasedType()) { factoryCalledPerKey(Unit as K) }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R : Any, K : Any> addPerThreadPerKeyFactory(
        forType: TypeReference<R>,
        factoryCalledPerKeyPerThread: (K) -> R,
    ) {
        CoreContainer.register(forType.type.erasedType()) { factoryCalledPerKeyPerThread(Unit as K) }
    }

    override fun <R : Any> addLoggerFactory(
        forLoggerType: TypeReference<R>,
        factoryByName: (String) -> R,
        factoryByClass: (Class<Any>) -> R,
    ) {}

    override fun <O : Any, T : O> addAlias(
        existingRegisteredType: TypeReference<T>,
        otherAncestorOrInterface: TypeReference<O>,
    ) {
        CoreContainer.register(otherAncestorOrInterface.type.erasedType()) {
            CoreContainer.get(existingRegisteredType.type.erasedType())
        }
    }

    override fun <T : Any> hasFactory(forType: TypeReference<T>): Boolean = true

    inline fun <reified T : Any> addSingleton(singleInstance: T) {
        addSingleton(fullType<T>(), singleInstance)
    }

    inline fun <reified R : Any> addSingletonFactory(noinline factoryCalledOnce: () -> R) {
        addSingletonFactory(fullType<R>(), factoryCalledOnce)
    }

    inline fun <reified R : Any> addFactory(noinline factoryCalledEveryTime: () -> R) {
        addFactory(fullType<R>(), factoryCalledEveryTime)
    }

    inline fun <reified T : Any> injectLazy(): Lazy<T> = lazy { get(T::class.java) }
    inline fun <reified T : Any> injectValue(): Lazy<T> = lazyOf(get(T::class.java))
    inline fun <reified T : Any> injectLazy(key: Any): Lazy<T> = lazy { get(T::class.java) }
    inline fun <reified T : Any> injectValue(key: Any): Lazy<T> = lazyOf(get(T::class.java))
    inline fun <reified T : Any, O : Any> injectLogger(forClass: Class<O>): Lazy<T> = lazy { get(T::class.java) }
    inline fun <reified T : Any, O : Any> injectLogger(forClass: KClass<O>): Lazy<T> = lazy { get(T::class.java) }
    inline fun <reified R : Any, reified T : Any> injectLogger(byName: String): Lazy<T> = lazy { get(T::class.java) }

    inline fun <reified R : Any> addScopedSingletonFactory(noinline scopedFactoryCalledOnce: InjektScope.() -> R) {
        addSingletonFactory(fullType<R>()) { this.scopedFactoryCalledOnce() }
    }

    inline fun <reified R : Any> addScopedFactory(noinline scopedFactoryCalledEveryTime: InjektScope.() -> R) {
        addFactory(fullType<R>()) { this.scopedFactoryCalledEveryTime() }
    }
}

abstract class LocalScoped(val localScope: InjektScope) {
    inline fun <reified T : Any> injectLazy(): Lazy<T> = localScope.injectLazy()
    inline fun <reified T : Any> injectValue(): Lazy<T> = localScope.injectValue()
    inline fun <reified T : Any> injectLazy(key: Any): Lazy<T> = localScope.injectLazy(key)
    inline fun <reified T : Any> injectValue(key: Any): Lazy<T> = localScope.injectValue(key)
}
