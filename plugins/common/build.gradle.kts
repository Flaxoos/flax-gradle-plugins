plugins {
    kotlin("jvm")
}

group = "io.github.flaxoos"

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + "-Xcontext-receivers"
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
}
