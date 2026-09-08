package uy.kohesive.injekt

import dev.mihon.injekt.patchInjekt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uy.kohesive.injekt.api.InjektScope
import uy.kohesive.injekt.api.InjektionException
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class InjektShimTest {

    private interface TestService {
        fun getName(): String
    }

    private class TestServiceImpl(private val name: String) : TestService {
        override fun getName(): String = name
    }

    private interface UnregisteredService

    @BeforeEach
    fun setUp() {
        patchInjekt()
    }

    @Test
    fun `Injekt is InjektScope`() {
        assertTrue(Injekt is InjektScope)
    }

    @Test
    fun `Injekt addSingleton and get resolves correctly`() {
        val service = TestServiceImpl("SingletonService")
        Injekt.addSingleton<TestService>(service)

        val resolved: TestService = Injekt.get()
        assertSame(service, resolved)
        assertEquals("SingletonService", resolved.getName())

        val resolvedByClass: TestService = Injekt.getInstance(TestService::class.java)
        assertSame(service, resolvedByClass)
    }

    @Test
    fun `Injekt addSingletonFactory registers provider and resolves lazily and once`() {
        var count = 0
        Injekt.addSingletonFactory<TestService> {
            count++
            TestServiceImpl("Count-$count")
        }

        assertEquals(0, count)
        val resolved1: TestService = Injekt.get()
        assertEquals(1, count)
        assertEquals("Count-1", resolved1.getName())
        val resolved2: TestService = Injekt.get()
        assertEquals(1, count)
        assertEquals("Count-1", resolved2.getName())
        assertSame(resolved1, resolved2)
    }

    @Test
    fun `injectLazy provides lazy resolution from Injekt`() {
        val service = TestServiceImpl("LazyService")
        Injekt.addSingleton<TestService>(service)

        val lazyService: Lazy<TestService> = injectLazy()
        assertNotNull(lazyService)
        assertEquals("LazyService", lazyService.value.getName())
        assertSame(service, lazyService.value)
    }

    @Test
    fun `injectLazy delegate provides lazy resolution inside classes`() {
        val service = TestServiceImpl("ExtensionClassService")
        Injekt.addSingleton<TestService>(service)

        class DummyExtension {
            val injected: TestService by injectLazy()
        }

        val dummy = DummyExtension()
        assertEquals("ExtensionClassService", dummy.injected.getName())
        assertSame(service, dummy.injected)
    }

    @Test
    fun `injectValue provides eager resolution from Injekt`() {
        val service = TestServiceImpl("EagerService")
        Injekt.addSingleton<TestService>(service)

        val resolved: Lazy<TestService> = injectValue()
        assertSame(service, resolved.value)
    }

    @Test
    fun `resolving unregistered dependency throws InjektionException`() {
        assertThrows(InjektionException::class.java) {
            Injekt.get<UnregisteredService>()
        }
    }

    @Test
    fun `Injekt implements InjektFactory, InjektRegistry, and InjektRegistrar interfaces`() {
        val scope: InjektScope = Injekt
        assertTrue(scope is uy.kohesive.injekt.api.InjektFactory)
        assertTrue(scope is uy.kohesive.injekt.api.InjektRegistry)
        assertTrue(scope is uy.kohesive.injekt.api.InjektRegistrar)

        val factory: uy.kohesive.injekt.api.InjektFactory = Injekt
        val service = TestServiceImpl("FactoryResolved")
        Injekt.addSingleton<TestService>(service)

        val resolvedFromFactory: TestService = factory.getInstance(TestService::class.java)
        assertSame(service, resolvedFromFactory)
    }

    @Test
    fun `InjektRegistrar imports InjektModule successfully`() {
        val registrar: uy.kohesive.injekt.api.InjektRegistrar = Injekt
        val service = TestServiceImpl("ModuleService")

        val module = object : uy.kohesive.injekt.api.InjektModule {
            override fun uy.kohesive.injekt.api.InjektRegistrar.registerInjectables() {
                addSingleton(uy.kohesive.injekt.api.fullType<TestService>(), service)
            }
        }
        registrar.importModule(module)

        val resolved: TestService = Injekt.get()
        assertSame(service, resolved)
    }

    @Test
    fun `patchInjekt function executes without error`() {
        patchInjekt()
        assertTrue(Injekt is InjektScope)
    }
}
