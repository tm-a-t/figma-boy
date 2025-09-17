subprojects { repositories { mavenCentral() } }

tasks.register("runIde") {
    dependsOn(":idea-plugin:runIde")
}
