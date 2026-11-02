plugins {
    alias(libs.plugins.micronaut.application)
}

dependencies {
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")

    implementation(project(":core"))
    implementation("io.micronaut:micronaut-runtime")
    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    runtimeOnly(libs.postgresql)
    // Compile-time too: the log redaction converter extends the logback pattern API.
    implementation("ch.qos.logback:logback-classic")
    runtimeOnly("org.yaml:snakeyaml")

    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("io.micronaut:micronaut-http-client")
    testImplementation(libs.swagger.parser)
}

application {
    mainClass.set("net.onelitefeather.menura.server.Application")
}

// The served contract is the file in contracts/openapi, copied unchanged into the classpath (see OpenApiController).
tasks.named<ProcessResources>("processResources") {
    from(rootProject.file("contracts/openapi/menura.yaml")) {
        into("openapi")
    }
}

micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        annotations("net.onelitefeather.menura.server.*")
    }
}
