import io.github.flaxoos.FUNCTIONAL_TEST_SUITE_NAME

plugins {
    id("conventions")
    id("io.github.flaxoos.kover-gradle-testkit")
}

group = property("GROUP").toString()
version = property("VERSION").toString()
description = property("DESCRIPTION").toString()

dependencies {
    implementation(libs.kover.gradlePlugin)
    implementation(libs.kotlinCssLib)
    implementation(libs.konform)
    implementation(libs.kgit)
    implementation(libs.kotlinLogging)
}

koverGradleTestKit {
    testConfigurationRuntime.set(configurations.functionalTestImplementation)
    task.set(tasks.named(FUNCTIONAL_TEST_SUITE_NAME, Test::class))
}

conventions {
    // overriding the convention, until koverGradleTestKit works properly
    minimumCoverage.set(40)
    pluginTags.set(listOf("kover", "badge", "shields", "shields.io", "coverage", "kotlin"))
}
