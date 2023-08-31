package io.github.flaxoos

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import javax.inject.Inject
import kotlin.reflect.KClass

open class PluginTestProjectConventionsExtension @Inject constructor(objects: ObjectFactory) {
    val plugin: Property<KClass<out Plugin<Project>>> = objects.property()
    val tasksToExecute: ListProperty<TaskProvider<Task>> = objects.listProperty<TaskProvider<Task>>()
    var acceptanceBlock: Property<(Project) -> Unit> = objects.property()
}

class PluginTestProjectConventions : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            val extension =
                project.extensions.create("pluginTestProject", PluginTestProjectConventionsExtension::class.java)

            afterEvaluate {
                pluginManager.apply(extension.plugin.get())

                val acceptPlugin = createAcceptanceTask(project, extension)

                val finalTask =
                    extension.tasksToExecute.map { it.lastOrNull()?.get() ?: tasks.named("build").get() }.get()
                finalTask.finalizedBy(acceptPlugin)
            }
        }
    }

    private fun createAcceptanceTask(
        project: Project,
        extension: PluginTestProjectConventionsExtension
    ): Task = project.tasks.create("acceptPlugin") {
        group = "verification"
        description = "Accepts the plugin if it is working as expected."

        doLast {
            try {
                extension.acceptanceBlock.get()(project)
            } catch (e: AssertionError) {
                throw GradleException(
                    "❌ Rejected plugin: ${extension.plugin.get().simpleName} \uD83E\uDEE4",
                    e
                )
            }
            logger.quiet("✅ Accepted plugin: ${extension.plugin.get().simpleName} \uD83E\uDEE1")
        }
    }
}
