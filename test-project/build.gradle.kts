import io.flax.kover.ColorBand.Companion.from
import io.flax.kover.Style

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlinxKover)
    id("io.flax.kover-badge")
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotest.junit5)
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
            "green" from 90.0f
        )
    )
}
