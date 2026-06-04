package uy.kohesive.injekt.api

import uy.kohesive.injekt.Injekt

inline fun <reified T : Any> Injekt.get(): T {
    return Injekt.get(T::class.java)
}
