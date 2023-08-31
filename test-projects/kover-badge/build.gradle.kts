import io.github.flaxoos.kover.ColorBand.Companion.from
import io.github.flaxoos.kover.KoverBadgePlugin
import io.github.flaxoos.kover.KoverBadgeTask
import io.github.flaxoos.kover.Style
import io.kotest.matchers.shouldBe

plugins {
    id("io.github.flaxoos.plugin-test-project-conventions")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlinxKover)
    id("io.github.flaxoos.kover-badge")
}

pluginTestProject {
    plugin.set(KoverBadgePlugin::class)
    tasksToExecute.set(listOf(tasks.named("koverHtmlReport")))
    acceptanceBlock.set {
        it.layout.projectDirectory.file("TEST_README.md").asFile.readText().lines().first() shouldBe
                "<a href=\"file:/Users/ido/IdeaProjects/flax-gradle-plugins-copy/test-projects/kover-badge-plugin/build/reports/kover/html/index.html\">![koverage](https://img.shields.io/badge/100.0-green?logo=kotlin&label=koverage&style=plastic)</a>"
    }
}

dependencies {
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.mockk)
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

koverReport {
    defaults {
        html { onCheck = true }
        verify {
            rule {
                isEnabled = true
                minBound(90)
            }
            onCheck = true
        }
    }
}

koverBadge {
    badgeLabel.set("koverage")
    readme.set(file("TEST_README.md"))
    badgeStyle.set(Style.Plastic)
    spectrum.set(
        listOf(
            "red" from 0.0f,
            "yellow" from 50.0f,
            "green" from 90.0f,
        ),
    )
}
