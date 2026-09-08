@file:Suppress("NOTHING_TO_INLINE", "DEPRECATION")

package uy.kohesive.injekt

import ephyra.core.common.di.CoreContainer
import uy.kohesive.injekt.api.InjektScope
import uy.kohesive.injekt.api.InjektScopedMain
import kotlin.reflect.KClass

object Injekt : InjektScope()

fun getInjekt(): InjektScope = Injekt

val injektInstance: InjektScope
    get() = Injekt

abstract class InjektMain : InjektScopedMain(Injekt)

inline fun <reified T : Any> injectLazy(): Lazy<T> = lazy { CoreContainer.get(T::class.java) }
inline fun <reified T : Any> injectValue(): Lazy<T> = lazyOf(CoreContainer.get(T::class.java))

inline fun <reified T : Any> injectLazy(key: Any): Lazy<T> = lazy { CoreContainer.get(T::class.java) }
inline fun <reified T : Any> injectValue(key: Any): Lazy<T> = lazyOf(CoreContainer.get(T::class.java))

inline fun <reified R : Any, reified T : Any> R.injectLogger(): Lazy<T> = lazy { CoreContainer.get(T::class.java) }
inline fun <reified T : Any, O : Any> injectLogger(
    forClass: KClass<O>,
): Lazy<T> = lazy { CoreContainer.get(T::class.java) }
inline fun <reified T : Any, O : Any> injectLogger(
    forClass: Class<O>,
): Lazy<T> = lazy { CoreContainer.get(T::class.java) }
inline fun <reified T : Any> injectLogger(byName: String): Lazy<T> = lazy { CoreContainer.get(T::class.java) }
