@file:Suppress("SuspiciousCollectionReassignment")

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij") version "1.5.3"
}

repositories {
    mavenCentral()
}
val kotlinVersion: String by rootProject.extra

dependencies {
    api(project(":script"))
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host:$kotlinVersion")
    api(project(":lib"))
    // implementation(project(":scriptHost"))
}

intellij {
    pluginName.set("Confis")
    // version.set("2021.3")
    version.set("2022.1")
    // version.set("IC-213.5744.223")
    type.set("IC")

    plugins.set(
        listOf(
            "Kotlin",
            "java",
            "org.intellij.plugins.markdown"
        )
    )
    downloadSources.set(true)
}

tasks.runIde {
    autoReloadPlugins.set(true)
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjvm-default=all"
        jvmTarget = "11"
    }
}
