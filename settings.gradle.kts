rootProject.name = "menura"

plugins {
    id("io.micronaut.platform.catalog") version "5.0.2"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include("core", "server", "jobs")
