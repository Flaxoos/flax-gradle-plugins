package io.flax.kover

import io.flax.kover.ColorBand.Companion.from
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.io.path.createTempDirectory

class KoverBadgeTaskIT : FunSpec() {

    init {
        lateinit var project: Project
        lateinit var task: KoverBadgeTask
        lateinit var testLogFile: File
        lateinit var testReadme: File

        beforeTest {
            project = ProjectBuilder.builder().build()
        }
        afterTest {
            testLogFile.delete()
            testReadme.delete()
        }

        context("KoverBadgeTask Test") {

            test("should generate the badge link in README with html report link") {
                val coverage = 85.0
                val color = "blue"
                val label = "test-label"
                val style = Style.Flat

                task = project.tasks.register("addKoverBadge", KoverBadgeTask::class.java).get()
                testLogFile = File.createTempFile("coverage", ".log")
                testLogFile.writeText("application line coverage: $coverage%")

                testReadme = File.createTempFile("TEST_README", ".md")
                testReadme.writeText("<a>![koverage](place_holder)</a>")

                val (testHtmlReportDir, testHtmlReport) = createTempDirectory().toFile().let { tmpDir ->
                    tmpDir to File(tmpDir, "index.html").also { file ->
                        file.createNewFile()
                        file.writeText("Sample report")
                    }
                }
                with(task) {
                    coverageLogFile.set(testLogFile)
                    readme.set(testReadme)
                    coverageHtmlReportDir.set(testHtmlReportDir)
                    badgeLabel.set(label)
                    badgeStyle.set(style)
                    spectrum.set(listOf(color from 80.0f))

                    editReadme()
                }

                testReadme.readText() shouldBe "<a href=\"${testHtmlReport.toURI()}\">![$label](https://img.shields.io/badge/$coverage-$color?logo=kotlin&label=$label&style=${style.name.lowercase()})</a>"
            }
            // TODO: Add more tests for different scenarios
        }
    }
}
