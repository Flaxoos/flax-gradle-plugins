package io.flax.kover.gradle.testkit

import org.gradle.api.Project
import java.io.File

internal object Names {
    const val KOVER_AGENT_RUNTIME_CONFIGURATION_NAME = "koverAgentRuntime"
    const val KOVER_REPORTER_RUNTIME_CONFIGURATION_NAME = "koverReporterRuntime"

    // Below are as defined in kotlinx.kover.gradle.plugin.tools.kover.KoverTool
    const val KOVER_TOOL_GROUP_NAME = "org.jetbrains.intellij.deps"
    const val KOVER_TOOL_AGENT_NAME = "intellij-coverage-agent"
    const val KOVER_TOOL_REPORTER_NAME = "intellij-coverage-reporter"
}

internal fun Project.testKitDir(taskName: String = "test") = File(buildDir, "testkit/$taskName")

internal fun generatePropertiesTaskName(taskName: String) =
    "generateKover${taskName.replaceFirstChar(Char::uppercase)}KitProperties"
