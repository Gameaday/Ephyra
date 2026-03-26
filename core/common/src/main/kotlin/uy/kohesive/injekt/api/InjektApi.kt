package uy.kohesive.injekt.api

import uy.kohesive.injekt.Injekt

/**
 * A shim delegating to Koin, allowing legacy extension code that uses the Injekt.get()
 * extension function to continue resolving dependencies.
 */
inline fun <reified T : Any> Injekt.get(): T {
    return Injekt.get<T>()
}
