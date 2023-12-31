@file:Suppress("TooManyFunctions")

package io.github.flaxoos

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.gradle.publish.PublishPlugin
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import kotlinx.kover.gradle.plugin.dsl.KoverReportExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.JvmTestSuitePlugin
import org.gradle.api.plugins.catalog.VersionCatalogPlugin
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.repositories
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin
import org.gradle.testing.base.TestingExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.utils.IMPLEMENTATION
import java.net.URI
import java.util.Optional
import javax.inject.Inject

const val INTEGRATION_TEST_SUITE_NAME = "integrationTest"
const val FUNCTIONAL_TEST_SUITE_NAME = "functionalTest"

private const val DEFAULT_MIN_COVERAGE = 90

abstract class ConventionExtension @Inject constructor(objectFactory: ObjectFactory) {
    val minimumCoverage: Property<Int> = objectFactory.property(Int::class.java).convention(DEFAULT_MIN_COVERAGE)
    val pluginTags: ListProperty<String> = objectFactory.listProperty(String::class.java)
}

@Suppress("UnstableApiUsage")
open class Conventions : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            with(plugins) {
                apply(project.plugin("kotlin"))
                apply("org.gradle.kotlin.kotlin-dsl")
                apply(JavaGradlePluginPlugin::class)
                apply(project.plugin("shadow"))
                apply(VersionCatalogPlugin::class)
                apply(JvmTestSuitePlugin::class)
                apply(MavenPublishPlugin::class)
                apply(PublishPlugin::class)
                apply(project.plugin("detekt"))
                apply(project.plugin("ktlint"))
                apply(project.plugin("versionCheck"))
                apply(project.plugin("kotlinxKover"))
            }
            val extenstion = project.extensions.create("conventions", ConventionExtension::class.java)

            group = "io.github.flaxoos"

            repositories {
                google()
                mavenCentral()
                gradlePluginPortal()
            }

            dependencies {
                IMPLEMENTATION(gradleApi())
                IMPLEMENTATION(project.library("logback-classic"))
                IMPLEMENTATION(project(":common"))
            }

            setupGradle(project)

            setupKotlin()

            setupTestSuites()

            setupJar()

            configureKover()

            configureKtlint()

            configureDetekt()

            setupGradlePlugin(
                testSourceSets = the<TestingExtension>().suites.let { testSuites ->
                    listOf(FUNCTIONAL_TEST_SUITE_NAME).map { testSuiteName ->
                        testSuites.named(testSuiteName, JvmTestSuite::class.java).map { it.sources }
                    }
                },
                name = project.name,
                tags = extenstion.pluginTags,
            )

            setupPublishing(project)
        }
    }
}

private fun Project.setupGradle(project: Project) {
    tasks.withType<Wrapper> {
        gradleVersion = project.versionOf("gradle")
        distributionType = Wrapper.DistributionType.BIN
    }
}

private fun Project.setupKotlin() {
    extensions.findByType(KotlinJvmProjectExtension::class)?.apply {
        jvmToolchain(versionOf("java").toInt())
    }

    tasks.withType(KotlinCompilationTask::class) {
        compilerOptions {
            freeCompilerArgs.add("-Xcontext-receivers")
        }
    }
}

@Suppress("UnstableApiUsage")
private fun Project.setupTestSuites() {
    the<TestingExtension>().apply {
        listOf(INTEGRATION_TEST_SUITE_NAME, FUNCTIONAL_TEST_SUITE_NAME).map { testSuiteName ->
            val testSuite = suites.register<JvmTestSuite>(testSuiteName) {
                dependencies {
                    implementation.add(project())
                    implementation.add(gradleTestKit())
                }
                targets.apply {
                    this.all {
                        testTask.configure {
                            shouldRunAfter(tasks.named("test"))
                        }
                    }
                }
            }

            // Expose project dependencies to tests suites
            the<KotlinJvmProjectExtension>().apply {
                target.compilations.named(testSuiteName) {
                    associateWith(target.compilations.named("main").get())
                }
            }
            // Exclude test suites from kover
            the<KoverProjectExtension>().apply {
                excludeSourceSets {
                    names(testSuite.get().sources.name)
                }
            }

            tasks.withType(ShadowJar::class) {
                this.archiveClassifier.set(provider { null })
            }
        }

        // Add testing framework
        suites.withType<JvmTestSuite> {
            dependencies {
                implementation.add(library("kotest-runner-junit5"))
                implementation.add(library("kotest-framework-datatest"))
                implementation.add(library("kotest-assertions-core"))
                implementation.add(library("mockk"))
            }
        }
    }
    tasks.withType(Test::class) {
        useJUnitPlatform()
    }
}

private fun Project.setupJar() {
    the<JavaPluginExtension>().apply {
        withJavadocJar()
        withSourcesJar()
        tasks.withType(ShadowJar::class) {
            exclude("org/gradle/**")
            this.archiveClassifier.set(provider { null })
        }
    }
}


@Suppress("UnstableApiUsage")
fun Project.setupGradlePlugin(name: String, testSourceSets: List<Provider<SourceSet>>, tags: ListProperty<String>) {
    the<GradlePluginDevelopmentExtension>().apply {
        website.set("https://github.com/flaxoos/flax-gradle-plugins")
        vcsUrl.set("https://github.com/flaxoos/flax-gradle-plugins")
        plugins {
            afterEvaluate {
                create("$group.$name") {
                    id = property("ID").toString()
                    implementationClass = property("IMPLEMENTATION_CLASS").toString()
                    description = property("DESCRIPTION").toString()
                    displayName = property("DISPLAY_NAME").toString()
                    this.tags.set(tags.get())
                }
            }
        }
        @Suppress("SpreadOperator")
        testSourceSets(*testSourceSets.map { it.get() }.toTypedArray())
        pluginSourceSet(
            the<SourceSetContainer>().named("main").get(),
        )
    }
}

private fun Project.configureKover() {
    the(KoverReportExtension::class).apply {
        defaults {
            html { onCheck = true }
            verify {
                rule {
                    isEnabled = true
                    afterEvaluate {
                        minBound(the(ConventionExtension::class).minimumCoverage.get())
                    }
                }
                onCheck = true
            }
            filters {
                excludes {
                    annotatedBy("io.github.flaxoos.KoverIgnore")
                }
            }
        }
    }
}

private fun Project.configureKtlint() {
    tasks.named("ktlintCheck") {
        dependsOn("ktlintFormat")
    }
}

private fun Project.configureDetekt() {
    the<DetektExtension>().apply {
        config.from(files(rootDir.parentFile.resolve("config/detekt/detekt.yml")))
    }
    tasks.named("build") {
        dependsOn(tasks.named("detekt"))
    }
}

private fun Project.setupPublishing(project: Project) {
    the(PublishingExtension::class).apply {
        repositories {
            maven {
                name = "GitHubPackages"
                url =
                    URI(
                        "https://maven.pkg.github.com/flaxoos/${
                            project.findProperty("github.repository.name") ?: project.name
                        }",
                    )
                gprWriteCredentials()
            }
            mavenLocal()
        }
    }

    tasks.register("createReleaseTag") {
        doLast {
            createReleaseTag()
        }
    }.let { tasks.named("publish") { dependsOn(it) } }
}

/**
 * Deletes the current tag and recreates it
 */
@Suppress("TooGenericExceptionCaught")
private fun Project.createReleaseTag() {
    val tagName = "release/$version"
    try {
        runCommands("git", "tag", "-d", tagName)
    } catch (e: Exception) {
        logger.warn(
            "Failed deleting release tag. if the tag $tagName doesn't exist then this is expected. " +
                e.message,
        )
    }
    runCommands("git", "status")
    runCommands("git", "tag", tagName)
}

/**
 * Run a command
 */
private fun runCommands(vararg commands: String): String {
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

context(Project)
private fun MavenArtifactRepository.gprWriteCredentials() {
    credentials {
        username = gprUser
        password = gprWriteToken
    }
}

context(Project)
@Suppress("UnusedPrivateMember", "UNUSED")
private fun MavenArtifactRepository.gprReadCredentials() {
    credentials {
        username = gprUser
        password = gprReadToken
    }
}

private val Project.gprWriteToken
    get() = findProperty("gpr.write.key") as String? ?: System.getenv("GPR_WRITE_TOKEN")

private val Project.gprReadToken
    get() = findProperty("gpr.read.key") as String? ?: System.getenv("GPR_READ_TOKEN")

private val Project.gprUser
    get() = findProperty("gpr.user") as String? ?: System.getenv("GPR_USER")

fun Project.libs(): Optional<VersionCatalog> = project.the<VersionCatalogsExtension>().find("libs")

fun Project.versionOf(version: String): String =
    this.libs().get().findVersion(version).get().toString()

fun Project.library(name: String): String =
    this.libs().get().findLibrary(name).get().get().toString()

fun Project.plugin(name: String): String =
    this.libs().get().findPlugin(name).get().get().pluginId
