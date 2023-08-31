package io.github.flaxoos.kover

import io.github.flaxoos.kover.ColorBand.Companion.from
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.kover.gradle.plugin.KoverGradlePlugin
import org.gradle.kotlin.dsl.apply
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
fun <T : Any> T?.shouldNotBeNull(): T {
    contract {
        returns() implies (this@shouldNotBeNull != null)
    }
    this shouldNotBe null
    return this!!
}

class KoverBadgePluginTest : FunSpec() {
    init {
        context("KoverBadgePlugin") {
            var project = ProjectBuilder.builder().build()

            beforeTest {
                project = ProjectBuilder.builder().build()
            }

            test("parameters are passed correctly from extension to task with KoverGradlePlugin applied") {
                project.pluginManager.apply(KoverGradlePlugin::class)
                project.pluginManager.apply(KoverBadgePlugin::class)
                val extension = project.extensions.findByName(Names.KOVER_BADGE_EXTENSION_NAME)
                    .shouldNotBeNull() as KoverBadgePluginExtension

                extension.readme.set(File("test_readme.md"))
                extension.badgeLabel.set("testBadge")
                extension.badgeStyle.set(Style.Flat)
                extension.spectrum.set(listOf("red" from 0.0f, "yellow" from 50.0f, "green" from 95.0f))

                project.afterEvaluate {
                    val task = project.tasks.findByName(Names.KOVER_BADGE_TASK_NAME).shouldNotBeNull() as KoverBadgeTask
                    task.readme.get().asFile.name shouldBe "test_readme.md"
                    task.badgeLabel.get() shouldBe "testBadge"
                    task.badgeStyle.get() shouldBe Style.Flat
                    task.spectrum.get() shouldBe listOf("red" from 0.0f, "yellow" from 50.0f, "green" from 95.0f)
                }
            }
        }
    }
}
