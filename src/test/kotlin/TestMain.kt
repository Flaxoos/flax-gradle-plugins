import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.Ignore

class Test{
    @Test @Ignore
    fun test1() {
        assertEquals("Hello, World! You're kovered", sayHello1())
    }

    @Test @Ignore
    fun test2() {
        assertEquals("Hello, World! You're kovered", sayHello2())
    }

    @Test @Ignore
    fun test3() {
        assertEquals("Hello, World! You're kovered", sayHello3())
    }
}
