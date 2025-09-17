plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.7.1"
    kotlin("plugin.serialization") version "2.2.20"
}

group = "me.tmat"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Configure IntelliJ Platform Gradle Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
val ktorVersion = "3.3.0"

dependencies {
    intellijPlatform {
        create("IC", "2025.2.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        compatiblePlugin("org.jetbrains.junie")
    }

    // Ktor server dependencies
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging-jvm:${ktorVersion}")
    implementation("io.ktor:ktor-server-sse-jvm:${ktorVersion}")
    implementation("io.ktor:ktor-server-websockets-jvm:${ktorVersion}")

    // Explicit kotlinx.serialization runtime to match Kotlin serialization compiler plugin
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3")

    configurations.configureEach {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
        }

        changeNotes = """
            Initial version
        """.trimIndent()
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

tasks.register("prepareKotlinBuildScriptModel") {}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
