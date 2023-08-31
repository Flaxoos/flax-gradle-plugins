package io.github.flaxoos.kover

class Application(private val helloSayer: HelloSayer, private val shouldSayGoodbye: Boolean) {
    fun run() {
        helloSayer.sayHello()
        if (shouldSayGoodbye) {
            helloSayer.sayGoodbye()
        }
    }
}

class HelloSayer(private val name: String) {
    fun sayHello() {
        println("Hello $name")
    }

    fun sayGoodbye() {
        println("Goodbye $name")
    }
}
