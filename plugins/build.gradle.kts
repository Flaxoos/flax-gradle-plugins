plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
}

