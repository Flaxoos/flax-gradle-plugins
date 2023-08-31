package io.github.flaxoos.kover

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.FunSpec
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify

class ApplicationTest : FunSpec() {

    override fun isolationMode() = IsolationMode.InstancePerTest

    init {
        val name = "John"
        val helloSayer = mockk<HelloSayer>()

        context("Application run") {
            test("should say hello and goodbye") {
                every { helloSayer.sayHello() } just Runs
                every { helloSayer.sayGoodbye() } just Runs

                val app = Application(helloSayer, shouldSayGoodbye = true)
                app.run()

                verify { helloSayer.sayHello() }
                verify { helloSayer.sayGoodbye() }
            }

            test("should say hello but not goodbye") {
                every { helloSayer.sayHello() } just Runs
                every { helloSayer.sayGoodbye() } just Runs

                val app = Application(helloSayer, shouldSayGoodbye = false)
                app.run()

                verify { helloSayer.sayHello() }
                verify(exactly = 0) { helloSayer.sayGoodbye() }
            }
        }

        context("HelloSayer methods") {
            test("should print hello message correctly") {
                val sayer = HelloSayer(name)
                sayer.sayHello() // You can redirect and capture the stdout to validate this.
            }

            test("should print goodbye message correctly") {
                val sayer = HelloSayer(name)
                sayer.sayGoodbye() // Similar to above, capture and validate stdout.
            }
        }
    }
}
