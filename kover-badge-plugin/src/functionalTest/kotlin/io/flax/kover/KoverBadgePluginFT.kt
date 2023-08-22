package io.flax.kover

import com.github.syari.kgit.KGit
import io.flax.kover.ColorBand.Companion.from
import io.flax.kover.GitCommitOption.Add
import io.flax.kover.GitCommitOption.Commit
import io.flax.kover.GitCommitOption.CommitReadme
import io.flax.kover.GitCommitOption.CommitToNewBranch
import io.flax.kover.GitCommitOption.DontCommit
import io.flax.kover.Names.KOVER_BADGE_TASK_NAME
import io.flax.kover.RegexGroupMatcher.Companion.shouldHaveGroupMatching
import io.flax.kover.TestInputs.POSSIBLE_LABELS
import io.flax.kover.TestInputs.POSSIBLE_SPECTRUMS
import io.flax.kover.TestInputs.randomLabel
import io.flax.kover.TestInputs.randomSpectrum
import io.kotest.assertions.asClue
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.inspectors.forAny
import io.kotest.inspectors.forNone
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import kotlinx.css.Color
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.treewalk.AbstractTreeIterator
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.gradle.testkit.runner.GradleRunner
import java.io.File
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.math.ceil
import kotlin.random.Random

private const val README_FILE_PATH = "TEST_README.md"
private const val NEW_BRANCH_NAME = "new-branch"

class KoverBadgePluginFT : FunSpec() {
    private lateinit var testProjectDir: File
    private lateinit var settingsFile: File
    private lateinit var buildFile: File
    private lateinit var mainFile: File
    private lateinit var testFile: File
    private lateinit var readmeFile: File
    private lateinit var gitDir: File
    private lateinit var git: KGit

    init {
        beforeTest {
            testProjectDir = createTempDirectory().toFile()
            settingsFile = File(testProjectDir, "settings.gradle.kts")
                .apply { writeText(readTestProjectFile("settings-gradle")) }
            buildFile = File(testProjectDir, "build.gradle.kts")
            readmeFile = File(testProjectDir, README_FILE_PATH)
            mainFile = File(testProjectDir, "src/main/kotlin/Main.kt")
                .also { it.createAndWriteText(readTestProjectFile("Main")) }
            testFile = File(testProjectDir, "src/test/kotlin/TestMain.kt")
                .also { it.createAndWriteText(readTestProjectFile("Test")) }

            // git
            File(testProjectDir, ".gitignore").createAndWriteText(readTestProjectFile("gitignore"))
            gitDir = File(testProjectDir, ".git").also { it.mkdir() }
            git = KGit.init {
                this.setGitDir(gitDir)
                setDirectory(testProjectDir)
            }
        }

        afterTest {
            git.close()
            testProjectDir.deleteRecursively()
        }
        context("Kover Badge Plugin Tests") {
            context("Should Generate Badge with different color bands, labels, and styles") {
                val labels = List(POSSIBLE_LABELS) { randomLabel() }
                val spectrums = List(POSSIBLE_SPECTRUMS) { randomSpectrum() }
                val cases = Style.values().flatMap { badgeStyle ->
                    labels.flatMap { badgeLabel ->
                        spectrums.flatMap { spectrum ->
                            spectrum.windowed(2, partialWindows = true).map { colorPair ->
                                val targetColorRange = colorPair[0] to colorPair.getOrNull(1)
                                KoverBadgeTestCase(
                                    targetColorRange = targetColorRange,
                                    badgeLabel = badgeLabel,
                                    badgeStyle = badgeStyle,
                                    applyKoverPlugin = true,
                                    spectrum = spectrum,
                                    gitCommitOption = DontCommit,
                                )
                            }
                        }
                    }
                }
                withData(nameFn = { it.name }, cases) { case ->
                    with(case) {
                        buildFile.writeText(editBuildFile(readTestProjectFile("build-gradle")))
                        readmeFile.createAndWriteText(editReadmeFile(readTestProjectFile("README")))
                        enableTestsForDesiredCoverage()

                        GradleRunner.create()
                            .withProjectDir(testProjectDir)
                            .withPluginClasspath()
                            .withArguments(KOVER_BADGE_TASK_NAME, "--stacktrace")
                            .build()

                        val updatedReadmeContent = File(testProjectDir, README_FILE_PATH).readText()
                        val match = updatedReadmeContent.lines()
                            .firstNotNullOfOrNull { line -> expectedKoverBadgeRegex.find(line) }
                        with(
                            withClue("no match for ${expectedKoverBadgeRegex.pattern} in $updatedReadmeContent") {
                                match.shouldNotBeNull()
                            },
                        ) {
                            this shouldHaveGroupMatching expectedHrefPattern
                            this shouldHaveGroupMatching expectedColorBand
                            this shouldHaveGroupMatching expectedColor
                            this shouldHaveGroupMatching expectedBadgeLabel
                            this shouldHaveGroupMatching expectedBadgeStyle
                        }
                    }
                }
            }
            context("Should handle git action") {
                val spectrum = randomSpectrum()
                withData(
                    nameFn = { "Should handle git action: ${it::class.simpleName}" },
                    listOf(DontCommit, Add, CommitReadme(), Commit(), CommitToNewBranch(branchName = NEW_BRANCH_NAME)),
                ) { gitCommitOption ->
                    with(
                        KoverBadgeTestCase(
                            applyKoverPlugin = true,
                            targetColorRange = spectrum[0] to spectrum[1],
                            badgeLabel = randomLabel(),
                            badgeStyle = Style.ForTheBadge,
                            spectrum = spectrum,
                            gitCommitOption = gitCommitOption,
                        ),
                    ) {
                        buildFile.writeText(editBuildFile(readTestProjectFile("build-gradle")))
                        readmeFile.createAndWriteText(editReadmeFile(readTestProjectFile("README")))

                        val projectFiles =
                            listOf(buildFile, settingsFile, mainFile, testFile, readmeFile).map {
                                it.relativeTo(
                                    testProjectDir,
                                )
                            }
                        val firstCommitMessage = "First Commit"
                        git.add { addFilepattern(".gitignore") }
                        git.status().added.shouldHaveSize(1)
                        val firstCommitId = git.commit { message = firstCommitMessage }.id.name

                        GradleRunner.create()
                            .withProjectDir(testProjectDir)
                            .withPluginClasspath()
                            .withArguments(KOVER_BADGE_TASK_NAME, "--stacktrace")
                            .build().output.let { println(it) }

                        val addedBadgeMessage = "Added $badgeLabel Badge"
                        when (gitCommitOption) {
                            Add -> {
                                git.status().added.toSet() shouldBe projectFiles.map { it.path }.toSet()
                                git.log().map { it.fullMessage }.forNone { it.shouldContain(addedBadgeMessage) }
                            }

                            is Commit -> {
                                git.shouldHaveDiffsOfExactly(firstCommitId, projectFiles)
                                git.log().map { it.fullMessage }.forAny { it.shouldContain(addedBadgeMessage) }
                            }

                            is CommitReadme -> {
                                git.shouldHaveDiffsOfExactly(
                                    firstCommitId,
                                    projectFiles.filter { it.name == readmeFile.name },
                                )
                                git.log().map { it.fullMessage }.forAny { it.shouldContain(addedBadgeMessage) }
                            }

                            is CommitToNewBranch -> {
                                git.shouldHaveDiffsOfExactly(firstCommitId, projectFiles, branch = NEW_BRANCH_NAME)
                                git.log().map { it.fullMessage }.forAny { it.shouldContain(addedBadgeMessage) }
                            }

                            DontCommit -> {
                                git.shouldHaveDiffsOfExactly(firstCommitId, projectFiles)
                                git.log().map { it.fullMessage }.forNone { it.shouldContain(addedBadgeMessage) }
                            }
                        }
                    }
                }
                test("Should not handle git action on CI") {
                    with(
                        KoverBadgeTestCase(
                            applyKoverPlugin = true,
                            targetColorRange = spectrum[0] to spectrum[1],
                            badgeLabel = randomLabel(),
                            badgeStyle = Style.ForTheBadge,
                            spectrum = spectrum,
                            gitCommitOption = Commit(),
                            isCi = true,
                        ),
                    ) {
                        buildFile.writeText(editBuildFile(readTestProjectFile("build-gradle")))
                        readmeFile.createAndWriteText(editReadmeFile(readTestProjectFile("README")))

                        val projectFiles =
                            listOf(buildFile, settingsFile, mainFile, testFile, readmeFile).map {
                                it.relativeTo(
                                    testProjectDir,
                                )
                            }
                        val firstCommitMessage = "First Commit"
                        git.add { addFilepattern(".gitignore") }
                        git.status().added.shouldHaveSize(1)
                        val firstCommitId = git.commit { message = firstCommitMessage }.id.name

                        GradleRunner.create()
                            .withProjectDir(testProjectDir)
                            .withPluginClasspath()
                            .withArguments(KOVER_BADGE_TASK_NAME, "--stacktrace")
                            .build().output.let { println(it) }

                        val addedBadgeMessage = "Added $badgeLabel Badge"
                        git.shouldHaveDiffsOfExactly(firstCommitId, projectFiles)
                        git.log().map { it.fullMessage }.forNone { it.shouldContain(addedBadgeMessage) }
                    }
                }
            }

            test("Should not register task if no kover plugin is applied") {
                with(
                    KoverBadgeTestCase(
                        applyKoverPlugin = false,
                        // rest doesn't matter
                        targetColorRange = Pair("red" from 0f, "green" from 100f),
                        badgeLabel = "",
                        badgeStyle = Style.ForTheBadge,
                        spectrum = listOf(),
                        gitCommitOption = DontCommit,
                    ),
                ) {
                    buildFile.writeText(editBuildFile(readTestProjectFile("build-gradle")))
                    readmeFile.createAndWriteText(editReadmeFile(readTestProjectFile("README")))

                    val result = GradleRunner.create()
                        .withProjectDir(testProjectDir)
                        .withPluginClasspath()
                        .withArguments(KOVER_BADGE_TASK_NAME, "--stacktrace")
                        .buildAndFail()

                    result.output.shouldContain("Task '$KOVER_BADGE_TASK_NAME' not found in root project ")
                }
            }
        }
    }

    private fun KGit.shouldHaveDiffsOfExactly(
        firstCommitId: String,
        files: List<File>,
        shouldHaveParent: Boolean = true,
        branch: String? = null,
    ) {
        fun prepareTreeParser(ref: String): AbstractTreeIterator {
            val objectId = repository.resolve("$ref^{tree}")
            val reader = repository.newObjectReader()

            return CanonicalTreeParser(null, reader, objectId)
        }

        val oldTree = prepareTreeParser(firstCommitId)
        val newTree = (
            branch?.let { "refs/heads/$it" }
                ?: log().firstOrNull { rev -> rev.parents.any { it.name == firstCommitId } }?.id?.name
            )?.let {
            prepareTreeParser(it)
        }
        if (!shouldHaveParent) {
            oldTree.shouldBeNull()
            return
        }
        val diffs: List<DiffEntry> = diff {
            setNewTree(newTree)
            setOldTree(oldTree)
        }
        val fileNames = files.map { it.path }.toSet()
        val diffNewPaths = diffs.map { it.newPath }.toSet()
        diffNewPaths shouldBe fileNames
    }

    private fun KoverBadgeTestCase.enableTestsForDesiredCoverage() {
        var tests = 0
        val pctCoveragePerTest = 100f / mainFile.readText().lines().count { line ->
            line.contains("fun")
        }.toFloat()
        val desiredTests = ceil(targetColorRange.first.lowerThreshold / pctCoveragePerTest)
        testFile.writeText(
            testFile.readText().lines().joinToString("\n") { line ->
                if (line.contains("@Ignore") && tests < desiredTests) {
                    line.replace("@Ignore", "").also { tests++ }
                } else {
                    line
                }
            },
        )
    }
}

private fun readTestProjectFile(fileName: String) =
    (
        KoverBadgePluginFT::class.java.getResource("/test-project-files/$fileName")?.readText()
            ?: error("File test-project-files/$fileName not found")
        )

private fun File.createAndWriteText(content: String) {
    this.toPath().parent.createDirectories()
    this.createNewFile()
    writeText(content)
}

data class RegexGroupMatcher<EXPECTED>(
    val groupName: String,
    val expected: EXPECTED,
    val checkExpected: EXPECTED.(String) -> String = { actual ->
        actual shouldBe toString()
        actual
    },
) {
    internal companion object {
        infix fun <EXPECTED> MatchResult.shouldHaveGroupMatching(regexGroupMatcher: RegexGroupMatcher<EXPECTED>) =
            with(
                this.groups[regexGroupMatcher.groupName].asClue { matchGroup ->
                    matchGroup.shouldNotBeNull()
                },
            ) { regexGroupMatcher.checkExpected(regexGroupMatcher.expected, this.value) }
    }
}

private data class KoverBadgeTestCase(
    val targetColorRange: Pair<ColorBand, ColorBand?>,
    val applyKoverPlugin: Boolean,
    val badgeLabel: String,
    val badgeStyle: Style,
    val spectrum: List<ColorBand>,
    val gitCommitOption: GitCommitOption,
    val isCi: Boolean = false,
) {
    private companion object {
        private const val KOVER_PLUGIN_PLACEHOLDER = "\$KOVER_PLUGIN"
        private const val KOVER_ID_PLACEHOLDER = "id(\"org.jetbrains.kotlinx.kover\") version \"0.7.3\""
        private const val KOVER_REPORT_PLACEHOLDER = "\$KOVER_REPORT"
        private const val BADGE_LABEL_PLACEHOLDER = "\$BADGE_LABEL"
        private const val README_FILE_PATH_PLACEHOLDER = "\$README_FILE_PATH"
        private const val BADGE_STYLE_PLACEHOLDER = "\$BADGE_STYLE"
        private const val SPECTRUM_PLACEHOLDER = "\$SPECTRUM"
        private const val GIT_ACTION_PLACEHOLDER = "\$GIT_ACTION"
        private const val IS_CI_PLACEHOLDER = "\$IS_CI"
    }

    val name =
        "Should create ${targetColorRange.first.colorName} badge with given label and given style if coverage is between ${targetColorRange.first.lowerThreshold} and ${targetColorRange.second?.lowerThreshold ?: 100}. Test case: $this: "
    val expectedKoverBadgeRegex = Style.values().joinToString(separator = "|") { it.value }.let { styles ->
        (
            "<a href=\"(?<HREF>[^\"]+)\">!" +
                "\\[(?<BADGE>[^]]+)]\\(https://img\\.shields\\.io/badge/(?<COVERAGE>\\d+(\\.\\d+)?)-(?<COLOR>\\w+)\\?" +
                "logo=kotlin&label=(?<LABEL>(\\b\\w+\\b)+)&style=(?<STYLE>(?:$styles))\\)</a>"
            ).toRegex()
    }

    val expectedHrefPattern =
        RegexGroupMatcher(
            "HREF",
            "file:/(.+)/build/reports/kover/html/index.html".toRegex(),
        ) { it.shouldMatch(this) }

    val expectedColorBand =
        RegexGroupMatcher("COVERAGE", (targetColorRange)) { actual ->
            actual.toFloat() shouldBeGreaterThanOrEqual targetColorRange.first.lowerThreshold
            actual.toFloat() shouldBeLessThanOrEqual (targetColorRange.second?.lowerThreshold ?: 100f)
            actual
        }

    val expectedBadgeLabel = RegexGroupMatcher("LABEL", badgeLabel)
    val expectedBadgeStyle = RegexGroupMatcher("STYLE", badgeStyle) {
        name.lowercase().replace("-", "") shouldBe it.replace("-", "")
    }
    val expectedColor = RegexGroupMatcher("COLOR", targetColorRange.first.colorName)

    fun editBuildFile(buildFileContent: String): String {
        return buildFileContent.replace(
            KOVER_PLUGIN_PLACEHOLDER,
            if (this.applyKoverPlugin) KOVER_ID_PLACEHOLDER else "",
        ).replace(
            KOVER_REPORT_PLACEHOLDER,
            if (this.applyKoverPlugin) {
                """
                koverReport {
                    defaults {
                        verify {
                            rule {
                                isEnabled = true
                                minBound(${spectrum.maxBy { it.lowerThreshold }.lowerThreshold.toInt()})
                            }
                            onCheck = true
                        }
                    }
                }
                """.trimIndent()
            } else {
                ""
            },
        )
            .replace(BADGE_LABEL_PLACEHOLDER, badgeLabel.quoted())
            .replace(README_FILE_PATH_PLACEHOLDER, README_FILE_PATH.quoted())
            .replace(BADGE_STYLE_PLACEHOLDER, this.badgeStyle.name)
            .replace(
                SPECTRUM_PLACEHOLDER,
                "listOf(${spectrum.joinToString(separator = ", ") { "${it.colorName.quoted()} from ${it.lowerThreshold}f" }})",
            ).replace(
                GIT_ACTION_PLACEHOLDER,
                gitCommitOption::class.simpleName +
                    when {
                        gitCommitOption in listOf(DontCommit, Add) -> ""
                        gitCommitOption::class == CommitToNewBranch::class -> "(branchName = ${NEW_BRANCH_NAME.quoted()})"
                        else -> "()"
                    },
            ).replace(IS_CI_PLACEHOLDER, this.isCi.toString())
    }

    fun editReadmeFile(readmeFileContent: String): String {
        return readmeFileContent.replace(BADGE_LABEL_PLACEHOLDER, badgeLabel.replace(" ", ""))
    }

    private fun Any.quoted() = "\"$this\""
}

private object TestInputs {
    const val LABEL_LENGTH = 10
    const val SPECTRUM_BAND_COUNT = 3
    const val POSSIBLE_LABELS = 1
    const val POSSIBLE_SPECTRUMS = 1
    val labelCharPool: List<Char> = ('a'..'z') + ('A'..'Z') + ' '
    val colorPool: Set<Color> = setOf(Color.green, Color.yellow, Color.red, Color.white, Color.gold, Color.gray)
    val random = Random(0)
    fun randomLabel() = (1..LABEL_LENGTH)
        .map { random.nextInt(0, labelCharPool.size).let { labelCharPool[it] } }
        .joinToString("")

    fun randomSpectrum(): List<ColorBand> {
        if (SPECTRUM_BAND_COUNT > colorPool.size) throw IllegalArgumentException("bands cannot exceed colorPool size for unique colors")
        val decimalFormat = "%.2f"
        val bandRange = decimalFormat.format(100f / SPECTRUM_BAND_COUNT).toFloat()
        return colorPool.shuffled(random).take(SPECTRUM_BAND_COUNT).mapIndexed { i, c ->
            c.value from i * bandRange
        }.sortedBy { it.lowerThreshold }
    }
}
