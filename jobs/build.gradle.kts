plugins {
    application
}

dependencies {
    implementation(project(":core"))
    implementation(libs.postgresql)

    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
}

application {
    mainClass.set("net.onelitefeather.menura.jobs.JobsMain")
}
