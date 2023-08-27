plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
}

dependencies {
    api(libs.kotest.assertions.core)
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

gradlePlugin {
    plugins {
        create("plugin-test-project-conventions") {
            id = "io.flax.plugin-test-project-conventions"
            implementationClass = "io.flax.PluginTestProjectConventions"
        }
    }
}
