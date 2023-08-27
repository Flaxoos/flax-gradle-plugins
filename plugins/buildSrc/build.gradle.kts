plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(gradleApi())
    implementation(gradleTestKit())
    implementation(libs.kotlin.gradleDsl)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kover.gradlePlugin)
    implementation(libs.ktlint.gradlePlugin)
    implementation(libs.detekt.gradlePlugin)
    implementation(libs.version.gradlePlugin)
    implementation(libs.gradlePublishPlugin.gradlePlugin)

    testImplementation(libs.mockk)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + "-Xcontext-receivers"
    }
}

gradlePlugin {
    plugins {
        create("conventions") {
            id = "conventions"
            implementationClass = "io.flax.Conventions"
        }
        create("kover-gradle-testkit") {
            id = "io.flax.kover-gradle-testkit"
            implementationClass = "io.flax.kover.gradle.testkit.KoverGradleTestKitPlugin"
        }
    }
}

val Project.gprToken
    get() = findProperty("gpr.key") as String? ?: System.getenv("GPR_TOKEN")

val Project.gprUser
    get() = findProperty("gpr.user") as String? ?: System.getenv("GPR_USER")
