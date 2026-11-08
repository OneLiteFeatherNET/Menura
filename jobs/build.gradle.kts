plugins {
    application
}

dependencies {
    implementation(project(":core"))
    implementation(libs.postgresql)
    // The retention job moves raw media to the cold storage class through the S3 API (add-content-library).
    implementation(platform(libs.awssdk.bom))
    implementation(libs.awssdk.s3)

    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
}

application {
    mainClass.set("net.onelitefeather.menura.jobs.JobsMain")
}
