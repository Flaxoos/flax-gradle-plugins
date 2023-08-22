import io.flax.kover.ColorBand.Companion.from
import io.flax.kover.GitCommitOption.*
import io.flax.kover.Style.*

plugins {
    kotlin("jvm") version "1.9.0"
    id("org.jetbrains.kotlinx.kover") version "0.7.3"
    id("io.flax.kover-badge") version "0.0.1"
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

koverReport {
    defaults {
        verify {
            rule {
                isEnabled = true
                minBound(66)
            }
            onCheck = true
        }
    }
}

koverBadge {
    badgeLabel.set("zAhOiQjaxd")
    readme.set(file("TEST_README.md"))
    badgeStyle.set(ForTheBadge)
    spectrum.set(
        listOf("red" from 0.0f, "yellow" from 33.33f, "gray" from 66.66f)
    )
    gitAction.set(CommitToNewBranch(branchName = "new-branch"))
}
