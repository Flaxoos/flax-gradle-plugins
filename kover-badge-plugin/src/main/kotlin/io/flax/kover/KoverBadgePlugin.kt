package io.flax.kover

import io.flax.kover.ColorBand.Companion.from
import io.flax.kover.Names.KOVER_BADGE_DEFAULT_LABEL
import io.flax.kover.Names.KOVER_BADGE_EXTENSION_NAME
import io.flax.kover.Names.KOVER_BADGE_TASK_GROUP_NAME
import io.flax.kover.Names.KOVER_BADGE_TASK_NAME
import kotlinx.kover.gradle.plugin.KoverGradlePlugin
import kotlinx.kover.gradle.plugin.dsl.koverHtmlReportName
import kotlinx.kover.gradle.plugin.dsl.koverLogName
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.findPlugin
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.process.ExecSpec
import java.io.File
import javax.inject.Inject

object Names {
    const val KOVER_BADGE_EXTENSION_NAME = "koverBadge"
    const val KOVER_BADGE_TASK_NAME = "addKoverBadge"
    const val KOVER_BADGE_TASK_GROUP_NAME = "documentation"
    const val KOVER_BADGE_DEFAULT_LABEL = "koverage"
}

/**
 * Plugin for embedding a badge in the project's readme for test coverage from [kotlinx.kover.gradle.plugin.KoverGradlePlugin]
 */
class KoverBadgePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            val extension = extensions.create(KOVER_BADGE_EXTENSION_NAME, KoverBadgePluginExtension::class.java)
            if (project.plugins.findPlugin(KoverGradlePlugin::class) == null) {
                logger.warn("Kover plugin not applied, kover-badge plugin has nothing to do")
                return@with
            }
            project.plugins.withType(KoverGradlePlugin::class) {
                val koverLogTask = tasks.getByName(tasks.koverLogName)
                val koverHtmlReportTask = tasks.getByName(tasks.koverHtmlReportName)

                tasks.register(KOVER_BADGE_TASK_NAME, KoverBadgeTask::class) {
                    group = KOVER_BADGE_TASK_GROUP_NAME
                    dependsOn(koverLogTask, koverHtmlReportTask)
                    mustRunAfter(koverLogTask, koverHtmlReportTask)
                    koverLogResultFile.set(koverLogTask.outputs.files.singleFile)
                    koverHtmlReportTask.outputs.files.singleFile.let {
                        if (it.exists()) koverHtmlReportDir.set(it)
                    }

                    readme.set(extension.readme)
                    badgeLabel.set(extension.badgeLabel.map { badgeContent ->
                        if (badgeContent.contains("_")) {
                            badgeContent.replace(" ", "-")
                                .also { logger.warn("spaces are not allowed in in badges, replaced with dash") }
                        } else badgeContent
                    })
                    badgeStyle.set(extension.badgeStyle)
                    spectrum.set(extension.spectrum)

                    doLast {
                        logger.quiet("Running KoverBadgeTask")

                        if (!extension.ciDetection.get().detect(project)) {
                            extension.handleGitCommitOption(this@register, this@with)
                        }
                    }
                }
            }
        }
    }

    private fun KoverBadgePluginExtension.handleGitCommitOption(
        koverBadgeTask: KoverBadgeTask,
        project: Project,
    ) {
        when (val option = this.gitAction.get()) {
            is GitCommitOptions.DontCommit -> {}

            is GitCommitOptions.Add,
            is GitCommitOptions.CommitReadme,
            is GitCommitOptions.Commit,
            is GitCommitOptions.CommitToNewBranch -> {
                fun ExecSpec.git(vararg args: String) {
                    try {
                        commandLine("git", *args)
                    } catch (e: Exception) {
                        project.logger.error("Failed to run git command", e)
                    }
                }

                val filesToStage = when (option) {
                    is GitCommitOptions.CommitReadme -> listOf(koverBadgeTask.readme.get().asFile.absolutePath)
                    else -> listOf("--all")
                }

                project.exec {
                    git("add", *filesToStage.toTypedArray())
                }

                if (option is GitCommitOptions.CommitOption) {
                    if (option is GitCommitOptions.CommitToNewBranch) {
                        project.exec {
                            git("checkout", "-b", option.branchName)
                        }
                    }
                    val message = option.message(this)
                    project.exec {
                        git("commit", "-m", message)
                    }
                }
            }
        }
    }
}

/**
 * Extension for [KoverBadgePlugin]
 */
open class KoverBadgePluginExtension @Inject constructor(objects: ObjectFactory) {

    /**
     * The project readme file, to place the badge in
     */
    val readme: RegularFileProperty =
        objects.fileProperty().convention(objects.fileProperty().fileValue(File("path/to/default/README.md")))

    /**
     * The label for the badge
     */
    val badgeLabel: Property<String> = objects.property(String::class.java)
        .convention(KOVER_BADGE_DEFAULT_LABEL)

    /**
     * The style of the badge
     */
    val badgeStyle: Property<Style> = objects.property(Style::class.java).convention(Style.Flat)

    /**
     * The color spectrum and coverage threshold for each color, meaning if coverage goes over a value,
     * the corresponding color will be used for the right side of the badge.
     * Default is red, yellow, green and 0, 50, 95, respectively.
     *
     * Note: Kover plugin and task configuration are internal, so there is no way to get the bounds set for any verify rules,
     * Therefor, the user must make sure the top color band threshold matches the bounds set in their rules, if they want
     * the badge to reflect the rules
     *
     * Can be initialized with the following DSL:
     * ```
     * listOf(
     *     "red" from 0.0f,
     *     "yellow" from 50.0f,
     *     "green" from 95.0f <-- ideally this would be the kover verify rule min bound
     * )
     * ```
     * The task will automatically sort the spectrum in any case
     */
    val spectrum: ListProperty<ColorBand> = objects.listProperty(ColorBand::class.java).convention(
        listOf(
            "red" from 0.0f,
            "yellow" from 50.0f,
            "green" from 95.0f
        )
    )

    /**
     * What git action should the task perform with the changes to the readme file
     * This option is only used if the task is not being executed within a CI/CD environment, as defined by the provided [ciDetection]
     */
    val gitAction: Property<GitCommitOptions> =
        objects.property(GitCommitOptions::class.java).convention(GitCommitOptions.Add)

    /**
     * Mechanism for determining if the task is being executed within a CI/CD environment.
     * By default, it checks for the presence of the "CI" environment variable.
     * Users can provide their own detection mechanism by overriding this property.
     */
    val ciDetection: Property<CiDetection> = objects.property(CiDetection::class.java).convention { _ ->
        System.getenv("CI")?.toBoolean() == true
    }
}

/**
 * Options for specifying the Git operations to perform after updating the README.
 */
sealed class GitCommitOptions {
    /**
     * Do not perform any Git operations after updating the README.
     */
    object DontCommit : GitCommitOptions()

    /**
     * Stage (add) the README to the Git index, but do not commit it.
     * Useful if there are additional changes that should be included in a single commit.
     */
    object Add : GitCommitOptions()

    sealed class CommitOption : GitCommitOptions() {
        abstract val message: KoverBadgePluginExtension.() -> String
    }

    /**
     * Stage (add) the README to the Git index and commit just the README.
     * This option ensures only changes to the README are committed, avoiding other unstaged changes.
     */
    class CommitReadme(override val message: KoverBadgePluginExtension.() -> String = defaultCommitMessage) :
        CommitOption()

    /**
     * Stage all changes (including the README) and commit them.
     * Be cautious using this option to ensure no unintended changes are committed.
     */
    class Commit(override val message: KoverBadgePluginExtension.() -> String = defaultCommitMessage) :
        CommitOption()

    /**
     * Stage all changes, create a new branch, and commit the changes to the new branch.
     * This option is useful for workflows that require changes to be made in separate branches.
     * The branch name will need to be specified separately.
     */
    data class CommitToNewBranch(
        override val message: KoverBadgePluginExtension.() -> String = defaultCommitMessage,
        val branchName: String
    ) : CommitOption()

    companion object {
        val defaultCommitMessage: KoverBadgePluginExtension.() -> String = {
            "Added ${this.badgeLabel.get()} Badge to ${this.readme.get().asFile.name}"
        }
    }
}

/**
 * Function for determining if the task is being executed within a CI/CD environment
 */
fun interface CiDetection {
    fun detect(project: Project): Boolean
}

