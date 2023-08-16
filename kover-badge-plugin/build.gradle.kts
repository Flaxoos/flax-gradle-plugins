import java.net.URI

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `version-catalog`
    `maven-publish`
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.versionCheck)
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
}

group = property("GROUP").toString()
version = property("VERSION").toString()
description = property("DESCRIPTION").toString()

@Suppress("UnstableApiUsage")
gradlePlugin {
    website.set("https://github.com/idoflax/kover-badge-plugin")
    vcsUrl.set("https://github.com/idoflax/kover-badge-plugin")
    plugins {
        create("io.flax.kover-badge") {
            id = property("ID").toString()
            implementationClass = property("IMPLEMENTATION_CLASS").toString()
            version = property("VERSION").toString()
            description = property("DESCRIPTION").toString()
            displayName = property("DISPLAY_NAME").toString()
            tags.set(listOf("plugin", "gradle", "kover", "badge"))
        }
    }
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(gradleApi())
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kover.gradlePlugin)
    implementation(libs.ktlint.gradlePlugin)
    implementation(libs.kotlinCssLib)
    implementation(libs.konform)

    testImplementation(libs.kotest.junit5)
    testImplementation(libs.mockk)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

publishing {
    repositories {
        maven {
            createReleaseTag()
            name = "GitHubPackages"
            url =
                URI("https://maven.pkg.github.com/idoflax/${project.findProperty("github.repository.name") ?: project.name}")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GPR_USER")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("GPR_TOKEN")
            }
        }
    }
}

/**
 * Deletes the current tag and recreates it
 */
fun Project.createReleaseTag() {
    val tagName = "release/${version}"
    try {
        runCommands("git", "tag", "-d", tagName)
    } catch (e: Exception) {
        logger.warn("Tagging release encountered: ${e.message}")
    }
    runCommands("git", "status")
    runCommands("git", "tag", tagName)
}

/**
 * Run a command
 */
fun runCommands(vararg commands: String): String {
    val process = ProcessBuilder(*commands).redirectErrorStream(true).start()
    process.waitFor()
    var result = ""
    process.inputStream.bufferedReader().use { it.lines().forEach { line -> result += line + "\n" } }
    val errorResult = process.exitValue() == 0
    if (!errorResult) {
        throw IllegalStateException(result)
    }
    return result
}
