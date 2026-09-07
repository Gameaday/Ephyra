package uy.kohesive.injekt

import ephyra.core.common.di.CoreContainer
import uy.kohesive.injekt.api.InjektScope

object Injekt : InjektScope()

fun getInjekt(): InjektScope = Injekt

val injektInstance: InjektScope
    get() = Injekt

inline fun <reified T : Any> injectLazy(): Lazy<T> = lazy { CoreContainer.get(T::class.java) }
inline fun <reified T : Any> Any.injectLazy(): Lazy<T> = lazy { CoreContainer.get(T::class.java) }

inline fun <reified T : Any> injectValue(): Lazy<T> = lazyOf(CoreContainer.get(T::class.java))
inline fun <reified T : Any> Any.injectValue(): Lazy<T> = lazyOf(CoreContainer.get(T::class.java))
