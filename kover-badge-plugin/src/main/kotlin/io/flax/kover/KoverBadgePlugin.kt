package io.flax.kover

import com.github.syari.kgit.KGit
import io.flax.kover.ColorBand.Companion.from
import io.flax.kover.KoverBadgeTask.Regexes.coverageRegex
import io.flax.kover.Names.KOVER_BADGE_DEFAULT_LABEL
import io.flax.kover.Names.KOVER_BADGE_EXTENSION_NAME
import io.flax.kover.Names.KOVER_BADGE_TASK_GROUP_NAME
import io.flax.kover.Names.KOVER_BADGE_TASK_NAME
import kotlinx.css.Color
import kotlinx.kover.gradle.plugin.KoverGradlePlugin
import kotlinx.kover.gradle.plugin.dsl.koverHtmlReportName
import kotlinx.kover.gradle.plugin.dsl.koverLogName
import org.eclipse.jgit.lib.RepositoryBuilder
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.findPlugin
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
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
@Suppress("TooGenericExceptionCaught")
class KoverBadgePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        try {
            with(project) {
                val extension = extensions.create(KOVER_BADGE_EXTENSION_NAME, KoverBadgePluginExtension::class.java)
                if (project.hasNoKoverApplied()) {
                    logger.info("Kover plugin not applied, kover-badge plugin has nothing to do")
                    return@with
                }
                project.plugins.withType(KoverGradlePlugin::class) {
                    tasks.register(KOVER_BADGE_TASK_NAME, KoverBadgeTask::class) {
                        val koverLogTask = tasks.find { it.name.contains(tasks.koverLogName) }
                            ?: error("No ${tasks.koverLogName} task found")
                        val koverHtmlReportTask = tasks.find { it.name.contains(tasks.koverHtmlReportName) }

                        group = KOVER_BADGE_TASK_GROUP_NAME
                        dependsOn(koverLogTask, koverHtmlReportTask)
                        mustRunAfter(koverLogTask, koverHtmlReportTask)

                        setConditions(koverLogTask)

                        setInputs(koverLogTask, koverHtmlReportTask, extension)
                        doLast {
                            if (!extension.ciDetection.get().detect(project)) {
                                extension.handleGitCommitOption(this@register, this@with)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            project.logger.error("Unexpected error: ${e.message}, please report this issue")
            throw e
        }
    }

    private fun KoverBadgeTask.setConditions(koverLogTask: Task) {
        onlyIf("${koverLogTask.name} has an output file containing pattern: \"${coverageRegex.pattern}\"") {
            val koverLogTaskOutputFile = koverLogTask.outputs.files.singleFile
            logger.info("${koverLogTask.name} output file content:\n${koverLogTaskOutputFile.readText()}")
            koverLogTask.outputs.files.singleFile.readText().contains(coverageRegex)
        }
    }

    private fun KoverBadgeTask.setInputs(
        koverLogTask: Task,
        koverHtmlReportTask: Task?,
        extension: KoverBadgePluginExtension,
    ) {
        fun <T> T?.logIfNull(message: String) = also {
            if (this == null) {
                logger.quiet("$message: is null")
            }
        }
        coverageLogFile.set(koverLogTask.outputs.files.singleFile)

        koverHtmlReportTask.logIfNull("koverHtmlReportTask")
            ?.outputs.logIfNull("koverHtmlReportTask.outputs")
            ?.files.logIfNull("koverHtmlReportTask.outputs.files")
            ?.singleFile.logIfNull("koverHtmlReportTask.outputs.files.singleFile")
            ?.let { coverageHtmlReportDir.set(it) }
        readme.set(extension.readme)
        badgeLabel.set(extension.badgeLabel)
        badgeStyle.set(extension.badgeStyle)
        spectrum.set(extension.spectrum)
    }

    @Suppress("NestedBlockDepth", "TooGenericExceptionCaught")
    private fun KoverBadgePluginExtension.handleGitCommitOption(
        koverBadgeTask: KoverBadgeTask,
        project: Project,
    ) {
        when (val option = this.gitAction.get()) {
            is GitCommitOption.DontCommit -> {}
            else -> {
                try {
                    val gitDir =
                        this@handleGitCommitOption.gitRepositoryDirectory
                            .getOrElse(project.layout.projectDirectory.dir(".git"))

                    val repoBuilder = RepositoryBuilder().setWorkTree(project.projectDir).setGitDir(gitDir.asFile)
                    if (!repoBuilder.readEnvironment().findGitDir().gitDir.exists()) {
                        project.logger.warn("No .git dir found at: $gitDir, skipping git commit")
                        return
                    }

                    val git = KGit(repoBuilder.build())

                    val filePattern = when (option) {
                        is GitCommitOption.CommitReadme ->
                            koverBadgeTask.readme.get().asFile.relativeTo(project.projectDir).path

                        else -> "."
                    }

                    git.add {
                        addFilepattern(filePattern)
                    }

                    if (option is GitCommitOption.CommitOption) {
                        if (option is GitCommitOption.CommitToNewBranch) {
                            git.checkout {
                                setName(option.branchName)
                                setCreateBranch(true)
                            }
                        }
                        val message = option.message(this)
                        git.commit {
                            if (GitCommitOption::class == GitCommitOption.CommitReadme::class) {
                                this.setOnly(
                                    readme.get().asFile.relativeTo(
                                        project.projectDir,
                                    ).path,
                                )
                            }
                            this.message = message
                        }
                    }
                } catch (e: Exception) {
                    project.logger.error("Git commit option ${option::class.simpleName} failed: ${e.localizedMessage}")
                    throw e
                }
            }
        }
    }
}

private const val DEFAULT_LOWER_BAND_THRESHOLD = 0.0f
private const val DEFAULT_MID_BAND_THRESHOLD = 50.0f
private const val DEFAULT_UPPER_BAND_THRESHOLD = 100.0f

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
     * Note: Kover plugin and task configuration are internal,
     * so there is no way to get the bounds set for any verify rules.
     * Therefor, the user must make sure the top color band threshold matches the bounds set in their rules,
     * if they want the badge to reflect the rules
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
            Color.red from DEFAULT_LOWER_BAND_THRESHOLD,
            Color.yellow from DEFAULT_MID_BAND_THRESHOLD,
            Color.green from DEFAULT_UPPER_BAND_THRESHOLD,
        ),
    )

    /**
     * The directory where the git repository is, if not specified, defaults to the project root
     */
    val gitRepositoryDirectory: DirectoryProperty = objects.directoryProperty()

    /**
     * What git action should the task perform with the changes to the readme file
     * This option is only used if the task is not being executed within a CI/CD environment, as defined by the provided [ciDetection]
     */
    val gitAction: Property<GitCommitOption> =
        objects.property(GitCommitOption::class.java).convention(GitCommitOption.Add)

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
sealed class GitCommitOption {
    /**
     * Do not perform any Git operations after updating the README.
     */
    object DontCommit : GitCommitOption()

    /**
     * Stage (add) the README to the Git index, but do not commit it.
     * Useful if there are additional changes that should be included in a single commit.
     */
    object Add : GitCommitOption()

    sealed class CommitOption : GitCommitOption() {
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
        val branchName: String,
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

private fun Project.hasNoKoverApplied() = this.plugins.findPlugin(KoverGradlePlugin::class) == null
