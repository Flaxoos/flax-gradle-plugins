import java.net.URI

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `jvm-test-suite`
    `version-catalog`
    `maven-publish`
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.versionCheck)
    alias(libs.plugins.kotlinxKover)
}

group = property("GROUP").toString()
version = property("VERSION").toString()
description = property("DESCRIPTION").toString()

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
}

val integrationTest by sourceSets.creating
val integrationTestTask = tasks.register<Test>("integrationTest") {
    description = "Runs the integration tests."
    group = "verification"
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    mustRunAfter(tasks.test)
}
tasks.check {
    dependsOn(integrationTestTask)
}

val functionalTest: SourceSet by sourceSets.creating
val functionalTestTask = tasks.register<Test>("functionalTest") {
    group = "verification"
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    useJUnitPlatform()
}

tasks.check {
    dependsOn(functionalTestTask)
}

kotlin.target.compilations.getByName("integrationTest") {
    associateWith(kotlin.target.compilations.getByName("main"))
}
kotlin.target.compilations.getByName("functionalTest") {
    associateWith(kotlin.target.compilations.getByName("main"))
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
    implementation(libs.kgit)
    implementation(libs.kotlinLogging)

    testImplementation(libs.kotest.junit5)
    testImplementation(libs.mockk)
    "functionalTestImplementation"(libs.kotest.junit5)
    "functionalTestImplementation"(libs.mockk)
    "functionalTestImplementation"(libs.kotestFrameworkDatatest)
    "integrationTestImplementation"(project)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

kover {
    excludeSourceSets {
        this.names(functionalTest.name, integrationTest.name)
    }
}

koverReport {
    defaults {
        filters {
            excludes {
                classes("io.flax.kover.ColorBand", "io.flax.kover.Names")
            }
        }
        html { onCheck = true }
        verify {
            rule {
                isEnabled = true
                minBound(20)
            }
            onCheck = true
        }
    }
}

publishing {
    repositories {
        maven {
            createReleaseTag()
            name = "GitHubPackages"
            url =
                URI("https://maven.pkg.github.com/idoflax/flax-gradle-plugins")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GPR_USER")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("GPR_TOKEN")
            }
        }
    }
}

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
    testSourceSets(functionalTest)
}

/**
 * Deletes the current tag and recreates it
 */
fun Project.createReleaseTag() {
    val tagName = "release/$version"
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
