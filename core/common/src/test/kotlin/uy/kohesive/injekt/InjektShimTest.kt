package uy.kohesive.injekt

import ephyra.core.common.di.CoreContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uy.kohesive.injekt.api.InjektScope

class InjektShimTest {

    private interface TestService {
        fun getName(): String
    }

    private class TestServiceImpl(private val name: String) : TestService {
        override fun getName(): String = name
    }

    @BeforeEach
    fun setUp() {
        // Reset or clear if needed
    }

    private interface UnregisteredService

    @Test
    fun `Injekt instance resolves via InjektKt getInjekt and top-level Injekt object`() {
        assertTrue(Injekt is InjektScope)
        assertSame(Injekt, getInjekt())
        assertSame(Injekt, injektInstance)
    }

    @Test
    fun `Injekt get resolves dependencies registered in CoreContainer`() {
        val service = TestServiceImpl("CoreContainerRegistered")
        CoreContainer.register(TestService::class.java) { service }

        val resolvedFromInjekt: TestService = Injekt.get()
        assertSame(service, resolvedFromInjekt)
        assertEquals("CoreContainerRegistered", resolvedFromInjekt.getName())

        val resolvedByClass: TestService = Injekt.get(TestService::class.java)
        assertSame(service, resolvedByClass)
    }

    @Test
    fun `Injekt addSingleton registers dependency in CoreContainer and resolves correctly`() {
        val service = TestServiceImpl("SingletonService")
        Injekt.addSingleton<TestService>(service)

        val resolved: TestService = Injekt.get()
        assertSame(service, resolved)
    }

    @Test
    fun `Injekt addSingletonFactory registers provider and resolves correctly`() {
        var count = 0
        Injekt.addSingletonFactory<TestService> {
            count++
            TestServiceImpl("Count-$count")
        }

        val resolved1: TestService = Injekt.get()
        assertEquals("Count-1", resolved1.getName())
        val resolved2: TestService = Injekt.get()
        assertEquals("Count-2", resolved2.getName())
    }

    @Test
    fun `injectLazy provides lazy resolution from Injekt`() {
        val service = TestServiceImpl("LazyService")
        CoreContainer.register(TestService::class.java) { service }

        val lazyService: Lazy<TestService> = injectLazy()
        assertNotNull(lazyService)
        assertEquals("LazyService", lazyService.value.getName())
        assertSame(service, lazyService.value)
    }

    @Test
    fun `Any injectLazy extension provides lazy resolution inside classes`() {
        val service = TestServiceImpl("ExtensionClassService")
        CoreContainer.register(TestService::class.java) { service }

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
        CoreContainer.register(TestService::class.java) { service }

        val resolved: Lazy<TestService> = injectValue()
        assertSame(service, resolved.value)
    }

    @Test
    fun `resolving unregistered dependency throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            Injekt.get<UnregisteredService>()
        }
    }
}
