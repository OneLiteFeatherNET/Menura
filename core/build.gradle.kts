plugins {
    `java-test-fixtures`
}

dependencies {
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.postgresql)
    // Parses the JSON bodies of the platform token and profile endpoints (channel.youtube).
    implementation(libs.nimbus.jose.jwt)

    // Integration tests (@Tag("integration")) run the channel store against PostgreSQL 17 in a container.
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
}
