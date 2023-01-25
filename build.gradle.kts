

plugins {
    kotlin("jvm") version "1.7.0" apply false
}

subprojects {

    apply(plugin = "org.jetbrains.kotlin.jvm")
}

allprojects {
    group = "muesli1"
    version = "1.0-SNAPSHOT"
}