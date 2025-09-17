plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    application
}

group = "me.tmat.figmaboy"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val ktorVersion = "3.3.0"

dependencies {
    // Ktor server dependencies
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:${ktorVersion}")
    implementation("io.ktor:ktor-server-sse:${ktorVersion}")
    implementation("io.ktor:ktor-server-websockets:${ktorVersion}")

    // Explicit kotlinx.serialization runtime to match Kotlin serialization compiler plugin
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3")

    // Use the server-focused MCP Kotlin SDK artifact (matches working sample)
    implementation("io.modelcontextprotocol:kotlin-sdk-server:0.7.0")

    implementation(project(":imgdiff"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

application {
    // Kotlin generates a class named MainKt for top-level main in me.tmat.figmaboy.mcp.Main.kt
    mainClass.set("me.tmat.figmaboy.mcp.MainKt")
}
