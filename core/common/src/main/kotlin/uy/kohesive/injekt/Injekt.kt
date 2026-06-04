package uy.kohesive.injekt

import ephyra.core.common.di.CoreContainer

object Injekt {
    fun <T : Any> get(clazz: Class<T>): T {
        return CoreContainer.get(clazz)
    }
}
