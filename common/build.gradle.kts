val ktor_version: String by project

plugins {
    id("org.jetbrains.kotlin.plugin.serialization") version "1.8.0"
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")

    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}