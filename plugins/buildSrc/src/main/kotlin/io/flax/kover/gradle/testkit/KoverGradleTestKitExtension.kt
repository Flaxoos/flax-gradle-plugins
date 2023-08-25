package io.flax.kover.gradle.testkit

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.property
import javax.inject.Inject

/**
 * Inspired by https://github.com/koral--/kover-gradle-testkit-plugin
 */
open class KoverGradleTestKitExtension @Inject constructor(private val project: Project, objects: ObjectFactory) {

    /**
     *  The path to the kover agent, not required, defaults to the jar provided by [KOVER_TOOL_GROUP_NAME]:[KOVER_TOOL_AGENT_NAME]
     */
    val koverAgentRuntimePathProvider: Provider<String> =
        project.provider { project.configurations.getByName(Names.KOVER_AGENT_RUNTIME_CONFIGURATION_NAME).asPath }

    /**
     * The path to the kover reporter, not required, defaults to the jar provided by [KOVER_TOOL_GROUP_NAME]:[KOVER_TOOL_REPORTER_NAME]
     */
    val koverReporterRuntimePathProvider: Provider<String> =
        project.provider { project.configurations.getByName(Names.KOVER_REPORTER_RUNTIME_CONFIGURATION_NAME).asPath }

    /**
     * The test configuration, for example, ```functionalTestImplementation```
     */
    val testConfigurationRuntime: Property<Configuration> = objects.property(Configuration::class.java)

    /**
     * The test task, for example,
     * ```functionalTest```
     */
    val task: Property<TaskProvider<Test>> =
        objects.property<TaskProvider<Test>>()

    companion object {
        internal const val NAME = "koverGradleTestKit"
    }
}
