package uy.kohesive.injekt

import org.koin.core.context.GlobalContext

/**
 * A shim that allows legacy extension code that depends on the Injekt service locator
 * to instead resolve its dependencies from the global Koin context.
 * 
 * This object is placed in the `uy.kohesive.injekt` package to intercept calls made
 * by APK plugins/extensions that were compiled against the original Injekt library.
 */
object Injekt {

    /**
     * Resolves a dependency of type [T] from the [GlobalContext].
     */
    inline fun <reified T : Any> get(): T {
        return GlobalContext.get().get()
    }
    
    /**
     * Legacy Injekt API bridge for lazy injection.
     */
    inline fun <reified T : Any> injectLazy(): Lazy<T> = lazy { get<T>() }
}

/**
 * Global extension for lazy injection, matching the original Injekt API.
 */
inline fun <reified T : Any> Any.injectLazy(): Lazy<T> = Injekt.injectLazy()
