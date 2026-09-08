package uy.kohesive.injekt.api

interface InjektModule {
    fun registerWith(intoModule: InjektRegistrar) {
        intoModule.registerInjectables()
    }

    fun InjektRegistrar.registerInjectables()
}

abstract class InjektScopedMain(val scope: InjektScope) : InjektModule {
    init {
        scope.registerInjectables()
    }
}
